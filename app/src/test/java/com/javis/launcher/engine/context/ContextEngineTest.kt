package com.javis.launcher.engine.context

import junit.framework.Assert.*
import com.javis.launcher.models.Contact
import com.javis.launcher.models.ConversationContext
import org.junit.Test

class ContextEngineTest {

    @Test
    fun testContextDefaults() {
        ContextEngine.reset()
        assertNull(ContextEngine.context.lastContact)
        assertNull(ContextEngine.context.lastPhoneNumber)
        assertNull(ContextEngine.context.lastWhatsAppJid)
        assertNull(ContextEngine.context.lastChatId)
        assertNull(ContextEngine.context.lastMessageText)
        assertNull(ContextEngine.context.currentGoal)
    }

    @Test
    fun testUpdateContact() {
        ContextEngine.reset()
        ContextEngine.updateContact(Contact(id = "1", name = "Aisha", phone = "+234"))
        assertEquals("Aisha", ContextEngine.context.lastContact?.name)
    }

    @Test
    fun testUpdateWhatsAppJid() {
        ContextEngine.reset()
        ContextEngine.updateWhatsAppJid("aisha@s.whatsapp.net")
        assertEquals("aisha@s.whatsapp.net", ContextEngine.context.lastWhatsAppJid)
    }

    @Test
    fun testUpdateChatId() {
        ContextEngine.reset()
        ContextEngine.updateChatId("chat123")
        assertEquals("chat123", ContextEngine.context.lastChatId)
    }

    @Test
    fun testUpdateMessage() {
        ContextEngine.reset()
        ContextEngine.updateMessageText("Hello world")
        ContextEngine.updateMessageSender("Aisha")
        assertEquals("Hello world", ContextEngine.context.lastMessageText)
        assertEquals("Aisha", ContextEngine.context.lastMessageSender)
    }

    @Test
    fun testUpdateConfirmation() {
        ContextEngine.reset()
        ContextEngine.updateConfirmation("Are you sure?")
        assertEquals("Are you sure?", ContextEngine.context.pendingConfirmation)
    }

    @Test
    fun testUpdateConfirmationClears() {
        ContextEngine.reset()
        ContextEngine.updateConfirmation("Are you sure?")
        ContextEngine.updateConfirmation(null)
        assertNull(ContextEngine.context.pendingConfirmation)
    }

    @Test
    fun testResolveContactReferencePronoun() {
        ContextEngine.reset()
        ContextEngine.updateContact(Contact(id = "1", name = "Aisha", phone = "+234"))
        assertNotNull(ContextEngine.resolveContactReference("she"))
        assertNotNull(ContextEngine.resolveContactReference("her"))
        assertNotNull(ContextEngine.resolveContactReference("that person"))
    }

    @Test
    fun testResolveContactReferenceName() {
        ContextEngine.reset()
        ContextEngine.updateContact(Contact(id = "1", name = "Aisha", phone = "+234"))
        assertNotNull(ContextEngine.resolveContactReference("Aisha"))
    }

    @Test
    fun testResolveContactReferenceUnknown() {
        ContextEngine.reset()
        assertNull(ContextEngine.resolveContactReference("Random Person"))
    }

    @Test
    fun testResolveContactReferenceNullContext() {
        ContextEngine.reset()
        assertNull(ContextEngine.resolveContactReference("she"))
    }

    @Test
    fun testBuildContextSummary() {
        ContextEngine.reset()
        ContextEngine.updateContact(Contact(id = "1", name = "Aisha", phone = "+234"))
        ContextEngine.updateGoal("Send message to Aisha")
        val summary = ContextEngine.buildContextSummary()
        assertTrue(summary.contains("Aisha"))
        assertTrue(summary.contains("Send message to Aisha"))
    }

    @Test
    fun testResetClearsAll() {
        ContextEngine.updateContact(Contact(id = "1", name = "Aisha", phone = "+234"))
        ContextEngine.updateGoal("test")
        ContextEngine.updateWhatsAppJid("jid")
        ContextEngine.reset()
        assertNull(ContextEngine.context.lastContact)
        assertNull(ContextEngine.context.currentGoal)
        assertNull(ContextEngine.context.lastWhatsAppJid)
    }

    @Test
    fun testUpdatePhoneNumber() {
        ContextEngine.reset()
        ContextEngine.updatePhoneNumber("+234XXXXXXXXXX")
        assertEquals("+234XXXXXXXXXX", ContextEngine.context.lastPhoneNumber)
    }

    @Test
    fun testUpdateTask() {
        ContextEngine.reset()
        ContextEngine.updateTask("Send message")
        assertEquals("Send message", ContextEngine.context.currentTask)
    }

    @Test
    fun testUpdateToolUsed() {
        ContextEngine.reset()
        ContextEngine.updateToolUsed("whatsapp.send_message")
        assertEquals("whatsapp.send_message", ContextEngine.context.lastToolUsed)
    }

    @Test
    fun testUpdateToolResult() {
        ContextEngine.reset()
        ContextEngine.updateToolResult("success")
        assertEquals("success", ContextEngine.context.lastToolResult)
    }
}
