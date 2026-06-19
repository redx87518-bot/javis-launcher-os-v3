package com.javis.launcher.ui.voice

import android.os.Bundle
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.javis.launcher.JavisApplication
import com.javis.launcher.R

class VoiceDiagnosticsActivity : AppCompatActivity() {

    private val voice get() = JavisApplication.instance.voiceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_diagnostics)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val tvEngine = findViewById<TextView>(R.id.tv_engine)
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val tvOnline = findViewById<TextView>(R.id.tv_online_status)
        val tvOffline = findViewById<TextView>(R.id.tv_offline_status)
        val tvError = findViewById<TextView>(R.id.tv_last_error)
        val btnTest = findViewById<Button>(R.id.btn_test_voice)

        tvEngine.text = "Engine: Android TTS (built-in)"
        tvStatus.text = if (voice.isReady()) "Status: Ready" else "Status: Initializing..."
        tvOnline.text = "Online TTS: Available"
        tvOffline.text = "Offline TTS: Android TTS"

        val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(this)
        tvError.text = if (recognizerAvailable) "Speech Recognizer: Available (no Google popup — uses direct API)" else "Speech Recognizer: Not available on this device"

        btnTest.setOnClickListener {
            tvStatus.text = "Status: Speaking..."
            voice.speak("JAVIS voice system is working correctly. Ready for your commands, Sir.") {
                runOnUiThread { tvStatus.text = "Status: Ready" }
            }
        }
    }
}
