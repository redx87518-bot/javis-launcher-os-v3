package com.javis.launcher.ui.whatsapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.R
import com.javis.launcher.util.ThemeManager
import com.javis.launcher.engine.whatsapp.repository.WhatsAppRepository

class WhatsAppTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whatsapp_test)

        val tvResult = findViewById<TextView>(R.id.tv_test_result)
        try {
            val repo = WhatsAppRepository(this)

            val status = repo.connectionState.value
            val phone = repo.getStoredPhoneNumber()
            val connected = repo.isConnected()

            val text = buildString {
                appendLine("Connection: ${status.isConnected}")
                appendLine("Pairing: ${status.isPairing}")
                appendLine("Phone: ${phone ?: "none"}")
                appendLine("Connected: $connected")
                appendLine("Error: ${status.errorMessage ?: "none"}")
                appendLine("Messages stored: ${repo.messages.value.size}")
                appendLine("Chats stored: ${repo.chats.value.size}")
            }
            tvResult.text = text
        } catch (e: Exception) {
            tvResult.text = "Error: ${e.message}"
        }
    }

    private fun applySavedTheme() {
        val theme = ThemeManager.getTheme(this)
        val styleName = ThemeManager.themeStyleName(theme)
        val resId = resources.getIdentifier(styleName, "style", packageName)
        if (resId != 0) setTheme(resId)
    }
}