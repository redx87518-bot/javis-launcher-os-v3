package com.javis.launcher.engine.whatsapp.tools

import android.content.Context
import com.javis.launcher.engine.agent.JavisTool
import com.javis.launcher.engine.agent.ToolResult
import com.javis.launcher.engine.whatsapp.WhatsAppChat
import com.javis.launcher.engine.whatsapp.WhatsAppClient
import com.javis.launcher.engine.whatsapp.WhatsAppContact
import com.javis.launcher.engine.whatsapp.WhatsAppMessage
import com.javis.launcher.engine.whatsapp.WhatsAppConnectionStatus
import org.json.JSONObject

class WhatsAppConnectionTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.connection_status"
    override val description: String = "Check the current WhatsApp connection status."
    override val parametersSchema: JSONObject? = null

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        return try {
                val status = (client as? com.javis.launcher.engine.whatsapp.WhatsmeowWhatsAppClient)?.connectionStatus()
                    ?: WhatsAppConnectionStatus.DISCONNECTED
                ToolResult.Success(
                    data = mapOf(
                        "isConnected" to (status == WhatsAppConnectionStatus.CONNECTED),
                        "isPairing" to (status == WhatsAppConnectionStatus.PAIRING),
                        "errorMessage" to ""
                    ),
                    userMessage = if (status == WhatsAppConnectionStatus.CONNECTED) "WhatsApp is connected." else "WhatsApp is not connected."
                )
        } catch (e: Exception) {
            ToolResult.Failed("Failed to check WhatsApp status: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppLinkTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.link_account"
    override val description: String = "Link WhatsApp account using phone number."
    override val parametersSchema: JSONObject? = JSONObject("{\"phoneNumber\":\"string\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val phoneNumber = arguments["phoneNumber"] as? String ?: return ToolResult.Failed("Phone number required.", retryable = false)
        return try {
            val updates = client.linkAccount(phoneNumber)
            ToolResult.Success(
                data = mapOf("phoneNumber" to phoneNumber, "status" to "linking"),
                userMessage = "WhatsApp linking started for $phoneNumber."
            )
        } catch (e: Exception) {
            ToolResult.Failed("Failed to link WhatsApp: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppDisconnectTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.disconnect"
    override val description: String = "Disconnect WhatsApp session."
    override val parametersSchema: JSONObject? = null

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        return try {
            client.disconnect()
            ToolResult.Success(userMessage = "WhatsApp disconnected.")
        } catch (e: Exception) {
            ToolResult.Failed("Failed to disconnect: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppFindContactTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.find_contact"
    override val description: String = "Find a WhatsApp contact by name or phone number."
    override val parametersSchema: JSONObject? = JSONObject("{\"query\":\"string\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val query = arguments["query"] as? String ?: return ToolResult.Failed("Search query required.", retryable = false)
        return try {
            val contacts = client.findContact(query)
            if (contacts.isEmpty()) {
                ToolResult.Success(data = mapOf("contacts" to emptyList<WhatsAppContact>()), userMessage = "No contacts found matching '$query'.")
            } else {
                ToolResult.Success(
                    data = mapOf("contacts" to contacts),
                    userMessage = "Found ${contacts.size} contact(s): ${contacts.map { it.displayName }.joinToString(", ")}"
                )
            }
        } catch (e: Exception) {
            ToolResult.Failed("Failed to search contacts: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppGetChatTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.get_chat"
    override val description: String = "Get information about a WhatsApp chat."
    override val parametersSchema: JSONObject? = JSONObject("{\"chatId\":\"string\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val chatId = arguments["chatId"] as? String ?: return ToolResult.Failed("Chat ID required.", retryable = false)
        return try {
            val chat = client.getChat(chatId)
            if (chat == null) {
                ToolResult.Success(data = mapOf("chat" to null), userMessage = "Chat not found.")
            } else {
                ToolResult.Success(
                    data = mapOf("chat" to chat),
                    userMessage = "Chat: ${chat.name} (${chat.unreadCount} unread)"
                )
            }
        } catch (e: Exception) {
            ToolResult.Failed("Failed to get chat: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppGetRecentMessagesTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.get_recent_messages"
    override val description: String = "Get recent messages from a WhatsApp chat."
    override val parametersSchema: JSONObject? = JSONObject("{\"chatId\":\"string\",\"limit\":\"integer\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val chatId = arguments["chatId"] as? String ?: return ToolResult.Failed("Chat ID required.", retryable = false)
        val limit = (arguments["limit"] as? Number)?.toInt() ?: 10
        return try {
            val messages = client.getRecentMessages(chatId, limit)
            ToolResult.Success(
                data = mapOf("messages" to messages, "count" to messages.size),
                userMessage = "Retrieved ${messages.size} message(s)."
            )
        } catch (e: Exception) {
            ToolResult.Failed("Failed to get messages: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppGetUnreadMessagesTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.get_unread_messages"
    override val description: String = "Get unread WhatsApp messages."
    override val parametersSchema: JSONObject? = JSONObject("{\"limit\":\"integer\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val limit = (arguments["limit"] as? Number)?.toInt() ?: 20
        return try {
            val messages = client.getUnreadMessages(limit)
            ToolResult.Success(
                data = mapOf("messages" to messages, "count" to messages.size),
                userMessage = "You have ${messages.size} unread message(s)."
            )
        } catch (e: Exception) {
            ToolResult.Failed("Failed to get unread messages: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppSearchMessagesTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.search_messages"
    override val description: String = "Search messages in WhatsApp."
    override val parametersSchema: JSONObject? = JSONObject("{\"query\":\"string\",\"chatId\":\"string\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val query = arguments["query"] as? String ?: return ToolResult.Failed("Search query required.", retryable = false)
        val chatId = arguments["chatId"] as? String
        return try {
            val messages = client.searchMessages(query, chatId)
            ToolResult.Success(
                data = mapOf("messages" to messages, "count" to messages.size),
                userMessage = "Found ${messages.size} message(s) matching '$query'."
            )
        } catch (e: Exception) {
            ToolResult.Failed("Failed to search: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppGetMessageTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.get_message"
    override val description: String = "Get a specific WhatsApp message."
    override val parametersSchema: JSONObject? = JSONObject("{\"messageId\":\"string\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val messageId = arguments["messageId"] as? String ?: return ToolResult.Failed("Message ID required.", retryable = false)
        return try {
            val message = client.getMessage(messageId)
            if (message == null) {
                ToolResult.Success(data = mapOf("message" to null), userMessage = "Message not found.")
            } else {
                ToolResult.Success(data = mapOf("message" to message), userMessage = "Message from ${message.senderName}: ${message.text.take(100)}")
            }
        } catch (e: Exception) {
            ToolResult.Failed("Failed to get message: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppSendMessageTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.send_message"
    override val description: String = "Send a text message on WhatsApp."
    override val parametersSchema: JSONObject? = JSONObject("{\"chatId\":\"string\",\"text\":\"string\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val chatId = arguments["chatId"] as? String ?: return ToolResult.Failed("Chat ID required.", retryable = false)
        val text = arguments["text"] as? String ?: return ToolResult.Failed("Message text required.", retryable = false)
        return try {
            val result = client.sendMessage(chatId, text)
            if (result.isSuccess) {
                val msg = result.getOrNull()
                ToolResult.Success(
                    data = mapOf("messageId" to msg?.id, "chatId" to chatId),
                    userMessage = "Message sent successfully."
                )
            } else {
                ToolResult.Failed("Failed to send message.", retryable = true)
            }
        } catch (e: Exception) {
            ToolResult.Failed("Send failed: ${e.message}", retryable = true)
        }
    }
}

class WhatsAppReplyTool(
    private val client: WhatsAppClient
) : JavisTool {
    override val name: String = "whatsapp.reply"
    override val description: String = "Reply to a specific WhatsApp message."
    override val parametersSchema: JSONObject? = JSONObject("{\"chatId\":\"string\",\"messageId\":\"string\",\"text\":\"string\"}")

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val chatId = arguments["chatId"] as? String ?: return ToolResult.Failed("Chat ID required.", retryable = false)
        val messageId = arguments["messageId"] as? String ?: return ToolResult.Failed("Message ID required.", retryable = false)
        val text = arguments["text"] as? String ?: return ToolResult.Failed("Message text required.", retryable = false)
        return try {
            val result = client.reply(chatId, messageId, text)
            if (result.isSuccess) {
                ToolResult.Success(userMessage = "Reply sent successfully.")
            } else {
                ToolResult.Failed("Failed to reply.", retryable = true)
            }
        } catch (e: Exception) {
            ToolResult.Failed("Reply failed: ${e.message}", retryable = true)
        }
    }
}
