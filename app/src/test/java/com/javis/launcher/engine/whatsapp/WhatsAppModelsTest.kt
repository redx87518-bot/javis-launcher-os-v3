package com.javis.launcher.engine.whatsapp

import junit.framework.Assert.*
import org.junit.Test

class WhatsAppModelsTest {

    @Test
    fun testWhatsAppContact() {
        val contact = WhatsAppContact(
            id = "123",
            displayName = "Aisha",
            phoneNumber = "+234XXXXXXXXXX",
            jid = "aisha@s.whatsapp.net",
            verified = true
        )
        assertEquals("123", contact.id)
        assertEquals("Aisha", contact.displayName)
        assertEquals("+234XXXXXXXXXX", contact.phoneNumber)
        assertEquals("aisha@s.whatsapp.net", contact.jid)
        assertTrue(contact.verified)
    }

    @Test
    fun testWhatsAppChat() {
        val chat = WhatsAppChat(
            chatId = "chat123",
            name = "Aisha",
            phoneNumber = "+234XXXXXXXXXX",
            jid = "aisha@s.whatsapp.net",
            lastMessage = "Hello",
            lastMessageTimestamp = 1000L,
            unreadCount = 3
        )
        assertEquals("chat123", chat.chatId)
        assertEquals(3, chat.unreadCount)
    }

    @Test
    fun testWhatsAppMessage() {
        val msg = WhatsAppMessage(
            id = "msg1",
            chatId = "chat1",
            senderId = "sender1",
            senderName = "Aisha",
            text = "Hello there",
            timestamp = 5000L,
            isFromMe = false,
            isRead = false,
            quotedMessageId = "ref1",
            quotedText = "previous"
        )
        assertFalse(msg.isFromMe)
        assertFalse(msg.isRead)
        assertEquals("ref1", msg.quotedMessageId)
        assertEquals("previous", msg.quotedText)
    }

    @Test
    fun testWhatsAppMessageDefaultValues() {
        val msg = WhatsAppMessage()
        assertEquals("", msg.id)
        assertEquals("", msg.chatId)
        assertTrue(msg.text.isEmpty())
        assertFalse(msg.isFromMe)
        assertNull(msg.quotedMessageId)
        assertNull(msg.quotedText)
    }

    @Test
    fun testWhatsAppConnectionState() {
        val state = WhatsAppConnectionState(
            isConnected = true,
            phoneNumber = "+234XXXXXXXXXX"
        )
        assertTrue(state.isConnected)
        assertEquals("+234XXXXXXXXXX", state.phoneNumber)
        assertNull(state.pairCode)
    }
}
