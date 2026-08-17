package com.javis.launcher.engine

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.javis.launcher.JavisApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WhatsAppEngine {

    const val TAG = "WhatsAppEngine"
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    data class WhatsAppMessage(
        val id: String,
        val contactName: String,
        val phoneNumber: String,
        val message: String,
        val timestamp: Long,
        val isGroup: Boolean = false,
        val unreadCount: Int = 1
    )

    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    val messages: StateFlow<List<WhatsAppMessage>> = _messages.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            _messages.value = emptyList()
        }
    }

    fun isWhatsAppInstalled(context: Context): Boolean {
        return isAppInstalled(context, WHATSAPP_PACKAGE) || isAppInstalled(context, WHATSAPP_BUSINESS_PACKAGE)
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openChat(context: Context, phoneNumber: String, message: String? = null) {
        val packageName = if (isAppInstalled(context, WHATSAPP_PACKAGE)) WHATSAPP_PACKAGE else WHATSAPP_BUSINESS_PACKAGE
        if (!isAppInstalled(context, packageName)) {
            Log.w(TAG, "WhatsApp not installed")
            return
        }

        try {
            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val uri = if (message != null) {
                Uri.parse("https://wa.me/$cleanNumber?text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://wa.me/$cleanNumber")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WhatsApp chat", e)
        }
    }

    fun sendMessage(context: Context, phoneNumber: String, message: String) {
        openChat(context, phoneNumber, message)
    }

    fun getLatestMessages(limit: Int = 10): List<WhatsAppMessage> {
        return _messages.value.take(limit)
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun addMessage(message: WhatsAppMessage) {
        val current = _messages.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == message.id }
        if (existingIndex >= 0) {
            current[existingIndex] = message
        } else {
            current.add(0, message)
        }
        _messages.value = current.take(20)
    }

    fun processNotification(notification: StatusBarNotification) {
        if (!_isEnabled.value) return

        val packageName = notification.packageName
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) return

        try {
            val extras = notification.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getString("android.title") ?: return
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence("android.text")?.toString()
                ?: return
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            val groupSummary = notification.notification.group == "msg"

            if (groupSummary && subText != null) {
                val groupMessages = parseGroupMessages(subText, notification.postTime)
                groupMessages.forEach { addMessage(it) }
                return
            }

            if (title.contains("WhatsApp", ignoreCase = true) || title.contains("Messages", ignoreCase = true)) {
                return
            }

            val phoneNumber = extractPhoneNumber(notification.key, title)
            val message = WhatsAppMessage(
                id = notification.key,
                contactName = title,
                phoneNumber = phoneNumber,
                message = text,
                timestamp = notification.postTime,
                isGroup = title.contains("group", ignoreCase = true) || subText?.contains("group", ignoreCase = true) == true,
                unreadCount = notification.notification.number
            )
            addMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing WhatsApp notification", e)
        }
    }

    private fun parseGroupMessages(subText: String, timestamp: Long): List<WhatsAppMessage> {
        val messages = mutableListOf<WhatsAppMessage>()
        val lines = subText.split("\n")
        var currentContact = ""
        var currentMessage = ""

        for (line in lines) {
            if (line.contains(":")) {
                if (currentContact.isNotBlank() && currentMessage.isNotBlank()) {
                    messages.add(
                        WhatsAppMessage(
                            id = "group_${timestamp}_${messages.size}",
                            contactName = currentContact,
                            phoneNumber = "",
                            message = currentMessage.trim(),
                            timestamp = timestamp,
                            isGroup = true
                        )
                    )
                }
                val parts = line.split(":", limit = 2)
                currentContact = parts[0].trim()
                currentMessage = if (parts.size > 1) parts[1].trim() else ""
            } else {
                currentMessage += "\n$line"
            }
        }

        if (currentContact.isNotBlank() && currentMessage.isNotBlank()) {
            messages.add(
                WhatsAppMessage(
                    id = "group_${timestamp}_${messages.size}",
                    contactName = currentContact,
                    phoneNumber = "",
                    message = currentMessage.trim(),
                    timestamp = timestamp,
                    isGroup = true
                )
            )
        }

        return messages
    }

    private fun extractPhoneNumber(notificationKey: String, contactName: String): String {
        return try {
            val prefs = JavisApplication.instance.getSharedPreferences("javis_whatsapp", Context.MODE_PRIVATE)
            prefs.getString("phone_${contactName.hashCode()}", "") ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun saveContactPhone(contactName: String, phoneNumber: String) {
        try {
            val prefs = JavisApplication.instance.getSharedPreferences("javis_whatsapp", Context.MODE_PRIVATE)
            prefs.edit().putString("phone_${contactName.hashCode()}", phoneNumber).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save contact phone", e)
        }
    }

    fun removeMessage(id: String) {
        val current = _messages.value.toMutableList()
        current.removeAll { it.id == id }
        _messages.value = current
    }
}

class WhatsAppNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(WhatsAppEngine.TAG, "WhatsApp notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        WhatsAppEngine.processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == WhatsAppEngine.WHATSAPP_PACKAGE || sbn.packageName == WhatsAppEngine.WHATSAPP_BUSINESS_PACKAGE) {
            WhatsAppEngine.removeMessage(sbn.key)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(WhatsAppEngine.TAG, "WhatsApp notification listener disconnected")
    }
}
