package com.javis.launcher.engine.whatsapp.repository

import android.content.Context
import android.util.Log
import com.javis.launcher.engine.whatsapp.WhatsAppModels.WhatsAppMessage
import com.javis.launcher.engine.whatsapp.WhatsAppModels.WhatsAppChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WhatsAppRepository(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppRepository"
        private const val PREFS = "javis_whatsapp_data"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _connectionState = MutableStateFlow(WhatsAppConnectionState())
    val connectionState: StateFlow<WhatsAppConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    val messages: StateFlow<List<WhatsAppMessage>> = _messages.asStateFlow()

    private val _chats = MutableStateFlow<List<WhatsAppChat>>(emptyList())
    val chats: StateFlow<List<WhatsAppChat>> = _chats.asStateFlow()

    fun updateConnectionState(state: WhatsAppConnectionState) {
        _connectionState.value = state
        prefs.edit().putString("conn_status", state.toString()).apply()
    }

    fun storeMessages(messages: List<WhatsAppMessage>) {
        _messages.value = messages
        prefs.edit().putString("messages_count", messages.size.toString()).apply()
    }

    fun storeChats(chats: List<WhatsAppChat>) {
        _chats.value = chats
    }

    fun addMessage(message: WhatsAppMessage) {
        val current = _messages.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            current[existingIndex] = message
        } else {
            current.add(0, message)
        }
        _messages.value = current.take(100)
    }

    fun getStoredPhoneNumber(): String? {
        return prefs.getString("linked_phone", null)
    }

    fun storePhoneNumber(phone: String) {
        prefs.edit().putString("linked_phone", phone).apply()
    }

    fun isConnected(): Boolean {
        return prefs.getString("conn_status", "DISCONNECTED")?.contains("CONNECTED") == true
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _connectionState.value = WhatsAppConnectionState()
        _messages.value = emptyList()
        _chats.value = emptyList()
    }

    fun getLastMessage(): WhatsAppMessage? {
        return _messages.value.firstOrNull()
    }

    fun getMessagesFromSender(senderName: String): List<WhatsAppMessage> {
        return _messages.value.filter { it.senderName == senderName || it.senderId == senderName }
    }
}
