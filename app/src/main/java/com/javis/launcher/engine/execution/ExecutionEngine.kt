package com.javis.launcher.engine.execution

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import com.javis.launcher.JavisApplication
import com.javis.launcher.engine.PersonalityEngine
import com.javis.launcher.engine.RoutineLearningEngine
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.memory.MemoryEngine
import com.javis.launcher.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class NeedsConfirmation(val message: String, val options: List<String>) : ExecutionResult()
    data class Failure(val message: String) : ExecutionResult()
}

class ExecutionEngine(private val context: Context) {

    private val memory: MemoryEngine?
        get() = JavisApplication.instance.memoryEngine

    suspend fun execute(intent: IntentResult): ExecutionResult = withContext(Dispatchers.Main) {
        Log.d("ExecutionEngine", "Executing ${intent.action}")
        val mem = memory ?: return@withContext ExecutionResult.Failure("Memory engine not initialized.")
        val result = when (intent.action) {
            JavisAction.OPEN_APP           -> openApp(intent.params["appName"] ?: "")
            JavisAction.CALL_CONTACT       -> callContact(intent.params["contactName"] ?: "")
            JavisAction.SET_ALARM          -> setAlarm(intent.params)
            JavisAction.QUERY_MEMORY       -> queryMemory(intent.params["key"] ?: "")
            JavisAction.UPDATE_MEMORY      -> updateMemory(intent.params)
            JavisAction.CLEAR_MISSED_CALLS -> clearMissedCalls()
            JavisAction.SWITCH_PERSONALITY -> switchPersonality(intent.params["mode"] ?: "JARVIS")
            JavisAction.ROUTINE_QUERY      -> routineQuery()
            JavisAction.OPEN_SETTINGS -> {
                val i = Intent(android.provider.Settings.ACTION_SETTINGS)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(i)
                ExecutionResult.Success("Opening settings.")
            }
            JavisAction.CHAT    -> ExecutionResult.Failure("CHAT")
            JavisAction.UNKNOWN -> ExecutionResult.Failure("I'm not sure what you meant. Could you clarify?")
        }

        // Log to Command Center (skip CHAT routing noise)
        if (intent.action != JavisAction.CHAT) {
            val actionLabel = intent.action.name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
            val detail = intent.params.values.firstOrNull() ?: ""
            val resultLabel = when (result) {
                is ExecutionResult.Success           -> "Done: ${result.message.take(40)}"
                is ExecutionResult.Failure           ->
                    if (result.message == "CHAT") "" else "Failed: ${result.message.take(40)}"
                is ExecutionResult.NeedsConfirmation -> "Needs input"
            }
            mem.logCommand(actionLabel, detail, resultLabel)
        }

        result
    }

    // ─── App Opening ───────────────────────────────────────────────────────
    private fun openApp(appName: String): ExecutionResult {
        val mem = memory ?: return ExecutionResult.Failure("Memory engine not initialized.")
        if (appName.isBlank()) return ExecutionResult.Failure("Which app would you like me to open, Sir?")

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)

        val scored = apps.map { info ->
            val label = pm.getApplicationLabel(info).toString()
            val lowerLabel = label.lowercase()
            val lowerName = appName.lowercase()
            val score = when {
                lowerLabel == lowerName -> 0
                lowerLabel.contains(lowerName) -> 1 + (label.length - appName.length).coerceAtLeast(0)
                else -> levenshtein(lowerName, lowerLabel) + 2
            }
            info to score
        }.filter { it.second < 100 }
         .sortedBy { it.second }

        if (scored.isEmpty())
            return ExecutionResult.Failure("I couldn't find an app called \"$appName\" on your device.")

        val best = scored.first().first

        val launchIntent = pm.getLaunchIntentForPackage(best.packageName)
            ?: return ExecutionResult.Failure("\"$appName\" is installed but cannot be launched.")

        val appLabel = pm.getApplicationLabel(best).toString()
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        context.startActivity(launchIntent)

        mem.trackAppOpen(best.packageName, appLabel)
        ContextEngine.updateApp(InstalledApp(best.packageName, appLabel))

