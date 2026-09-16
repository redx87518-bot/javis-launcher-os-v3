package com.javis.launcher.ui.whatsapp.link

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.util.ThemeManager
import com.javis.launcher.engine.voice.VoiceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class WhatsAppLinkActivity : AppCompatActivity() {

    private val voice get() = JavisApplication.instance.voiceEngine
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionState: TextView
    private lateinit var etPhoneNumber: EditText
    private lateinit var btnLink: Button
    private lateinit var tvPairCode: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var btnPair: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnClear: Button
    private lateinit var progressBar: ProgressBar

    private var phase: Int = 0 // 0=enter_phone, 1=show_code, 2=connected

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whatsapp_link)

        tvTitle = findViewById(R.id.tv_wa_title)
        tvStatus = findViewById(R.id.tv_wa_status)
        tvConnectionState = findViewById(R.id.tv_connection_state)
        etPhoneNumber = findViewById(R.id.et_phone_number)
        btnLink = findViewById(R.id.btn_link_whatsapp)
        tvPairCode = findViewById(R.id.tv_pair_code)
        tvInstructions = findViewById(R.id.tv_instructions)
        btnPair = findViewById(R.id.btn_pair)
        btnDisconnect = findViewById(R.id.btn_disconnect)
        btnClear = findViewById(R.id.btn_clear_session)
        progressBar = findViewById(R.id.progress_bar)

        btnLink.setOnClickListener { startPairing() }
        btnPair.setOnClickListener { confirmPairing() }
        btnDisconnect.setOnClickListener { disconnect() }
        btnClear.setOnClickListener { clearSession() }

        updatePhase(0)
    }

    private fun startPairing() {
        val phone = etPhoneNumber.text.toString().trim()
        if (!isValidPhoneNumber(phone)) {
            Toast.makeText(this, "Enter a valid phone number (e.g. +234XXXXXXXXXX)", Toast.LENGTH_SHORT).show()
            return
        }

        phase = 1
        updatePhase(1)
        scope.launch {
            tvStatus.text = "Generating pairing code..."
            try {
                // This will be replaced with actual bridge call
                val pairCode = generatePairCode()
                tvPairCode.text = pairCode
                scope.launch {
                    delay(60_000L)
                    if (phase == 1) {
                        tvStatus.text = "Pairing code expired. Please request a new one."
                    }
                }
            } catch (e: Exception) {
                tvStatus.text = "Unable to generate pairing code. Please try again."
                updatePhase(0)
            }
        }
    }

    private fun confirmPairing() {
        // Will use actual bridge when available
        updatePhase(2)
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
    }

    private fun disconnect() {
        // Will call WhatsAppClient.disconnect() when available
        phase = 0
        updatePhase(0)
        Toast.makeText(this, "WhatsApp disconnected.", Toast.LENGTH_SHORT).show()
    }

    private fun clearSession() {
        // Will clear stored session
        phase = 0
        updatePhase(0)
        Toast.makeText(this, "Session cleared.", Toast.LENGTH_SHORT).show()
    }

    private fun updatePhase(p: Int) {
        when (p) {
            0 -> { // Enter phone
                tvTitle.text = "Link WhatsApp"
                tvStatus.text = "Connect your WhatsApp account"
                etPhoneNumber.visibility = View.VISIBLE
                btnLink.visibility = View.VISIBLE
                tvPairCode.visibility = View.GONE
                tvInstructions.visibility = View.GONE
                btnPair.visibility = View.GONE
                btnDisconnect.visibility = View.GONE
                btnClear.visibility = View.VISIBLE
                tvConnectionState.text = "Disconnected"
                tvConnectionState.setTextColor(getColor(android.R.color.holo_red_dark))
                progressBar.visibility = View.GONE
            }
            1 -> { // Show pairing code
                tvTitle.text = "Pair your device"
                tvStatus.text = "Enter this code on your WhatsApp"
                etPhoneNumber.visibility = View.GONE
                btnLink.visibility = View.GONE
                tvPairCode.visibility = View.VISIBLE
                tvInstructions.visibility = View.VISIBLE
                btnPair.visibility = View.VISIBLE
                btnDisconnect.visibility = View.VISIBLE
                btnClear.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                tvConnectionState.text = "Pairing..."
                tvConnectionState.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
            2 -> { // Connected
                tvTitle.text = "WhatsApp Connected"
                tvStatus.text = "Linked device active"
                etPhoneNumber.visibility = View.GONE
                btnLink.visibility = View.GONE
                tvPairCode.visibility = View.VISIBLE
                tvInstructions.visibility = View.GONE
                btnPair.visibility = View.GONE
                btnDisconnect.visibility = View.VISIBLE
                btnClear.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                tvConnectionState.text = "Connected"
                tvConnectionState.setTextColor(getColor(android.R.color.holo_green_dark))
            }
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        return phone.matches(Regex("""^\+[1-9]\d{6,14}$"""))
    }

    private fun generatePairCode(): String {
        return String.format("%04d-%04d", (0..9999).random(), (0..9999).random())
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun applySavedTheme() {
        val theme = ThemeManager.getTheme(this)
        val styleName = ThemeManager.themeStyleName(theme)
        val resId = resources.getIdentifier(styleName, "style", packageName)
        if (resId != 0) setTheme(resId)
    }
}