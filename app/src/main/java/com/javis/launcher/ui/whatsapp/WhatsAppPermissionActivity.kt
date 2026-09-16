package com.javis.launcher.ui.whatsapp

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.R
import com.javis.launcher.util.ThemeManager

class WhatsAppPermissionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whatsapp_permission)

        val tvTitle = findViewById<TextView>(R.id.tv_permission_title)
        val tvDesc = findViewById<TextView>(R.id.tv_permission_desc)
        val btnGrant = findViewById<Button>(R.id.btn_grant)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)

        tvTitle.text = "WhatsApp Permissions"
        tvDesc.text = "JAVIS needs notification access to read incoming WhatsApp messages. Go to Settings to grant permission."

        btnGrant.setOnClickListener {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS")
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "Please grant notification access in Settings > Apps > Special Access", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnCancel.setOnClickListener { finish() }
    }

    private fun applySavedTheme() {
        val theme = ThemeManager.getTheme(this)
        val styleName = ThemeManager.themeStyleName(theme)
        val resId = resources.getIdentifier(styleName, "style", packageName)
        if (resId != 0) setTheme(resId)
    }
}