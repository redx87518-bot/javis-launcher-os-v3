package com.javis.launcher.engine.context

import com.javis.launcher.models.ConversationContext
import com.javis.launcher.models.Contact
import com.javis.launcher.models.InstalledApp
import com.javis.launcher.models.JavisAction

/**
 * V5 ContextEngine — multi-turn conversation state tracking.
 *
 * Tracks enough context for the AgentEngine to resolve natural language
 * references across conversation turns.
 *
 * Resolution priority:
 *   1. AI reasoning (via AgentEngine prompt injection)
 *   2. Deterministic context lookup (contact names, JIDs, chat IDs)
 *   3. Pronoun matching (she, he, they, same person, etc.)
 */
object ContextEngine {

    val context = ConversationContext()

    // ─── Updaters ─────────────────────────────────────────────────
    fun updateContact(contact: Contact) {
        context.lastContact = contact
    }

    fun updateApp(app: InstalledApp) {
        context.lastApp = app
    }

    fun updateAction(action: JavisAction) {
        context.lastAction = action
        if (action == JavisAction.OPEN_APP || action == JavisAction.CALL_CONTACT) {
            context.currentGoal = null
        }
    }

    fun updateTopic(topic: String) {
        context.lastTopic = topic
    }

    fun updateGoal(goal: String) {
        context.currentGoal = goal
    }

    fun updatePhoneNumber(phone: String) {
        context.lastPhoneNumber = phone
    }

    fun updateWhatsAppJid(jid: String) {
        context.lastWhatsAppJid = jid
    }

    fun updateChatId(chatId: String) {
        context.lastChatId = chatId
    }

    fun updateMessageId(messageId: String) {
        context.lastMessageId = messageId
    }

    fun updateMessageSender(sender: String) {
        context.lastMessageSender = sender
    }

    fun updateMessageText(text: String) {
        context.lastMessageText = text
    }

    fun updateTask(task: String) {
        context.currentTask = task
    }

    fun updateConfirmation(confirmation: String?) {
        context.pendingConfirmation = confirmation
    }

    fun updateToolUsed(tool: String) {
        context.lastToolUsed = tool
    }

    fun updateToolResult(result: String) {
        context.lastToolResult = result
    }

    // ─── Pronoun & Reference Resolution ──────────────────────────
    fun resolveContactReference(input: String): Contact? {
        val lowered = input.lowercase().trim()
        val pronouns = listOf(
            "her", "him", "them", "that person", "the same", "same person",
            "she ", "he ", "they ", "Aisha", "aisha"
        )
        val isPronoun = pronouns.any { lowered == it || lowered.endsWith(it) }
        return if (isPronoun || isNamedContact(lowered)) {
            context.lastContact
        } else null
    }

    private fun isNamedContact(input: String): Boolean {
        val name = input.trim().lowercase()
        return context.lastContact?.let {
            it.name.lowercase() == name ||
            name.contains(it.name.lowercase()) ||
            name.contains(it.displayName.lowercase())
        } ?: false
    }

    fun resolveAppReference(input: String): InstalledApp? {
        val lowered = input.lowercase()
        val refs = listOf(" it", "that app", "same app", "that one", "the app")
        if (refs.any { lowered.contains(it) }) {
            return context.lastApp
        }
        return null
    }

    fun resolveWhatsAppJid(query: String): String? {
        return context.lastWhatsAppJid
    }

    fun resolveChatId(): String? {
        return context.lastChatId
    }

    fun resolveLastContactName(): String? {
        return context.lastContact?.name ?: context.lastContact?.name
    }

    // ─── WhatsApp Context ────────────────────────────────────────
    fun updateWhatsAppContext(
        contactName: String? = null,
        phoneNumber: String? = null,
        jid: String? = null,
        chatId: String? = null
    ) {
        contactName?.let {
            context.lastContact = Contact(id = jid ?: it, name = it, phone = phoneNumber ?: "")
        }
        phoneNumber?.let { updatePhoneNumber(it) }
        jid?.let { updateWhatsAppJid(it) }
        chatId?.let { updateChatId(it) }
    }

    // ─── Context Summary for AI ──────────────────────────────────
    fun buildContextSummary(): String {
        val parts = mutableListOf<String>()
        context.lastContact?.let { parts += "Last contact mentioned: ${it.name}" }
        context.lastApp?.let { parts += "Last app used: ${it.appName}" }
        context.lastTopic?.let { if (it.isNotBlank()) parts += "Last topic: $it" }
        context.currentGoal?.let { if (it.isNotBlank()) parts += "Current goal: $it" }
        context.currentTask?.let { if (it.isNotBlank()) parts += "Current task: $it" }
        context.lastPhoneNumber?.let { parts += "Last phone number: $it" }
        context.lastWhatsAppJid?.let { parts += "Last WhatsApp JID: $it" }
        context.lastChatId?.let { parts += "Last chat: $it" }
        context.lastMessageSender?.let { parts += "Last message from: $it" }
        context.lastMessageText?.let { parts += "Last message content: $it" }
        return parts.joinToString(". ")
    }

    fun contextSummary(): String = buildContextSummary()

    fun inferAndUpdateGoal(input: String) {
        val lowered = input.lowercase()
        val newGoal = when {
            lowered.contains("plan") || lowered.contains("planning") -> input
            lowered.contains("help me") -> input
            lowered.contains("how do i") || lowered.contains("how to") -> input
            lowered.contains("explain") || lowered.contains("what is") -> input
            lowered.contains("remind me") || lowered.contains("schedule") -> input
            lowered.contains("plan my") || lowered.contains("organize") -> input
            else -> null
        }
        if (newGoal != null) context.currentGoal = newGoal
    }

    fun reset() {
        context.lastContact = null
        context.lastApp = null
        context.lastAction = null
        context.lastTopic = null
        context.currentGoal = null
        context.lastPhoneNumber = null
        context.lastWhatsAppJid = null
        context.lastChatId = null
        context.lastMessageId = null
        context.lastMessageSender = null
        context.lastMessageText = null
        context.currentTask = null
        context.pendingConfirmation = null
        context.lastToolUsed = null
        context.lastToolResult = null
    }
}
