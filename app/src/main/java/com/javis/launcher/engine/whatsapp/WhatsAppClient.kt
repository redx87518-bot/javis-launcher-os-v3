package com.javis.launcher.engine.whatsapp

import kotlinx.coroutines.flow.Flow

enum class WhatsAppConnectionStatus {
    DISCONNECTED,
    PAIRING,
    CONNECTED,
    CONNECTING,
    ERROR,
    LOGGED_OUT,
    SESSION_EXPIRED
}

data class WhatsAppPairingEvent(
    val code: String,
    val expiresInSeconds: Long = 60
)

data class WhatsAppConnectionUpdate(
    val status: WhatsAppConnectionStatus,
    val phoneNumber: String? = null,
    val pairCode: String? = null,
    val errorMessage: String? = null
)

interface WhatsAppClient {
    suspend fun connectionStatus(): WhatsAppConnectionStatus
    suspend fun linkAccount(phoneNumber: String): Flow<WhatsAppConnectionUpdate>
    suspend fun disconnect()
    suspend fun logout()
    suspend fun findContact(query: String): List<WhatsAppContact>
    suspend fun getChat(chatId: String): WhatsAppChat?
    suspend fun getRecentMessages(chatId: String, limit: Int): List<WhatsAppMessage>
    suspend fun getUnreadMessages(limit: Int): List<WhatsAppMessage>
    suspend fun searchMessages(query: String, chatId: String?): List<WhatsAppMessage>
    suspend fun getMessage(messageId: String): WhatsAppMessage?
    suspend fun sendMessage(chatId: String, text: String): Result<WhatsAppMessage>
    suspend fun reply(chatId: String, messageId: String, text: String): Result<WhatsAppMessage>
}
