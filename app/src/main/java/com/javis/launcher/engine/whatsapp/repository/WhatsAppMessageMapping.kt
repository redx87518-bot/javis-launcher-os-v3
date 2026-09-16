package com.javis.launcher.engine.whatsapp.repository

import com.javis.launcher.engine.whatsapp.WhatsAppMessage

data class WhatsAppMessageMapping(
    val remoteMessageId: String,
    val chatId: String,
    val senderJid: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val isRead: Boolean,
    val quotedMessageId: String? = null,
    val quotedText: String? = null
) {
    fun toModel(): WhatsAppMessage {
        return WhatsAppMessage(
            id = remoteMessageId,
            chatId = chatId,
            senderId = senderJid,
            senderName = senderName,
            text = text,
            timestamp = timestamp,
            isFromMe = isFromMe,
            isRead = isRead,
            quotedMessageId = quotedMessageId,
            quotedText = quotedText
        )
    }

    companion object {
        fun fromModel(msg: WhatsAppMessage): WhatsAppMessageMapping {
            return WhatsAppMessageMapping(
                remoteMessageId = msg.id,
                chatId = msg.chatId,
                senderJid = msg.senderId,
                senderName = msg.senderName,
                text = msg.text,
                timestamp = msg.timestamp,
                isFromMe = msg.isFromMe,
                isRead = msg.isRead,
                quotedMessageId = msg.quotedMessageId,
                quotedText = msg.quotedText
            )
        }
    }
}
