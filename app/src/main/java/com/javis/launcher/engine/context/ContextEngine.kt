package com.javis.launcher.engine.context

import com.javis.launcher.models.ConversationContext
import com.javis.launcher.models.Contact
import com.javis.launcher.models.InstalledApp
import com.javis.launcher.models.JavisAction

/**
 * Tracks conversation context so JAVIS can resolve pronouns like "him", "it", "that app"
 */
object ContextEngine {
    val context = ConversationContext()

    fun updateContact(contact: Contact) {
        context.lastContact = contact
    }

    fun updateApp(app: InstalledApp) {
        context.lastApp = app
    }

    fun updateAction(action: JavisAction) {
        context.lastAction = action
    }

    fun updateTopic(topic: String) {
        context.lastTopic = topic
    }

    fun resolveContactReference(input: String): Contact? {
        val lowered = input.lowercase()
        if (lowered.contains("him") || lowered.contains("her") || lowered.contains("them") ||
            lowered.contains("that person") || lowered.contains("the same")) {
            return context.lastContact
        }
        return null
    }

    fun resolveAppReference(input: String): InstalledApp? {
        val lowered = input.lowercase()
        if (lowered.contains("it") || lowered.contains("that app") || lowered.contains("same app")) {
            return context.lastApp
        }
        return null
    }

    fun reset() {
        context.lastContact = null
        context.lastApp = null
        context.lastAction = null
        context.lastTopic = null
    }
}