        return ExecutionResult.Success("Opening $appLabel.")
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
        }
        return dp[m][n]
    }

    // ─── Contact Calling ───────────────────────────────────────────────────
    private suspend fun callContact(name: String): ExecutionResult = withContext(Dispatchers.IO) {
        val mem = memory ?: return@withContext ExecutionResult.Failure("Memory engine not initialized.")
        if (name.isBlank()) return@withContext ExecutionResult.Failure("Who would you like me to call, Sir?")

        val contacts = withTimeout(10_000) { findContacts(name) }
        when {
            contacts.isEmpty() ->
                ExecutionResult.Failure("I couldn't find anyone named \"$name\" in your contacts.")
            contacts.size == 1 -> {
                val contact = contacts.first()
                withContext(Dispatchers.Main) { 
                    if (!initiateCall(contact)) {
                        return@withContext ExecutionResult.Failure("Memory engine not initialized.")
                    }
                }
                ExecutionResult.Success("Calling ${contact.name}.")
            }
            else -> {
                val names = contacts.take(3).mapIndexed { i, c -> "${i + 1}. ${c.name}" }
                ExecutionResult.NeedsConfirmation(
                    "I found ${contacts.size} contacts named \"$name\". Which one?",
                    names
                )
            }
        }
    }

    private fun findContacts(name: String): List<Contact> {
        val results = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            uri, projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )?.use { cursor ->
            val idIdx    = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx  = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                results.add(Contact(
                    id    = cursor.getString(idIdx),
                    name  = cursor.getString(nameIdx),
                    phone = cursor.getString(phoneIdx)
                ))
            }
        }
        return results
    }

    private fun initiateCall(contact: Contact): Boolean {
        val mem = memory ?: return false
        val intent = Intent(Intent.ACTION_CALL).apply {
            data  = Uri.parse("tel:${contact.phone}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        mem.trackContactCall(contact)
        ContextEngine.updateContact(contact)
        return true
    }

    // ─── Alarm Creation (verify before reporting success) ──────────────────
    private fun setAlarm(params: Map<String, String>): ExecutionResult {
        val hour = params["hour"]?.toIntOrNull()
            ?: return ExecutionResult.Failure("What time should I set the alarm for?")
        val minute = params["minute"]?.toIntOrNull() ?: 0
        val label  = params["label"] ?: "JAVIS Alarm"

        if (hour !in 0..23) return ExecutionResult.Failure("Hour must be between 0 and 23, Sir.")
        if (minute !in 0..59) return ExecutionResult.Failure("Minute must be between 0 and 59, Sir.")

        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val resolvedActivity = context.packageManager
            .resolveActivity(alarmIntent, PackageManager.MATCH_DEFAULT_ONLY)

        if (resolvedActivity == null) {
            return ExecutionResult.Failure(
                "I couldn't find a clock app to set the alarm. Please install one."
            )
        }

        return try {
            context.startActivity(alarmIntent)
            val amPm        = if (hour < 12) "AM" else "PM"
            val displayHour = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            val displayMin  = minute.toString().padStart(2, '0')
            ExecutionResult.Success("Alarm set for $displayHour:$displayMin $amPm.")
        } catch (e: Exception) {
            ExecutionResult.Failure("I wasn't able to set the alarm: ${e.message}")
        }
    }

    // ─── Clear Missed Calls ────────────────────────────────────────────────
    private suspend fun clearMissedCalls(): ExecutionResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val values = ContentValues().apply { put(CallLog.Calls.IS_READ, 1) }
            val rows = context.contentResolver.update(
                CallLog.Calls.CONTENT_URI, values,
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0")
            )
            if (rows > 0)
                ExecutionResult.Success("Done. $rows missed ${if (rows == 1) "call" else "calls"} marked as read.")
            else
                ExecutionResult.Success("No unread missed calls to clear.")
        } catch (e: SecurityException) {
            ExecutionResult.Failure("I need permission to access your call log. Please grant it in Settings > Apps > Javis > Permissions.")
        } catch (e: Exception) {
            ExecutionResult.Failure("Couldn't clear missed calls: ${e.message}")
        }
    }

    // ─── Personality Switching (V4) ────────────────────────────────────────
    private fun switchPersonality(mode: String): ExecutionResult {
        val newMode = when (mode.uppercase()) {
            "PROFESSIONAL" -> PersonalityEngine.Mode.PROFESSIONAL
            "FRIENDLY"     -> PersonalityEngine.Mode.FRIENDLY
            else           -> PersonalityEngine.Mode.JARVIS
        }
        PersonalityEngine.currentMode = newMode
        JavisApplication.instance.voiceEngine?.refreshPersonality()

        val confirm = when (newMode) {
            PersonalityEngine.Mode.JARVIS        -> "JARVIS mode activated. Formal protocols engaged, Sir."
            PersonalityEngine.Mode.PROFESSIONAL  -> "Switching to Professional mode. Let's get things done."
            PersonalityEngine.Mode.FRIENDLY      -> "Friendly mode on! Happy to chat anytime."
        }
        return ExecutionResult.Success(confirm)
    }

    // ─── Routine Query (V4) ────────────────────────────────────────────────
    private suspend fun routineQuery(): ExecutionResult {
        val mem = memory ?: return ExecutionResult.Failure("Memory engine not initialized.")
        val briefing = RoutineLearningEngine.getMorningBriefing(mem)
        return ExecutionResult.Success(briefing)
    }

    // ─── Memory Operations ─────────────────────────────────────────────────
    private suspend fun queryMemory(key: String): ExecutionResult {
        val mem = memory ?: return ExecutionResult.Failure("Memory engine not initialized.")
        return when (key) {
            "user_name" -> {
                val name = mem.getUserName()
                if (name != null) ExecutionResult.Success("Your name is $name.")
                else ExecutionResult.Failure("I don't have your name stored yet.")
            }
            "user_nickname" -> {
                val nick = mem.getNickname()
                if (nick != null) ExecutionResult.Success("I call you $nick.")
                else ExecutionResult.Failure("I don't have a nickname for you yet.")
            }
            else -> {
                val value = mem.recall(key)
                if (value != null) ExecutionResult.Success("I remember: $value")
                else ExecutionResult.Failure("I don't have that stored.")
            }
        }
    }

    private suspend fun updateMemory(params: Map<String, String>): ExecutionResult {
        val mem = memory ?: return ExecutionResult.Failure("Memory engine not initialized.")
        val key   = params["key"]   ?: return ExecutionResult.Failure("I couldn't understand what to remember.")
        val value = params["value"] ?: return ExecutionResult.Failure("I couldn't understand what value to store.")
        when (key) {
            "user_name"     -> mem.setUserName(value)
            "user_nickname" -> mem.setNickname(value)
            else            -> mem.remember(key, value)
        }
        return ExecutionResult.Success("Got it. I've saved that to memory.")
    }
}
