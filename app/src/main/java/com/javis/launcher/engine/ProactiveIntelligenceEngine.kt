package com.javis.launcher.engine

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.javis.launcher.JavisApplication
import com.javis.launcher.engine.memory.MemoryEngine
import com.javis.launcher.models.AppUsage
import com.javis.launcher.models.ContactUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object ProactiveIntelligenceEngine {

    data class ProactiveBriefing(
        val headline: String,
        val details: List<String> = emptyList(),
        val priority: Int = 0
    )

    data class CalendarEvent(
        val title: String,
        val startTime: Long,
        val endTime: Long,
        val location: String? = null
    )

    suspend fun generateMorningBriefing(memory: MemoryEngine): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val name = memory.getUserName()
        val address = if (name != null) ", $name" else ", Sir"

        sb.append("Good morning$address. ")

        val topApps = memory.getTopApps(3)
        if (topApps.isNotEmpty()) {
            val appNames = topApps.map { it.appName }.joinToString(", ")
            sb.append("Based on your habits, you usually open $appNames in the morning. ")
        }

        val topContacts = memory.getTopContacts(2)
        if (topContacts.isNotEmpty()) {
            sb.append("You frequently call ${topContacts.first().name} — would you like me to dial them? ")
        }

        sb.append("All systems are operational. ")
        sb.append(generateWeatherSnippet())
        sb.append("How can I help you today?")
        sb.toString()
    }

    suspend fun generateAfternoonBriefing(memory: MemoryEngine): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val name = memory.getUserName()
        val address = if (name != null) ", $name" else ", Sir"

        sb.append("Good afternoon$address. ")
        sb.append(generateWeatherSnippet())

        val topApps = memory.getTopApps(3)
        if (topApps.isNotEmpty()) {
            val appNames = topApps.map { it.appName }.joinToString(", ")
            sb.append("This afternoon you typically use $appNames. ")
        }

        sb.append("Need anything?")
        sb.toString()
    }

    suspend fun generateEveningBriefing(memory: MemoryEngine): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        val name = memory.getUserName()
        val address = if (name != null) ", $name" else ", Sir"

        sb.append("Good evening$address. ")

        val topContacts = memory.getTopContacts(2)
        if (topContacts.isNotEmpty()) {
            sb.append("You often call ${topContacts.first().name} in the evening. ")
        }

        sb.append(generateWeatherSnippet())
        sb.append("Anything else before the day ends?")
        sb.toString()
    }

    suspend fun getProactiveSuggestions(memory: MemoryEngine, context: Context): List<ProactiveBriefing> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<ProactiveBriefing>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val minute = Calendar.getInstance().get(Calendar.MINUTE)

        if (hour == 8 && minute < 15) {
            suggestions.add(ProactiveBriefing(
                headline = "Morning briefing ready",
                details = listOf("Weather update", "Top apps", "Frequent contacts"),
                priority = 10
            ))
        }

        if (hour in 12..13) {
            suggestions.add(ProactiveBriefing(
                headline = "Lunchtime",
                details = listOf("Any calls to make?", "Check messages"),
                priority = 6
            ))
        }

        if (hour in 17..18) {
            suggestions.add(ProactiveBriefing(
                headline = "End of day approaching",
                details = listOf("Set tomorrow's alarm", "Review tomorrow's schedule"),
                priority = 8
            ))
        }

        if (hour in 21..22) {
            suggestions.add(ProactiveBriefing(
                headline = "Wind down time",
                details = listOf("Any reminders?", "Prepare for tomorrow"),
                priority = 6
            ))
        }

        val topApps = memory.getTopApps(5)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        topApps.forEach { app ->
            if (isTypicalAppTime(app, currentHour)) {
                suggestions.add(ProactiveBriefing(
                    headline = "Usually open ${app.appName} now",
                    details = listOf("Based on your routine"),
                    priority = 5
                ))
            }
        }

        suggestions
    }

    suspend fun checkSystemHealth(): String = withContext(Dispatchers.IO) {
        val status = SystemDiagnosticsEngine.getSystemStatus()
        SystemDiagnosticsEngine.generateStatusReport(status)
    }

    fun scheduleRepeatingBriefing(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, JavisApplication::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun generateWeatherSnippet(): String {
        return try {
            val prefs = JavisApplication.instance.getSharedPreferences("javis_weather", Context.MODE_PRIVATE)
            val lastWeather = prefs.getString("last_weather", null)
            if (!lastWeather.isNullOrBlank()) {
                "Weather update: $lastWeather. "
            } else {
                "Weather data not yet configured. "
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun isTypicalAppTime(app: AppUsage, currentHour: Int): Boolean {
        val lastUsed = Calendar.getInstance().apply { timeInMillis = app.lastUsed }
        val typicalHour = lastUsed.get(Calendar.HOUR_OF_DAY)
        return typicalHour == currentHour && app.useCount > 5
    }
}
