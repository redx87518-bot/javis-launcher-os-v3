package com.javis.launcher.engine

import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.intent.IntentAnalyzer
import com.javis.launcher.models.*

/**
 * JAVIS Thinking Engine — the intelligent request router.
 *
 * All user input flows through here first. It decides which system handles
 * the request so VoiceActivity/ChatActivity never contain routing logic.
 *
 * Route map:
 *   LOCAL_ACTION   → ExecutionEngine (alarms, calls, app opens, memory writes)
 *   MEMORY_QUERY   → MemoryEngine (recall stored facts, no AI needed)
 *   AI_CONVERSATION → AIEngine (complex chat, explanations, questions)
 *   HYBRID         → ExecutionEngine + AIEngine (execute AND explain)
 */
object ThinkingEngine {

    enum class Category {
        LOCAL_ACTION,
        MEMORY_QUERY,
        AI_CONVERSATION,
        HYBRID
    }

    data class ThinkingResult(
        val category: Category,
        val intentResult: IntentResult,
        val shouldAlsoAsk: Boolean = false,   // true = run execution AND send to AI
        val enrichedPrompt: String? = null    // richer prompt to send AI if needed
    )

    fun think(input: String): ThinkingResult {
        val intent = IntentAnalyzer.analyze(input)
        val ctx = ContextEngine.context

        return when (intent.action) {
            // ── Clear-cut local commands ──────────────────────────────────
            JavisAction.OPEN_APP,
            JavisAction.SET_ALARM,
            JavisAction.CLEAR_MISSED_CALLS,
            JavisAction.OPEN_SETTINGS ->
                ThinkingResult(Category.LOCAL_ACTION, intent)

            // ── Calls: local but may need AI for ambiguity ─────────────
            JavisAction.CALL_CONTACT ->
                ThinkingResult(Category.LOCAL_ACTION, intent)

            // ── Memory reads: answer from DB, no AI ────────────────────
            JavisAction.QUERY_MEMORY ->
                ThinkingResult(Category.MEMORY_QUERY, intent)

            // ── Memory writes: local, no AI needed ─────────────────────
            JavisAction.UPDATE_MEMORY ->
                ThinkingResult(Category.LOCAL_ACTION, intent)

            // ── Pure conversation → AI Brain ────────────────────────────
            JavisAction.CHAT -> {
                val enriched = buildEnrichedPrompt(input, ctx)
                ThinkingResult(
                    category = Category.AI_CONVERSATION,
                    intentResult = intent,
                    enrichedPrompt = enriched
                )
            }

            // ── Unknown: let AI figure it out ──────────────────────────
            JavisAction.UNKNOWN -> {
                val enriched = buildEnrichedPrompt(input, ctx)
                ThinkingResult(
                    category = Category.AI_CONVERSATION,
                    intentResult = IntentResult(JavisAction.CHAT),
                    enrichedPrompt = enriched
                )
            }
        }
    }

    /**
     * Enrich the raw user input with context so the AI can resolve references
     * like "him", "it", "that app" without the user repeating themselves.
     */
    private fun buildEnrichedPrompt(input: String, ctx: ConversationContext): String {
        val notes = mutableListOf<String>()
        ctx.lastContact?.let { notes += "[Context: last mentioned contact is ${it.name}]" }
        ctx.lastApp?.let { notes += "[Context: last mentioned app is ${it.appName}]" }
        ctx.lastTopic?.let { if (it.isNotBlank()) notes += "[Context: last topic was $it]" }
        ctx.currentGoal?.let { if (it.isNotBlank()) notes += "[User's current goal: $it]" }

        return if (notes.isEmpty()) input
        else "${notes.joinToString(" ")} User says: $input"
    }
}
