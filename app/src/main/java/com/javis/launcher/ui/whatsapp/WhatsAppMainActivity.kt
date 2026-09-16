package com.javis.launcher.ui.whatsapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.R
import com.javis.launcher.util.ThemeManager

class WhatsAppMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whatsapp_main)

        val tvStatus = findViewById<TextView>(R.id.tv_whatsapp_status)
        val tvPhone = findViewById<TextView>(R.id.tv_phone_number)
        val tvSession = findViewById<TextView>(R.id.tv_session_status)
        val tvConnection = findViewById<TextView>(R.id.tv_connection_detail)
        val btnLink = findViewById<android.widget.Button>(R.id.btn_whatsapp_link)
        val btnSettings = findViewById<android.widget.Button>(R.id.btn_whatsapp_settings)

        val prefs = getSharedPreferences("javis_whatsapp", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        val phone = prefs.getString("linked_phone", null)
        val isConnected = prefs.getBoolean("connected", false)

        tvStatus.text = if (isConnected) "Connected" else "Disconnected"
        tvStatus.setTextColor(if (isConnected) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.holo_red_dark))
        tvPhone.text = phone ?: "Not linked"
        tvSession.text = if (isConnected) "Active session" else "No active session"
        tvConnection.text = if (isConnected) "Healthy" else "Not connected"

        btnLink?.setOnClickListener {
            try {
                startActivity(Intent(this, com.javis.launcher.ui.whatsapp.link.WhatsAppLinkActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open WhatsApp linking", Toast.LENGTH_SHORT).show()
            }
        }
        btnSettings?.setOnClickListener {
            try {
                startActivity(Intent(this, WhatsAppSettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applySavedTheme() {
        val theme = ThemeManager.getTheme(this)
        val styleName = ThemeManager.themeStyleName(theme)
        val resId = resources.getIdentifier(styleName, "style", packageName)
        if (resId != 0) setTheme(resId)
    }
}