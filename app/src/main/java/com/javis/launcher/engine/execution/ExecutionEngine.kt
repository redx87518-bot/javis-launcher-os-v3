package com.javis.launcher.engine.execution

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CallLog
import android.provider.ContactsContract
import com.javis.launcher.JavisApplication
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class NeedsConfirmation(val message: String, val options: List<String>) : ExecutionResult()
    data class Failure(val message: String) : ExecutionResult()
}

class ExecutionEngine(private val context: Context) {

    private val memory = JavisApplication.instance.memoryEngine

    suspend fun execute(intent: IntentResult): ExecutionResult = withContext(Dispatchers.Main) {
        return@withContext when (intent.action) {
            JavisAction.OPEN_APP          -> openApp(intent.params["appName"] ?: "")
            JavisAction.CALL_CONTACT      -> callContact(intent.params["contactName"] ?: "")
            JavisAction.SET_ALARM         -> setAlarm(intent.params)
            JavisAction.QUERY_MEMORY      -> queryMemory(intent.params["key"] ?: "")
            JavisAction.UPDATE_MEMORY     -> updateMemory(intent.params)
            JavisAction.CLEAR_MISSED_CALLS -> clearMissedCalls()
            JavisAction.OPEN_SETTINGS     -> {
                val i = Intent(android.provider.Settings.ACTION_SETTINGS)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(i)
                ExecutionResult.Success("Opening settings.")
            }
            JavisAction.CHAT    -> ExecutionResult.Failure("CHAT")
            JavisAction.UNKNOWN -> ExecutionResult.Failure("I'm not sure what you meant. Could you clarify?")
        }
    }

    // ─── App Opening ───────────────────────────────────────────────────────
    private fun openApp(appName: String): ExecutionResult {
        if (appName.isBlank()) return ExecutionResult.Failure("Which app would you like me to open?")

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        val matches = apps.filter { appInfo ->
            pm.getApplicationLabel(appInfo).toString().lowercase().contains(appName.lowercase())
        }

        if (matches.isEmpty())
            return ExecutionResult.Failure("I couldn't find an app called $appName on your device.")

        val best = matches.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase() == appName.lowercase()
        } ?: matches.first()

        val launchIntent = pm.getLaunchIntentForPackage(best.packageName)
            ?: return ExecutionResult.Failure("$appName is installed but cannot be launched.")

        val appLabel = pm.getApplicationLabel(best).toString()
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        context.startActivity(launchIntent)

        memory.trackAppOpen(best.packageName, appLabel)
        ContextEngine.updateApp(InstalledApp(best.packageName, appLabel))

        return ExecutionResult.Success("$appLabel opened.")
    }

    // ─── Contact Calling ───────────────────────────────────────────────────
    private suspend fun callContact(name: String): ExecutionResult = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext ExecutionResult.Failure("Who would you like to call?")

        val contacts = findContacts(name)
        when {
            contacts.isEmpty() ->
                ExecutionResult.Failure("I couldn't find anyone named $name in your contacts.")
            contacts.size == 1 -> {
                val contact = contacts.first()
                withContext(Dispatchers.Main) { initiateCall(contact) }
                ExecutionResult.Success("Calling ${contact.name}.")
            }
            else -> {
                val names = contacts.take(3).mapIndexed { i, c -> "${i + 1}. ${c.name}" }
                ExecutionResult.NeedsConfirmation(
                    "I found ${contacts.size} contacts named $name. Which one?",
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
                results.add(Contact(cursor.getString(idIdx), cursor.getString(nameIdx), cursor.getString(phoneIdx)))
            }
        }
        return results
    }

    private fun initiateCall(contact: Contact) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data  = Uri.parse("tel:${contact.phone}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        memory.trackContactCall(contact)
        ContextEngine.updateContact(contact)
    }

    // ─── Alarm Creation ────────────────────────────────────────────────────
    private fun setAlarm(params: Map<String, String>): ExecutionResult {
        val hour   = params["hour"]?.toIntOrNull()
            ?: return ExecutionResult.Failure("I didn't catch the time. What time should I set the alarm for?")
        val minute = params["minute"]?.toIntOrNull() ?: 0
        val label  = params["label"] ?: "JAVIS Alarm"

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            val amPm         = if (hour < 12) "AM" else "PM"
            val displayHour  = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayMin   = minute.toString().padStart(2, '0')
            ExecutionResult.Success("Alarm set for $displayHour:$displayMin $amPm.")
        } catch (e: Exception) {
            ExecutionResult.Failure("I wasn't able to set the alarm. Please check your alarm app permissions.")
        }
    }

    // ─── Clear Missed Calls ────────────────────────────────────────────────
    private suspend fun clearMissedCalls(): ExecutionResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val values = ContentValues().apply { put(CallLog.Calls.IS_READ, 1) }
            val rows = context.contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                values,
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0")
            )
            if (rows > 0) {
                ExecutionResult.Success("Done. $rows missed ${if (rows == 1) "call" else "calls"} marked as read.")
            } else {
                ExecutionResult.Success("No unread missed calls to clear.")
            }
        } catch (e: SecurityException) {
            ExecutionResult.Failure("I need call log permission to do that. Please grant it in settings.")
        } catch (e: Exception) {
            ExecutionResult.Failure("I couldn't clear the missed calls: ${e.message}")
        }
    }

    // ─── Memory Operations ─────────────────────────────────────────────────
    private suspend fun queryMemory(key: String): ExecutionResult {
        return when (key) {
            "user_name" -> {
                val name = memory.getUserName()
                if (name != null) ExecutionResult.Success("Your name is $name.")
                else ExecutionResult.Failure("I don't have your name stored yet. Tell me by saying 'My name is...'")
            }
            "user_nickname" -> {
                val nick = memory.getNickname()
                if (nick != null) ExecutionResult.Success("I call you $nick.")
                else ExecutionResult.Failure("I don't have a nickname stored for you.")
            }
            else -> {
                val value = memory.recall(key)
                if (value != null) ExecutionResult.Success("I remember: $value")
                else ExecutionResult.Failure("I don't have that information stored.")
            }
        }
    }

    private suspend fun updateMemory(params: Map<String, String>): ExecutionResult {
        val key   = params["key"]   ?: return ExecutionResult.Failure("I couldn't understand what to remember.")
        val value = params["value"] ?: return ExecutionResult.Failure("I couldn't understand the value to remember.")
        if (key == "user_name") memory.setUserName(value)
        else if (key == "user_nickname") memory.setNickname(value)
        else memory.remember(key, value)
        return ExecutionResult.Success("Got it. I've saved that to memory.")
    }
}
