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

    // ─── Store a key-value memory ──────────────────────────────────────────
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

    // ─── Conversation History ──────────────────────────────────────────────
    fun saveMessage(role: String, content: String) {
        scope.launch {
            db.conversationDao().insert(ConversationMessage(role = role, content = content))
            // Keep last 7 days only
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            db.conversationDao().deleteOlderThan(cutoff)
        }
    }

    suspend fun getRecentHistory(limit: Int = 20): List<ConversationMessage> = withContext(Dispatchers.IO) {
        db.conversationDao().getRecent(limit).reversed()
    }

    // ─── App Usage Tracking ────────────────────────────────────────────────
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

    // ─── Contact Usage Tracking ────────────────────────────────────────────
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

    // ─── User Identity Shortcuts ───────────────────────────────────────────
    fun setUserName(name: String) = remember("user_name", name, "identity")
    suspend fun getUserName(): String? = recall("user_name")
    fun setNickname(nick: String) = remember("user_nickname", nick, "identity")
    suspend fun getNickname(): String? = recall("user_nickname")
}
