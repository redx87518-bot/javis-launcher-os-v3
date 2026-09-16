package com.javis.launcher.engine.whatsapp.tools

import junit.framework.Assert.*
import org.junit.Test
import org.json.JSONObject

class WhatsAppToolsTest {

    @Test
    fun testConnectionToolName() {
        val tool = WhatsAppConnectionTool(object : com.javis.launcher.engine.whatsapp.WhatsAppClient {
            override suspend fun connectionStatus() = com.javis.launcher.engine.whatsapp.WhatsAppConnectionState(isConnected = true)
            override suspend fun linkAccount(phoneNumber: String) = kotlinx.coroutines.flow.flowOf()
            override suspend fun disconnect() {}
            override suspend fun logout() {}
            override suspend fun findContact(query: String) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppContact>()
            override suspend fun getChat(chatId: String) = null
            override suspend fun getRecentMessages(chatId: String, limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getUnreadMessages(limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun searchMessages(query: String, chatId: String?) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getMessage(messageId: String) = null
            override suspend fun sendMessage(chatId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
            override suspend fun reply(chatId: String, messageId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
        })
        assertEquals("whatsapp.connection_status", tool.name)
        assertNotNull(tool.parametersSchema)
    }

    @Test
    fun testSendMessageToolName() {
        val tool = WhatsAppSendMessageTool(object : com.javis.launcher.engine.whatsapp.WhatsAppClient {
            override suspend fun connectionStatus() = com.javis.launcher.engine.whatsapp.WhatsAppConnectionState(isConnected = true)
            override suspend fun linkAccount(phoneNumber: String) = kotlinx.coroutines.flow.flowOf()
            override suspend fun disconnect() {}
            override suspend fun logout() {}
            override suspend fun findContact(query: String) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppContact>()
            override suspend fun getChat(chatId: String) = null
            override suspend fun getRecentMessages(chatId: String, limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getUnreadMessages(limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun searchMessages(query: String, chatId: String?) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getMessage(messageId: String) = null
            override suspend fun sendMessage(chatId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
            override suspend fun reply(chatId: String, messageId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
        })
        assertEquals("whatsapp.send_message", tool.name)
        val schema = tool.parametersSchema ?: JSONObject()
        assertTrue(schema.has("chatId"))
        assertTrue(schema.has("text"))
    }

    @Test
    fun testSendMessageToolMissingChatId() {
        val tool = WhatsAppSendMessageTool(object : com.javis.launcher.engine.whatsapp.WhatsAppClient {
            override suspend fun connectionStatus() = com.javis.launcher.engine.whatsapp.WhatsAppConnectionState(isConnected = true)
            override suspend fun linkAccount(phoneNumber: String) = kotlinx.coroutines.flow.flowOf()
            override suspend fun disconnect() {}
            override suspend fun logout() {}
            override suspend fun findContact(query: String) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppContact>()
            override suspend fun getChat(chatId: String) = null
            override suspend fun getRecentMessages(chatId: String, limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getUnreadMessages(limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun searchMessages(query: String, chatId: String?) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getMessage(messageId: String) = null
            override suspend fun sendMessage(chatId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
            override suspend fun reply(chatId: String, messageId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
        })
        val result = tool.execute(mapOf("text" to "hello"))
        assertTrue(result is ToolResult.Failed)
    }

    @Test
    fun testReplyToolName() {
        val tool = WhatsAppReplyTool(object : com.javis.launcher.engine.whatsapp.WhatsAppClient {
            override suspend fun connectionStatus() = com.javis.launcher.engine.whatsapp.WhatsAppConnectionState(isConnected = true)
            override suspend fun linkAccount(phoneNumber: String) = kotlinx.coroutines.flow.flowOf()
            override suspend fun disconnect() {}
            override suspend fun logout() {}
            override suspend fun findContact(query: String) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppContact>()
            override suspend fun getChat(chatId: String) = null
            override suspend fun getRecentMessages(chatId: String, limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getUnreadMessages(limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun searchMessages(query: String, chatId: String?) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getMessage(messageId: String) = null
            override suspend fun sendMessage(chatId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
            override suspend fun reply(chatId: String, messageId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
        })
        assertEquals("whatsapp.reply", tool.name)
    }

    @Test
    fun testFindContactToolName() {
        val tool = WhatsAppFindContactTool(object : com.javis.launcher.engine.whatsapp.WhatsAppClient {
            override suspend fun connectionStatus() = com.javis.launcher.engine.whatsapp.WhatsAppConnectionState(isConnected = true)
            override suspend fun linkAccount(phoneNumber: String) = kotlinx.coroutines.flow.flowOf()
            override suspend fun disconnect() {}
            override suspend fun logout() {}
            override suspend fun findContact(query: String) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppContact>()
            override suspend fun getChat(chatId: String) = null
            override suspend fun getRecentMessages(chatId: String, limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getUnreadMessages(limit: Int) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun searchMessages(query: String, chatId: String?) = emptyList<com.javis.launcher.engine.whatsapp.WhatsAppMessage>()
            override suspend fun getMessage(messageId: String) = null
            override suspend fun sendMessage(chatId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
            override suspend fun reply(chatId: String, messageId: String, text: String) = Result.success(com.javis.launcher.engine.whatsapp.WhatsAppMessage())
        })
        assertEquals("whatsapp.find_contact", tool.name)
    }
}
