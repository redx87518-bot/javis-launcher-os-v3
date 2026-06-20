package com.javis.launcher.engine.context

import com.javis.launcher.models.ConversationContext
import com.javis.launcher.models.Contact
import com.javis.launcher.models.InstalledApp
import com.javis.launcher.models.JavisAction

/**
 * V4 ContextEngine — tracks full conversation state so JAVIS can resolve
 * references like "him", "it", "that app", "same one" across turns.
 *
 * Also tracks the user's current goal across multiple conversation turns.
 */
object ContextEngine {

    val context = ConversationContext()

    // ─── Updaters ─────────────────────────────────────────────────────────
    fun updateContact(contact: Contact) {
        context.lastContact = contact
    }

    fun updateApp(app: InstalledApp) {
        context.lastApp = app
    }

    fun updateAction(action: JavisAction) {
        context.lastAction = action
        // If user is starting a new unrelated action, clear the goal
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

    // ─── Pronoun resolution ───────────────────────────────────────────────
    fun resolveContactReference(input: String): Contact? {
        val lowered = input.lowercase()
        val pronouns = listOf("him", "her", "them", "that person", "the same", "same person", "he ", "she ")
        if (pronouns.any { lowered.contains(it) }) {
            return context.lastContact
        }
        return null
    }

    fun resolveAppReference(input: String): InstalledApp? {
        val lowered = input.lowercase()
        val refs = listOf(" it", "that app", "same app", "that one", "the app")
        if (refs.any { lowered.contains(it) }) {
            return context.lastApp
        }
        return null
    }

    // ─── Build a readable context summary for AI injection ────────────────
    fun buildContextSummary(): String {
        val parts = mutableListOf<String>()
        context.lastContact?.let { parts += "Last contact: ${it.name}" }
        context.lastApp?.let { parts += "Last app: ${it.appName}" }
        context.lastTopic?.let { if (it.isNotBlank()) parts += "Last topic: $it" }
        context.currentGoal?.let { if (it.isNotBlank()) parts += "Current goal: $it" }
        return parts.joinToString(". ")
    }

    // ─── Infer goal from conversation ─────────────────────────────────────
    fun inferAndUpdateGoal(input: String) {
        val lowered = input.lowercase()
        val newGoal = when {
            lowered.contains("plan") || lowered.contains("planning") -> input
            lowered.contains("help me") -> input
            lowered.contains("how do i") || lowered.contains("how to") -> input
            lowered.contains("explain") || lowered.contains("what is") -> input
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
    }
}
