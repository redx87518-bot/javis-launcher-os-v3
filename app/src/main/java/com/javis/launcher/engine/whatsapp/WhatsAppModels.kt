package com.javis.launcher.engine.whatsapp

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
