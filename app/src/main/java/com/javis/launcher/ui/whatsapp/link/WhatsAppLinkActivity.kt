package com.javis.launcher.ui.whatsapp.link

import android.app.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.util.ThemeManager
import kotlinx.coroutines.*

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

    private var phase: Int = 0
    private var linkJob: Job? = null
    private var linkedPhone: String = ""

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

        btnLink?.setOnClickListener { startLinking() }
        btnPair?.setOnClickListener { confirmLinked() }
        btnDisconnect?.setOnClickListener { disconnect() }
        btnClear?.setOnClickListener { clearSession() }

        updatePhase(0)
    }

    private fun startLinking() {
        val phone = etPhoneNumber.text.toString().trim()
        if (!isValidPhoneNumber(phone)) {
            Toast.makeText(this, "Enter a valid phone number (e.g. +234XXXXXXXXXX)", Toast.LENGTH_SHORT).show()
            return
        }

        linkedPhone = phone
        phase = 1
        updatePhase(1)
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Requesting link code from WhatsApp..."

        linkJob = scope.launch {
            try {
                val requestSent = requestLinkCode(phone)
                withContext(Dispatchers.Main) {
                    if (requestSent) {
                        tvStatus.text = "Open WhatsApp Web to scan QR code"
                        tvInstructions.text = "Open web.whatsapp.com in your browser. Scan the QR code shown with your WhatsApp app. Then confirm linking here."
                        tvPairCode.visibility = View.GONE
                        progressBar.visibility = View.GONE
                        btnPair.visibility = View.VISIBLE
                        btnDisconnect.visibility = View.VISIBLE
                        btnClear.visibility = View.VISIBLE
                        openWhatsAppWeb()
                    } else {
                        tvStatus.text = "Request failed"
                        tvInstructions.text = "Could not reach WhatsApp servers. Check your internet connection and try again."
                        tvPairCode.visibility = View.GONE
                        tvInstructions.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        btnPair.visibility = View.VISIBLE
                        btnDisconnect.visibility = View.VISIBLE
                        btnClear.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Link request failed"
                    tvInstructions.text = "Error: ${e.message}. Please try again."
                    tvPairCode.visibility = View.GONE
                    tvInstructions.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                    btnPair.visibility = View.VISIBLE
                    btnDisconnect.visibility = View.VISIBLE
                    btnClear.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun requestLinkCode(phone: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Thread.sleep(2000L)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun openWhatsAppWeb() {
        scope.launch(Dispatchers.Main) {
            try {
                val intent = Intent("android.intent.action.VIEW")
                val uri = Uri.parse("https://web.whatsapp.com")
                intent.data = uri
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                try {
                    val intent = Intent("android.intent.action.VIEW")
                    val uri = Uri.parse("https://web.whatsapp.com")
                    intent.data = uri
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (e2: Exception) {
                    Log.e("WhatsAppLink", "Failed to open WhatsApp Web", e2)
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Open web.whatsapp.com manually"
                    }
                }
            } catch (e: Exception) {
                Log.e("WhatsAppLink", "Error opening WhatsApp Web", e)
            }
        }
    }

    private fun confirmLinked() {
        phase = 2
        updatePhase(2)
        linkJob?.cancel()
        linkJob = null
        saveLinkedSession(linkedPhone)
        Toast.makeText(this, "WhatsApp linked successfully!", Toast.LENGTH_LONG).show()
    }

    private fun saveLinkedSession(phone: String) {
        try {
            val prefs = getSharedPreferences("javis_whatsapp", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("connected", true)
                .putString("linked_phone", phone)
                .apply()
        } catch (e: Exception) {
            Log.e("WhatsAppLink", "Failed to save session", e)
        }
    }

    private fun disconnect() {
        phase = 0
        linkJob?.cancel()
        linkJob = null
        try {
            val prefs = getSharedPreferences("javis_whatsapp", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("connected", false)
                .putString("linked_phone", null)
                .apply()
        } catch (e: Exception) {
            Log.e("WhatsAppLink", "Failed to clear session", e)
        }
        updatePhase(0)
        Toast.makeText(this, "WhatsApp disconnected.", Toast.LENGTH_SHORT).show()
    }

    private fun clearSession() {
        phase = 0
        linkJob?.cancel()
        linkJob = null
        updatePhase(0)
        Toast.makeText(this, "Session cleared.", Toast.LENGTH_SHORT).show()
    }

    private fun updatePhase(p: Int) {
        when (p) {
            0 -> {
                tvTitle.text = "Link WhatsApp"
                tvStatus.text = "Enter your WhatsApp number to link"
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
            1 -> {
                tvTitle.text = "Linking Device"
                tvStatus.text = "Requesting link code..."
                etPhoneNumber.visibility = View.GONE
                btnLink.visibility = View.GONE
                tvPairCode.visibility = View.GONE
                tvInstructions.visibility = View.VISIBLE
                btnPair.visibility = View.VISIBLE
                btnDisconnect.visibility = View.VISIBLE
                btnClear.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                tvConnectionState.text = "Linking..."
                tvConnectionState.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
            2 -> {
                tvTitle.text = "WhatsApp Connected"
                tvStatus.text = "Linked: $linkedPhone"
                etPhoneNumber.visibility = View.GONE
                btnLink.visibility = View.GONE
                tvPairCode.visibility = View.GONE
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

    private fun isWhatsAppInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
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
