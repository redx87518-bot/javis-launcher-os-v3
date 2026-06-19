package com.javis.launcher.models

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Memory ────────────────────────────────────────────────────────────────
@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Conversation ──────────────────────────────────────────────────────────
@Entity(tableName = "conversations")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── App Usage ─────────────────────────────────────────────────────────────
@Entity(tableName = "app_usage")
data class AppUsage(
    @PrimaryKey val packageName: String,
    val appName: String,
    val useCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

// ─── Contact Usage ─────────────────────────────────────────────────────────
@Entity(tableName = "contact_usage")
data class ContactUsage(
    @PrimaryKey val contactId: String,
    val name: String,
    val phone: String,
    val callCount: Int = 1,
    val lastCalled: Long = System.currentTimeMillis()
)

// ─── Alarm ─────────────────────────────────────────────────────────────────
data class AlarmInfo(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
    val timeMillis: Long
)

// ─── Contact ───────────────────────────────────────────────────────────────
data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val photoUri: String? = null
)

// ─── Installed App ─────────────────────────────────────────────────────────
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable? = null
)

// ─── AI Provider ───────────────────────────────────────────────────────────
enum class AIProvider { OPENROUTER, GROQ, DEEPSEEK }

data class ProviderConfig(
    val provider: AIProvider,
    val apiKey: String,
    val model: String,
    var isActive: Boolean = false,
    var latencyMs: Long = -1
)

// ─── Intent Result ─────────────────────────────────────────────────────────
data class IntentResult(
    val action: JavisAction,
    val params: Map<String, String> = emptyMap(),
    val confidence: Float = 1.0f
)

enum class JavisAction {
    OPEN_APP,
    CALL_CONTACT,
    SET_ALARM,
    QUERY_MEMORY,
    UPDATE_MEMORY,
    CHAT,
    OPEN_SETTINGS,
    UNKNOWN
}

// ─── Context State ─────────────────────────────────────────────────────────
data class ConversationContext(
    var lastContact: Contact? = null,
    var lastApp: InstalledApp? = null,
    var lastAction: JavisAction? = null,
    var lastTopic: String? = null
)

// ─── Voice Engine State ────────────────────────────────────────────────────
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, COMPLETED, ERROR }
