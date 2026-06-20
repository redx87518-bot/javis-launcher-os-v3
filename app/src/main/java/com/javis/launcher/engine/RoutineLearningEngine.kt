package com.javis.launcher.engine

import android.content.Context
import com.javis.launcher.engine.memory.MemoryEngine
import com.javis.launcher.models.AppUsage
import com.javis.launcher.models.ContactUsage
import java.util.Calendar

/**
 * JAVIS Routine Learning Engine
 *
 * Learns the user's daily patterns from MemoryEngine data and generates
 * proactive, context-aware suggestions. No new DB tables needed —
 * all data comes from the existing app_usage and contact_usage tables.
 *
 * Patterns learned:
 * - Favourite apps by time of day (morning / afternoon / evening)
 * - Most-called contacts
 * - Common request types
 */
object RoutineLearningEngine {

    data class Suggestion(
        val text: String,          // What JAVIS says
        val actionHint: String,    // Quick-action label on the UI
        val priority: Int = 0      // Higher = shown first
    )

    // ─── Generate proactive suggestions for RIGHT NOW ─────────────────────
    suspend fun getSuggestions(memory: MemoryEngine, context: Context): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val topApps      = memory.getTopApps(6)
        val topContacts  = memory.getTopContacts(4)
        val userName     = memory.getUserName()

        // Time-of-day greeting suggestion
        timeBasedSuggestion(hour)?.let { suggestions.add(it) }

        // App suggestions (based on overall frequency)
        topApps.take(2).forEach { app ->
            suggestions.add(Suggestion(
                text       = "Open ${app.appName}",
                actionHint = app.appName,
                priority   = app.useCount
            ))
        }

        // Contact suggestions
        topContacts.take(2).forEach { c ->
            suggestions.add(Suggestion(
                text       = "Call ${c.name}",
                actionHint = "Call ${c.name}",
                priority   = c.callCount
            ))
        }

        // Morning routine
        if (hour in 5..9) {
            suggestions.add(Suggestion("Check your calendar", "Open Calendar", priority = 5))
            suggestions.add(Suggestion("Set a morning alarm", "New Alarm", priority = 3))
        }

        // Evening routine
        if (hour in 18..22) {
            suggestions.add(Suggestion("Set your alarm for tomorrow", "New Alarm", priority = 5))
        }

        return suggestions.sortedByDescending { it.priority }.take(4)
    }

    // ─── Briefing text for voice delivery ─────────────────────────────────
    suspend fun getMorningBriefing(memory: MemoryEngine): String {
        val topApps     = memory.getTopApps(3)
        val topContacts = memory.getTopContacts(2)
        val name        = memory.getUserName()
        val address     = if (name != null) ", $name" else ", Sir"

        val sb = StringBuilder("Good morning$address. ")

        if (topApps.isNotEmpty()) {
            val appNames = topApps.map { it.appName }.joinToString(", ")
            sb.append("Based on your habits, you usually open $appNames in the morning. ")
        }
        if (topContacts.isNotEmpty()) {
            val contactName = topContacts.first().name
            sb.append("You frequently call $contactName — would you like me to dial them? ")
        }
        sb.append("How can I help you today?")
        return sb.toString()
    }

    // ─── Pattern analysis helpers ─────────────────────────────────────────
    fun getMostUsedApp(apps: List<AppUsage>): AppUsage? = apps.maxByOrNull { it.useCount }
    fun getMostCalledContact(contacts: List<ContactUsage>): ContactUsage? = contacts.maxByOrNull { it.callCount }

    fun getInsightText(apps: List<AppUsage>, contacts: List<ContactUsage>): String {
        val sb = StringBuilder()
        if (apps.isNotEmpty()) {
            val top = apps.first()
            sb.append("You most often open ${top.appName} (${top.useCount} times). ")
        }
        if (contacts.isNotEmpty()) {
            val top = contacts.first()
            sb.append("Your most frequent contact is ${top.name} (${top.callCount} calls).")
        }
        return sb.toString().ifBlank { "Keep using JAVIS and I'll learn your habits over time." }
    }

    private fun timeBasedSuggestion(hour: Int): Suggestion? = when {
        hour in 5..8   -> Suggestion("Good morning! Start your day.", "Morning Briefing", priority = 10)
        hour in 9..11  -> Suggestion("Ready to be productive?", "Daily Summary",        priority  = 8)
        hour in 12..13 -> Suggestion("Lunchtime — any calls to make?", "Contacts",      priority  = 6)
        hour in 17..18 -> Suggestion("End of day — set tomorrow's alarm?", "New Alarm", priority  = 8)
        hour in 20..22 -> Suggestion("Wind down time — any reminders?", "New Alarm",    priority  = 6)
        else           -> null
    }
}
