package com.javis.launcher.engine.memory

import android.content.Context
import com.javis.launcher.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemoryEngine(private val context: Context) {

    private val db = MemoryDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // ─── Key-Value Memory ──────────────────────────────────────────────────
    fun remember(key: String, value: String, category: String = "general") {
        scope.launch {
            db.memoryDao().insert(Memory(key = key, value = value, category = category))
        }
    }

    suspend fun recall(key: String): String? = withContext(Dispatchers.IO) {
        db.memoryDao().getByKey(key)?.value
    }

    suspend fun recallAll(): List<Memory> = withContext(Dispatchers.IO) {
        db.memoryDao().getAll()
    }

    suspend fun recallByCategory(category: String): List<Memory> = withContext(Dispatchers.IO) {
        db.memoryDao().getByCategory(category)
    }

    fun forget(key: String) {
        scope.launch { db.memoryDao().deleteByKey(key) }
    }

    // ─── Conversation History (V4: up to 100 messages) ─────────────────────
    fun saveMessage(role: String, content: String) {
        scope.launch {
            db.conversationDao().insert(ConversationMessage(role = role, content = content))
            // Keep last 30 days
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L
            db.conversationDao().deleteOlderThan(cutoff)
        }
    }

    suspend fun getRecentHistory(limit: Int = 50): List<ConversationMessage> =
        withContext(Dispatchers.IO) {
            db.conversationDao().getRecent(limit).reversed()
        }

    // ─── App Usage ─────────────────────────────────────────────────────────
    fun trackAppOpen(packageName: String, appName: String) {
        scope.launch {
            val existing = db.appUsageDao().getApp(packageName)
            val updated = existing?.copy(useCount = existing.useCount + 1, lastUsed = System.currentTimeMillis())
                ?: AppUsage(packageName, appName)
            db.appUsageDao().insert(updated)
        }
    }

    suspend fun getTopApps(limit: Int = 6): List<AppUsage> = withContext(Dispatchers.IO) {
        db.appUsageDao().getTopApps(limit)
    }

    // ─── Contact Usage ─────────────────────────────────────────────────────
    fun trackContactCall(contact: Contact) {
        scope.launch {
            val existing = db.contactUsageDao().getContact(contact.id)
            val updated = existing?.copy(callCount = existing.callCount + 1, lastCalled = System.currentTimeMillis())
                ?: ContactUsage(contact.id, contact.name, contact.phone)
            db.contactUsageDao().insert(updated)
        }
    }

    suspend fun getTopContacts(limit: Int = 6): List<ContactUsage> = withContext(Dispatchers.IO) {
        db.contactUsageDao().getTopContacts(limit)
    }

    // ─── Command Log (V4) ──────────────────────────────────────────────────
    fun logCommand(action: String, detail: String = "", result: String = "") {
        scope.launch {
            db.commandLogDao().insert(CommandLog(action = action, detail = detail, result = result))
            // Keep last 7 days of logs
            val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000L
            db.commandLogDao().deleteOlderThan(cutoff)
        }
    }

    suspend fun getCommandLogs(limit: Int = 100): List<CommandLog> = withContext(Dispatchers.IO) {
        db.commandLogDao().getRecent(limit)
    }

    // ─── User Identity ─────────────────────────────────────────────────────
    fun setUserName(name: String) = remember("user_name", name, "identity")
    suspend fun getUserName(): String? = recall("user_name")
    fun setNickname(nick: String) = remember("user_nickname", nick, "identity")
    suspend fun getNickname(): String? = recall("user_nickname")
}
