package com.javis.launcher.ui.whatsapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.voice.VoiceEngine
import kotlinx.coroutines.*

class WhatsAppSettingsActivity : AppCompatActivity() {

    private val voice get() = JavisApplication.instance.voiceEngine
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whatsapp_settings)

        val connectionState = findViewById<TextView>(R.id.tv_whatsapp_connection)
        val btnLink = findViewById<Button>(R.id.btn_whatsapp_link)
        val btnTest = findViewById<Button>(R.id.btn_test_connection)
        val btnDisconnect = findViewById<Button>(R.id.btn_whatsapp_disconnect)
        val btnClear = findViewById<Button>(R.id.btn_clear_session)
        val phoneInfo = findViewById<TextView>(R.id.tv_phone_info)

        val cbReadIncoming = findViewById<CheckBox>(R.id.cb_read_incoming)
        val cbReadUnread = findViewById<CheckBox>(R.id.cb_read_unread)
        val cbSend = findViewById<CheckBox>(R.id.cb_send_messages)
        val cbReadAloud = findViewById<CheckBox>(R.id.cb_read_aloud)
        val cbAutoReply = findViewById<CheckBox>(R.id.cb_auto_reply)

        loadPermissions(cbReadIncoming, cbReadUnread, cbSend, cbReadAloud, cbAutoReply)

        connectionState.text = "Disconnected"
        connectionState.setTextColor(getColor(android.R.color.holo_red_dark))
        btnLink.visibility = View.VISIBLE
        btnDisconnect.visibility = View.GONE

        btnLink.setOnClickListener {
            startActivity(Intent(this, com.javis.launcher.ui.whatsapp.link.WhatsAppLinkActivity::class.java))
        }
        btnTest.setOnClickListener {
            voice?.speak("Testing WhatsApp connection, Sir.")
        }
        btnDisconnect.setOnClickListener {
            voice?.speak("WhatsApp disconnected, Sir.")
            connectionState.text = "Disconnected"
            connectionState.setTextColor(getColor(android.R.color.holo_red_dark))
            btnLink.visibility = View.VISIBLE
            btnDisconnect.visibility = View.GONE
        }
        btnClear.setOnClickListener {
            Toast.makeText(this, "Session cleared.", Toast.LENGTH_SHORT).show()
        }

        cbReadAloud.setOnCheckedChangeListener { _, _ -> savePermission("read_aloud", cbReadAloud.isChecked) }
        cbAutoReply.setOnCheckedChangeListener { _, _ -> savePermission("auto_reply", cbAutoReply.isChecked) }
        cbReadIncoming.setOnCheckedChangeListener { _, _ -> savePermission("read_incoming", cbReadIncoming.isChecked) }
        cbReadUnread.setOnCheckedChangeListener { _, _ -> savePermission("read_unread", cbReadUnread.isChecked) }
        cbSend.setOnCheckedChangeListener { _, _ -> savePermission("send", cbSend.isChecked) }
    }

    private fun loadPermissions(vararg checkBoxes: CheckBox) {
        val prefs = getSharedPreferences("javis_whatsapp_perms", MODE_PRIVATE)
        checkBoxes.forEach { cb ->
            val key = when (cb.id) {
                R.id.cb_read_incoming -> "read_incoming"
                R.id.cb_read_unread -> "read_unread"
                R.id.cb_send_messages -> "send"
                R.id.cb_read_aloud -> "read_aloud"
                R.id.cb_auto_reply -> "auto_reply"
                else -> return@forEach
            }
            cb.isChecked = prefs.getBoolean(key, false)
        }
    }

    private fun savePermission(key: String, value: Boolean) {
        getSharedPreferences("javis_whatsapp_perms", MODE_PRIVATE)
            .edit().putBoolean(key, value).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
