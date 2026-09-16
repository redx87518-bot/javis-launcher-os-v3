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

// ─── App Usage ────────────────────────────────────────────────────────────
@Entity(tableName = "app_usage")
data class AppUsage(
    @PrimaryKey val packageName: String,
    val appName: String,
    val useCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

// ─── Contact Usage ────────────────────────────────────────────────────────
@Entity(tableName = "contact_usage")
data class ContactUsage(
    @PrimaryKey val contactId: String,
    val name: String,
    val phone: String,
    val callCount: Int = 1,
    val lastCalled: Long = System.currentTimeMillis()
)

// ─── Command Log (V4) ──────────────────────────────────────────────────────
@Entity(tableName = "command_log")
data class CommandLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val detail: String = "",
    val result: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Contact ────────────────────────────────────────────────────────────────
data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val photoUri: String? = null
)

// ─── Installed App ────────────────────────────────────────────────────────
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable? = null
)

// ─── Chat Message (UI layer) ────────────────────────────────────────────
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

// ─── AI Provider ──────────────────────────────────────────────────────────
enum class AIProvider { OPENROUTER, GROQ, DEEPSEEK }

data class ProviderConfig(
    val provider: AIProvider,
    val apiKey: String,
    val model: String,
    var isActive: Boolean = false,
    var latencyMs: Long = -1,
    var lastUsed: Long = 0
)

// ─── WhatsApp Models ──────────────────────────────────────────────────────
data class WhatsAppContact(
    val id: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val jid: String = "",
    val verified: Boolean = false
)

data class WhatsAppChat(
    val chatId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val jid: String = "",
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0,
    val unreadCount: Int = 0
)

data class WhatsAppMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val isFromMe: Boolean = false,
    val isRead: Boolean = false,
    val quotedMessageId: String? = null,
    val quotedText: String? = null
)

data class WhatsAppConnectionState(
    val isConnected: Boolean = false,
    val isPairing: Boolean = false,
    val pairCode: String? = null,
    val errorMessage: String? = null,
    val phoneNumber: String? = null
)

// ─── Intent Result ────────────────────────────────────────────────────────
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
    CLEAR_MISSED_CALLS,
    SWITCH_PERSONALITY,
    ROUTINE_QUERY,
    SYSTEM_DIAGNOSTICS,
    WEATHER_QUERY,
    NEWS_BRIEFING,
    AUTOMATION_ROUTINE,
    WHATSAPP_MESSAGE,
    WHATSAPP_READ,
    WHATSAPP_LINK,
    WHATSAPP_UNLINK,
    UNKNOWN
}

// ─── Context State ────────────────────────────────────────────────────────
data class ConversationContext(
    var lastContact: Contact? = null,
    var lastApp: InstalledApp? = null,
    var lastAction: JavisAction? = null,
    var lastTopic: String? = null,
    var currentGoal: String? = null,
    var lastPhoneNumber: String? = null,
    var lastWhatsAppJid: String? = null,
    var lastChatId: String? = null,
    var lastMessageId: String? = null,
    var lastMessageSender: String? = null,
    var lastMessageText: String? = null,
    var currentTask: String? = null,
    var pendingConfirmation: String? = null,
    var lastToolUsed: String? = null,
    var lastToolResult: String? = null
)

// ─── Voice State ──────────────────────────────────────────────────────────
enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, COMPLETED, ERROR }
