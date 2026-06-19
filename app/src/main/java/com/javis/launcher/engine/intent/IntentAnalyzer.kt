package com.javis.launcher.engine.intent

import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.models.IntentResult
import com.javis.launcher.models.JavisAction

object IntentAnalyzer {

    fun analyze(input: String): IntentResult {
        val text = input.lowercase().trim()

        // ─── Clear missed calls ────────────────────────────────────────────
        if (matchesClearMissedCalls(text)) {
            return IntentResult(action = JavisAction.CLEAR_MISSED_CALLS, confidence = 0.95f)
        }

        // ─── App Opening ───────────────────────────────────────────────────
        if (matchesOpenApp(text)) {
            val appName = extractAppName(text)
            return IntentResult(
                action = JavisAction.OPEN_APP,
                params = mapOf("appName" to appName),
                confidence = 0.95f
            )
        }

        // ─── Contact Calling ───────────────────────────────────────────────
        if (matchesCall(text)) {
            val resolved = ContextEngine.resolveContactReference(text)
            val contactName = resolved?.name ?: extractContactName(text)
            return IntentResult(
                action = JavisAction.CALL_CONTACT,
                params = mapOf("contactName" to contactName),
                confidence = 0.95f
            )
        }

        // ─── Alarm Creation ────────────────────────────────────────────────
        if (matchesAlarm(text)) {
            val timeInfo = extractAlarmTime(text)
            return IntentResult(
                action = JavisAction.SET_ALARM,
                params = timeInfo,
                confidence = 0.9f
            )
        }

        // ─── Memory Query ──────────────────────────────────────────────────
        if (matchesMemoryQuery(text)) {
            val key = extractMemoryKey(text)
            return IntentResult(
                action = JavisAction.QUERY_MEMORY,
                params = mapOf("key" to key),
                confidence = 0.9f
            )
        }

        // ─── Memory Update ─────────────────────────────────────────────────
        if (matchesMemoryUpdate(text)) {
            val (key, value) = extractMemoryKeyValue(text)
            return IntentResult(
                action = JavisAction.UPDATE_MEMORY,
                params = mapOf("key" to key, "value" to value),
                confidence = 0.85f
            )
        }

        // ─── Settings ─────────────────────────────────────────────────────
        if (text.contains("settings") || text.contains("configure") || text.contains("setup")) {
            return IntentResult(action = JavisAction.OPEN_SETTINGS, confidence = 0.8f)
        }

        // ─── Default: Chat ─────────────────────────────────────────────────
        return IntentResult(action = JavisAction.CHAT, confidence = 0.5f)
    }

    private fun matchesClearMissedCalls(text: String): Boolean {
        return text.contains("i saw") || text.contains("mark") && text.contains("read") ||
                text.contains("clear missed") || text.contains("dismiss") && text.contains("call") ||
                text.contains("acknowledge") || text == "i saw those" || text == "got it" ||
                text.contains("clear the calls") || text.contains("mark calls") ||
                text.contains("clear calls") || text.contains("seen the calls")
    }

    private fun matchesOpenApp(text: String): Boolean {
        return text.startsWith("open ") || text.startsWith("launch ") ||
                text.startsWith("start ") || text.contains("open the ") ||
                text.contains("open my ")
    }

    private fun matchesCall(text: String): Boolean {
        return text.startsWith("call ") || text.startsWith("dial ") ||
                text.contains("phone ") || text.contains("ring ") ||
                text.contains("call him") || text.contains("call her")
    }

    private fun matchesAlarm(text: String): Boolean {
        return text.contains("alarm") || text.contains("wake me") ||
                text.contains("remind me") || text.contains("set a timer") ||
                (text.contains("set") && (text.contains("am") || text.contains("pm")))
    }

    private fun matchesMemoryQuery(text: String): Boolean {
        return text.startsWith("what is my") || text.startsWith("what's my") ||
                text.startsWith("do you know my") || text.startsWith("tell me my") ||
                text.contains("what do you know about me") || text.contains("my name")
    }

    private fun matchesMemoryUpdate(text: String): Boolean {
        return text.startsWith("my name is") || text.startsWith("remember that") ||
                text.startsWith("i am ") || text.startsWith("i'm ") ||
                text.contains("call me ") || text.contains("i like ") ||
                text.contains("my favorite")
    }

    private fun extractAppName(text: String): String {
        val prefixes = listOf("open ", "launch ", "start ", "open the ", "open my ")
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) return text.removePrefix(prefix).trim()
        }
        return text.substringAfter("open ").trim()
    }

    private fun extractContactName(text: String): String {
        val prefixes = listOf("call ", "dial ", "phone ", "ring ", "call me ", "call up ")
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) return text.removePrefix(prefix).trim()
        }
        return text.substringAfter("call ").trim()
    }

    private fun extractAlarmTime(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val timePattern = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|AM|PM)?""")
        val match = timePattern.find(text)
        if (match != null) {
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val amPm = match.groupValues[3].lowercase()
            when {
                amPm == "pm" && hour != 12 -> hour += 12
                amPm == "am" && hour == 12 -> hour = 0
            }
            result["hour"] = hour.toString()
            result["minute"] = minute.toString()
        }
        val forIdx = text.indexOf(" for ")
        if (forIdx != -1) {
            val label = text.substring(forIdx + 5).trim()
            if (label.isNotEmpty() && !label.first().isDigit()) result["label"] = label
        }
        return result
    }

    private fun extractMemoryKey(text: String): String {
        return when {
            text.contains("name")            -> "user_name"
            text.contains("nickname")        -> "user_nickname"
            text.contains("favorite app")    -> "favorite_app"
            text.contains("favorite contact") -> "favorite_contact"
            else                             -> "general"
        }
    }

    private fun extractMemoryKeyValue(text: String): Pair<String, String> {
        return when {
            text.startsWith("my name is") ->
                "user_name" to text.removePrefix("my name is").trim()
            text.startsWith("call me ") ->
                "user_nickname" to text.removePrefix("call me ").trim()
            text.contains("my favorite app is") ->
                "favorite_app" to text.substringAfter("my favorite app is").trim()
            text.startsWith("remember that ") ->
                "custom_${System.currentTimeMillis()}" to text.removePrefix("remember that ").trim()
            else -> "custom_info" to text
        }
    }
}
