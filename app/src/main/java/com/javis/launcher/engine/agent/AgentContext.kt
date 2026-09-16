package com.javis.launcher.engine.agent

import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.memory.MemoryEngine
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.engine.execution.ExecutionEngine
import com.javis.launcher.engine.voice.VoiceEngine
import com.javis.launcher.models.ConversationMessage
import kotlinx.coroutines.flow.Flow

data class AgentContext(
    val userInput: String,
    val conversationHistory: List<ConversationMessage> = emptyList(),
    val contextSummary: String = "",
    val memory: MemoryEngine? = null,
    val aiEngine: AIEngine? = null,
    val executionEngine: ExecutionEngine? = null,
    val voiceEngine: VoiceEngine? = null,
    val contextEngine: ContextEngine = ContextEngine,
    val tools: List<JavisTool> = emptyList()
)

data class AgentStep(
    val observation: String,
    val thought: String,
    val action: String? = null,
    val actionInput: Map<String, Any?>? = null,
    val toolResult: ToolResult? = null,
    val response: String? = null
)
