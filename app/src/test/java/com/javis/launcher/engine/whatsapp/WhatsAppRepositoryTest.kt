package com.javis.launcher.engine.whatsapp.repository

import junit.framework.Assert.*
import org.junit.Test
import com.javis.launcher.engine.whatsapp.WhatsAppModels.WhatsAppMessage

class WhatsAppRepositoryTest {

    @Test
    fun testWhatsAppMessageMappingToModel() {
        val mapping = WhatsAppMessageMapping(
            remoteMessageId = "msg1",
            chatId = "chat1",
            senderJid = "sender1",
            senderName = "Aisha",
            text = "Hello",
            timestamp = 5000L,
            isFromMe = false,
            isRead = true,
            quotedMessageId = "q1",
            quotedText = "reply text"
        )
        val model = mapping.toModel()
        assertEquals("msg1", model.id)
        assertEquals("chat1", model.chatId)
        assertEquals("sender1", model.senderId)
        assertEquals("Aisha", model.senderName)
        assertEquals("Hello", model.text)
        assertEquals(5000L, model.timestamp)
        assertFalse(model.isFromMe)
        assertTrue(model.isRead)
        assertEquals("q1", model.quotedMessageId)
        assertEquals("reply text", model.quotedText)
    }

    @Test
    fun testWhatsAppMessageMappingFromModel() {
        val model = WhatsAppMessage(
            id = "msg1",
            chatId = "chat1",
            senderId = "s1",
            senderName = "Bob",
            text = "Hi",
            timestamp = 1000L,
            isFromMe = true,
            isRead = false
        )
        val mapping = WhatsAppMessageMapping.fromModel(model)
        assertEquals("msg1", mapping.remoteMessageId)
        assertEquals("chat1", mapping.chatId)
        assertEquals("s1", mapping.senderJid)
        assertEquals("Bob", mapping.senderName)
        assertEquals("Hi", mapping.text)
        assertEquals(1000L, mapping.timestamp)
        assertTrue(mapping.isFromMe)
        assertFalse(mapping.isRead)
    }

    @Test
    fun testWhatsAppMessageMappingDefaultValues() {
        val mapping = WhatsAppMessageMapping()
        assertEquals("", mapping.remoteMessageId)
        assertEquals("", mapping.chatId)
        assertNull(mapping.quotedMessageId)
        assertNull(mapping.quotedText)
    }
}
