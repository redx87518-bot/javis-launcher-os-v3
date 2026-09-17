package com.javis.launcher.ui.whatsapp.link

import android.app.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.util.ThemeManager
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

    private var phase: Int = 0
    private var pairingJob: Job? = null

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

        btnLink?.setOnClickListener { startPairing() }
        btnPair?.setOnClickListener { confirmPairing() }
        btnDisconnect?.setOnClickListener { disconnect() }
        btnClear?.setOnClickListener { clearSession() }

        updatePhase(0)
    }

    private fun startPairing() {
        val phone = etPhoneNumber.text.toString().trim()
        if (!isValidPhoneNumber(phone)) {
            Toast.makeText(this, "Enter a valid phone number (e.g. +234XXXXXXXXXX)", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, "WhatsApp is not installed. Please install WhatsApp first.", Toast.LENGTH_LONG).show()
            return
        }

        phase = 1
        updatePhase(1)
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Opening WhatsApp..."

        scope.launch {
            try {
                if (openWhatsAppLink(phone)) {
                    tvStatus.text = "Check WhatsApp for pairing code"
                    pairingJob = scope.launch {
                        delay(60_000L)
                        if (phase == 1) {
                            withContext(Dispatchers.Main) {
                                tvStatus.text = "Pairing code expired. Please try again."
                                progressBar.visibility = View.GONE
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Could not open WhatsApp. Showing manual instructions."
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
                    tvStatus.text = "Unable to connect. Try manual linking."
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

    private fun openWhatsAppLink(phone: String): Boolean {
        return try {
            val intent = Intent("android.intent.action.VIEW")
            intent.setPackage("com.whatsapp")
            val uri = Uri.parse("https://wa.me/$phone?text=")
            intent.data = uri
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun confirmPairing() {
        phase = 2
        updatePhase(2)
        pairingJob?.cancel()
        Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
    }

    private fun disconnect() {
        phase = 0
        pairingJob?.cancel()
        pairingJob = null
        updatePhase(0)
        Toast.makeText(this, "WhatsApp disconnected.", Toast.LENGTH_SHORT).show()
    }

    private fun clearSession() {
        phase = 0
        pairingJob?.cancel()
        pairingJob = null
        updatePhase(0)
        Toast.makeText(this, "Session cleared.", Toast.LENGTH_SHORT).show()
    }

    private fun updatePhase(p: Int) {
        when (p) {
            0 -> {
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
            1 -> {
                tvTitle.text = "Pair your device"
                tvStatus.text = "Check WhatsApp for the code"
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
            2 -> {
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
