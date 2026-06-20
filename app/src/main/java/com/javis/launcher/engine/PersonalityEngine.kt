package com.javis.launcher.engine

import java.util.Calendar

/**
 * JAVIS Personality Engine
 *
 * Controls how JAVIS speaks and presents responses.
 * Modes:
 *   JARVIS       — formal, calm, professional. Like the MCU JARVIS.
 *   PROFESSIONAL — business-focused, concise.
 *   FRIENDLY     — warm and conversational.
 */
object PersonalityEngine {

    enum class Mode { JARVIS, PROFESSIONAL, FRIENDLY }

    var currentMode: Mode = Mode.JARVIS

    // ── System prompt suffix injected into every AI call ─────────────────
    fun systemPromptForMode(): String = when (currentMode) {
        Mode.JARVIS -> """
            Speak like the MCU JARVIS AI: calm, articulate, and highly capable.
            Use formal but warm language. Address the user as "Sir" unless you know their name.
            Always give complete, detailed answers. Never truncate mid-thought.
            If explaining something technical, walk through it step by step.
            Responses should be as long as the question requires — never artificially short.
        """.trimIndent()

        Mode.PROFESSIONAL -> """
            Be precise, clear, and professional. Avoid small talk unless asked.
            Give detailed, structured responses. Use numbered lists for multi-step answers.
            Responses should fully address the question without unnecessary filler.
        """.trimIndent()

        Mode.FRIENDLY -> """
            Be warm, conversational, and encouraging. Use natural language.
            Show genuine interest in helping. Give thorough answers with examples.
            It's okay to be slightly informal.
        """.trimIndent()
    }

    // ── Welcome message when phone unlocks ───────────────────────────────
    fun welcomeMessage(name: String?, batteryLevel: Int, missedCalls: Int, unreadSms: Int): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val address = buildAddress(name)

        val timeGreet = when {
            hour < 5  -> "Welcome back"
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            hour < 21 -> "Good evening"
            else      -> "Welcome back"
        }

        return when (currentMode) {
            Mode.JARVIS -> buildJarvisGreeting(timeGreet, address, batteryLevel, missedCalls, unreadSms)
            Mode.PROFESSIONAL -> buildProGreeting(timeGreet, address, batteryLevel, missedCalls, unreadSms)
            Mode.FRIENDLY -> buildFriendlyGreeting(timeGreet, address, batteryLevel, missedCalls, unreadSms)
        }
    }

    private fun buildJarvisGreeting(
        greet: String, address: String,
        battery: Int, missed: Int, sms: Int
    ): String {
        val sb = StringBuilder("$greet$address.")
        if (battery in 0..15) sb.append(" Battery is critically low at $battery percent. I recommend charging immediately.")
        else if (battery in 16..30) sb.append(" Battery is at $battery percent.")
        else if (battery == 100) sb.append(" Battery is fully charged.")

        if (missed > 0 && sms > 0) {
            sb.append(" You have $missed missed ${if (missed == 1) "call" else "calls"} and $sms unread ${if (sms == 1) "message" else "messages"}.")
        } else if (missed > 0) {
            sb.append(" You have $missed missed ${if (missed == 1) "call" else "calls"}.")
        } else if (sms > 0) {
            sb.append(" You have $sms unread ${if (sms == 1) "message" else "messages"}.")
        } else {
            sb.append(" All systems are operational.")
        }
        return sb.toString()
    }

    private fun buildProGreeting(
        greet: String, address: String,
        battery: Int, missed: Int, sms: Int
    ): String {
        val sb = StringBuilder("$greet$address.")
        if (battery in 0..30) sb.append(" Battery: $battery%.")
        if (missed > 0) sb.append(" Missed calls: $missed.")
        if (sms > 0) sb.append(" Unread: $sms.")
        return sb.toString()
    }

    private fun buildFriendlyGreeting(
        greet: String, address: String,
        battery: Int, missed: Int, sms: Int
    ): String {
        val sb = StringBuilder("Hey! $greet$address.")
        if (battery in 0..15) sb.append(" Heads up — battery is really low!")
        if (missed > 0) sb.append(" You've got $missed missed ${if (missed == 1) "call" else "calls"} waiting.")
        if (sms > 0) sb.append(" And $sms unread ${if (sms == 1) "message" else "messages"}.")
        return sb.toString()
    }

    private fun buildAddress(name: String?): String {
        if (name == null) return when (currentMode) {
            Mode.JARVIS, Mode.PROFESSIONAL -> ", Sir"
            Mode.FRIENDLY -> ""
        }
        return ", $name"
    }

    // ── Format AI response with personality polish ────────────────────────
    fun formatResponse(raw: String): String {
        // JARVIS: keep it clean and complete. No forced shortening.
        return raw.trim()
    }

    fun modeName(): String = when (currentMode) {
        Mode.JARVIS       -> "JARVIS"
        Mode.PROFESSIONAL -> "Professional"
        Mode.FRIENDLY     -> "Friendly"
    }
}
