package com.javis.launcher.engine.agent

import android.content.Context
import android.util.Log
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.execution.ExecutionEngine
import com.javis.launcher.engine.execution.ExecutionResult
import com.javis.launcher.engine.intent.IntentAnalyzer
import com.javis.launcher.engine.memory.MemoryEngine
import com.javis.launcher.engine.voice.VoiceEngine
import com.javis.launcher.models.AIProvider
import com.javis.launcher.models.ConversationMessage
import com.javis.launcher.models.JavisAction
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentEngine(
    private val context: Context
) {
    companion object {
        private const val TAG = "AgentEngine"
        private const val MAX_AGENT_STEPS = 8
        private const val MAX_HISTORY = 50
    }

    private val toolRegistry = ToolRegistry()
    private val contextEngine = ContextEngine

    private val aiEngine: AIEngine?
        get() = try { AIEngine(context) } catch (e: Exception) { null }

    private val executionEngine: ExecutionEngine?
        get() = try { ExecutionEngine(context) } catch (e: Exception) { null }

    private val voiceEngine: VoiceEngine?
        get() = try { com.javis.launcher.JavisApplication.instance.voiceEngine } catch (e: Exception) { null }

    private val memoryEngine: MemoryEngine?
        get() = try { com.javis.launcher.JavisApplication.instance.memoryEngine } catch (e: Exception) { null }

    private var session = AgentSession()

    init {
        registerDefaultTools()
    }

    private fun registerDefaultTools() {
        // Tools will be registered here as they're created
        // WhatsApp tools registered via registerWhatsAppTools()
    }

    fun registerWhatsAppTools(whatsappTools: List<JavisTool>) {
        whatsappTools.forEach { toolRegistry.register(it) }
    }

    fun registerTool(tool: JavisTool) {
        toolRegistry.register(tool)
    }

    suspend fun process(userInput: String): AgentResult = withContext(Dispatchers.IO) {
        session = session.copy(
            status = AgentStatus.OBSERVING,
            currentGoal = determineGoal(userInput),
            currentTask = userInput
        )

        try {
            val ctx = buildAgentContext(userInput)
            var stepCount = 0
            var continueLoop = true

            while (continueLoop && stepCount < MAX_AGENT_STEPS) {
                stepCount++
                session = session.copy(status = AgentStatus.UNDERSTANDING)

                val observation = observe(userInput, stepCount)
                session.steps.add(AgentStep(observation = observation, thought = ""))

                session = session.copy(status = AgentStatus.PLANNING)
                val planResult = planAndExecute(ctx, observation, stepCount)

                when (planResult) {
                    is PlanResult.Continue -> {
                        session.steps.add(AgentStep(
                            observation = planResult.observation,
                            thought = planResult.thought,
                            action = planResult.action,
                            actionInput = planResult.actionInput,
                            toolResult = planResult.toolResult
                        ))
                        continueLoop = true
                    }
                    is PlanResult.Respond -> {
                        session.steps.add(AgentStep(
                            observation = "",
                            thought = "",
                            response = planResult.response
                        ))
                        session = session.copy(status = AgentStatus.RESPONDING)
                        continueLoop = false
                        return@withContext AgentResult(
                            response = planResult.response ?: "Done, Sir.",
                            needsConfirmation = false,
                            session = session
                        )
                    }
                    is PlanResult.NeedConfirmation -> {
                        session = session.copy(
                            pendingConfirmation = planResult.question,
                            status = AgentStatus.VERIFYING
                        )
                        return@withContext AgentResult(
                            response = planResult.question,
                            needsConfirmation = true,
                            session = session
                        )
                    }
                    is PlanResult.Error -> {
                        session = session.copy(status = AgentStatus.ERROR)
                        return@withContext AgentResult(
                            response = planResult.message,
                            needsConfirmation = false,
                            session = session
                        )
                    }
                }
            }

            val finalResponse = generateFinalResponse(ctx, session)
            session = session.copy(status = AgentStatus.RESPONDING)
            AgentResult(
                response = finalResponse,
                needsConfirmation = false,
                session = session
            )
        } catch (e: Exception) {
            Log.e(TAG, "Agent process error", e)
            session = session.copy(status = AgentStatus.ERROR)
            var fallbackResponse: String? = null
            val intent = IntentAnalyzer.analyze(userInput)
            if (intent.action != JavisAction.CHAT && intent.action != JavisAction.UNKNOWN && intent.confidence >= 0.5f) {
                try {
                    val result = executionEngine?.execute(intent)
                    if (result is ExecutionResult.Success) {
                        fallbackResponse = result.message
                    }
                } catch (_: Exception) {}
            }
            AgentResult(
                response = fallbackResponse ?: "I encountered an issue processing that, Sir. Let me try again.",
                needsConfirmation = false,
                session = session
            )
        }
    }

    private suspend fun buildAgentContext(userInput: String): AgentContext {
        val history = memoryEngine?.getRecentHistory(MAX_HISTORY) ?: emptyList()
        val summary = contextEngine.buildContextSummary()
        return AgentContext(
            userInput = userInput,
            conversationHistory = history,
            contextSummary = summary,
            memory = memoryEngine,
            aiEngine = aiEngine,
            executionEngine = executionEngine,
            voiceEngine = voiceEngine,
            contextEngine = contextEngine,
            tools = toolRegistry.getAllTools()
        )
    }

    private suspend fun observe(userInput: String, step: Int): String {
        val ctx = contextEngine.context
        val parts = mutableListOf("Step $step: User said: $userInput")
        if (ctx.lastContact != null) parts += "Last contact: ${ctx.lastContact!!.name}"
        if (ctx.lastWhatsAppJid != null) parts += "Last WhatsApp contact: ${ctx.lastWhatsAppJid}"
        if (ctx.lastChatId != null) parts += "Last chat: ${ctx.lastChatId}"
        if (ctx.currentGoal != null) parts += "Current goal: ${ctx.currentGoal}"
        if (ctx.lastMessageText != null) parts += "Last message: ${ctx.lastMessageText}"
        return parts.joinToString(" | ")
    }

    private suspend fun planAndExecute(
        ctx: AgentContext,
        observation: String,
        step: Int
    ): PlanResult {
        val ai = aiEngine ?: return PlanResult.Error("AI engine not available")
        val toolDefs = toolRegistry.getToolDefinitions()

        val toolCallingResult = callAIWithTools(ai, ctx, toolDefs)

        return when {
            toolCallingResult.needsConfirmation -> PlanResult.NeedConfirmation(toolCallingResult.confirmationText)
            toolCallingResult.toolCall != null -> {
                val toolResult = executeTool(toolCallingResult.toolCall.toolName, toolCallingResult.toolCall.arguments)
                if (toolResult is ToolResult.Failed && !toolResult.retryable) {
                    val errorMsg = when {
                        toolResult.error.contains("confirmation", ignoreCase = true) ->
                            "I need your confirmation before proceeding."
                        else -> toolResult.error
                    }
                    PlanResult.Error(errorMsg)
                } else {
                    val nextThought = "Executed ${toolCallingResult.toolCall.toolName}. Result: ${toolResult::class.simpleName}"
                    PlanResult.Continue(
                        observation = "Tool ${toolCallingResult.toolCall.toolName} executed.",
                        thought = nextThought,
                        action = toolCallingResult.toolCall.toolName,
                        actionInput = toolCallingResult.toolCall.arguments,
                        toolResult = toolResult
                    )
                }
            }
            else -> PlanResult.Respond(toolCallingResult.aiResponse)
        }
    }

    private suspend fun callAIWithTools(
        ai: AIEngine,
        ctx: AgentContext,
        toolDefs: List<JSONObject>
    ): ToolCallingResult {
        val fullPrompt = buildString {
            append("You are JAVIS, an intelligent assistant. The user wants: ${ctx.userInput}")
            if (ctx.contextSummary.isNotBlank()) {
                append("\nContext: $ctx.contextSummary")
            }
            append("\n\nAvailable tools:")
            toolDefs.forEach { def ->
                append("\n- ${def.optString("name")}: ${def.optString("description")}")
            }
            append("\n\nRespond ONLY in the following JSON format when you need to use a tool:")
            append("\n{\"action\":\"tool\",\"tool\":\"tool_name\",\"arguments\":{...}}")
            append("\nIf you need confirmation, respond: {\"action\":\"confirm\",\"question\":\"...\"}")
            append("\nFor normal responses, respond naturally in one sentence.")
        }

        val history = ctx.conversationHistory.takeLast(20)
        val response = ai.chat(fullPrompt, history)

        return parseAIResponse(response)
    }

    private fun parseAIResponse(response: String): ToolCallingResult {
        return try {
            val trimmed = response.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val action = json.optString("action", "")

                when (action) {
                    "tool" -> {
                        val toolName = json.optString("tool", "")
                        val args = json.optJSONObject("arguments") ?: JSONObject()
                        val argMap = mutableMapOf<String, Any?>()
                        args.keys().forEach { key ->
                            argMap[key] = when (val v = args.get(key)) {
                                is String -> v
                                is Number -> v
                                is Boolean -> v
                                is JSONObject -> v.toString()
                                else -> v.toString()
                            }
                        }
                        ToolCallingResult(toolCall = ToolCall(toolName, argMap))
                    }
                    "confirm" -> {
                        ToolCallingResult(needsConfirmation = true, confirmationText = json.optString("question", "Continue?"))
                    }
                    else -> ToolCallingResult(aiResponse = response)
                }
            } else {
                ToolCallingResult(aiResponse = response)
            }
        } catch (e: Exception) {
            ToolCallingResult(aiResponse = response)
        }
    }

    private suspend fun executeTool(name: String, args: Map<String, Any?>): ToolResult {
        contextEngine.context.lastToolUsed = name
        contextEngine.context.lastToolResult = args.toString()

        return try {
            val tool = toolRegistry.getTool(name)
                ?: return ToolResult.Failed("Unknown tool: $name", retryable = true)
            val result = tool.execute(args)
            if (result is ToolResult.Success) {
                contextEngine.context.lastToolResult = result.data.toString()
            }
            result
        } catch (e: Exception) {
            ToolResult.Failed("Tool execution failed: ${e.message}", retryable = true)
        }
    }

    private suspend fun generateFinalResponse(ctx: AgentContext, session: AgentSession): String {
        val ai = aiEngine ?: return "Done, Sir."

        val toolSummary = session.steps.lastOrNull()?.let { step ->
            if (step.toolResult is ToolResult.Success && step.toolResult.userMessage.isNotBlank()) {
                step.toolResult.userMessage
            } else null
        }

        return toolSummary ?: run {
            val followUpPrompt = "Based on the actions taken, provide a concise natural response to the user.\n\n" +
                if (session.steps.any { it.observation.contains("WhatsApp") || it.action?.contains("whatsapp") == true }) {
                    "Actions involved WhatsApp.\n"
                } else "" +
                "User asked: ${ctx.userInput}\n" +
                "Steps taken: ${session.steps.size}\n" +
                "Respond naturally as JAVIS would."

            try {
                ai.chat(followUpPrompt, ctx.conversationHistory.takeLast(10))
            } catch (e: Exception) {
                "Done, Sir."
            }
        }
    }

    private fun determineGoal(input: String): String {
        return input.takeIf { it.isNotBlank() } ?: "general conversation"
    }

    data class ToolCall(
        val toolName: String,
        val arguments: Map<String, Any?>
    )

    data class ToolCallingResult(
        val toolCall: ToolCall? = null,
        val aiResponse: String = "",
        val needsConfirmation: Boolean = false,
        val confirmationText: String = ""
    )

    sealed class PlanResult {
        data class Continue(
            val observation: String,
            val thought: String,
            val action: String? = null,
            val actionInput: Map<String, Any?>? = null,
            val toolResult: ToolResult? = null
        ) : PlanResult()
        data class Respond(val response: String) : PlanResult()
        data class NeedConfirmation(val question: String) : PlanResult()
        data class Error(val message: String) : PlanResult()
    }
}

data class AgentResult(
    val response: String,
    val needsConfirmation: Boolean,
    val session: AgentSession
)
