package com.javis.launcher.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.util.ThemeManager
import com.javis.launcher.engine.ai.AIEngine
import android.util.Log
import com.javis.launcher.engine.agent.AgentEngine
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.execution.ExecutionEngine
import com.javis.launcher.engine.voice.SpeechRecognitionEngine
import com.javis.launcher.models.VoiceState
import kotlinx.coroutines.launch

class VoiceActivity : AppCompatActivity() {

    private lateinit var recognition: SpeechRecognitionEngine
    private lateinit var execution: ExecutionEngine
    private lateinit var ai: AIEngine
    private lateinit var agentEngine: AgentEngine
    private val voice  get() = JavisApplication.instance.voiceEngine
    private val memory get() = JavisApplication.instance.memoryEngine

    private lateinit var tvStatus:     TextView
    private lateinit var tvTranscript: TextView
    private lateinit var tvResponse:   TextView
    private lateinit var orbView:      OrbView
    private lateinit var waveView:     VoiceWaveView

    private var state = VoiceState.IDLE
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)

        tvStatus     = findViewById(R.id.tv_status)
        tvTranscript = findViewById(R.id.tv_transcript)
        tvResponse   = findViewById(R.id.tv_response)
        orbView      = findViewById(R.id.orb_view)
        waveView     = findViewById(R.id.wave_view)

        recognition = SpeechRecognitionEngine(this)
        execution   = ExecutionEngine(this)
        ai          = AIEngine(this)
        agentEngine = AgentEngine(this)

        try {
            val whatsappClient = com.javis.launcher.engine.whatsapp.WhatsmeowWhatsAppClient(this)
            agentEngine.registerWhatsAppTools(listOf(
                com.javis.launcher.engine.whatsapp.tools.WhatsAppConnectionTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppLinkTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppDisconnectTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppFindContactTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppGetChatTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppGetRecentMessagesTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppGetUnreadMessagesTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppSearchMessagesTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppGetMessageTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppSendMessageTool(whatsappClient),
                com.javis.launcher.engine.whatsapp.tools.WhatsAppReplyTool(whatsappClient)
            ))
        } catch (e: Exception) {
            Log.e("VoiceActivity", "Failed to register WhatsApp tools", e)
        }

        setupVoiceCallbacks()

        orbView.setOnClickListener {
            when (state) {
                VoiceState.IDLE, VoiceState.COMPLETED -> startListening()
                VoiceState.LISTENING                  -> stopListening()
                else -> { /* busy — ignore tap */ }
            }
        }

        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }

        if (hasAudioPermission()) startListening()
        else requestAudioPermission()
    }

    // ─── Speech Callbacks ─────────────────────────────────
    private fun setupVoiceCallbacks() {
        recognition.setCallback(object : SpeechRecognitionEngine.RecognitionCallback {
            override fun onListeningStarted() {
                runOnUiThread { setState(VoiceState.LISTENING) }
            }

            override fun onPartialResult(partial: String) {
                runOnUiThread { tvTranscript.text = partial }
            }

            override fun onResult(text: String) {
                runOnUiThread {
                    tvTranscript.text = text
                    setState(VoiceState.THINKING)
                    processInput(text)
                }
            }

            override fun onError(errorCode: Int, errorMessage: String) {
                runOnUiThread {
                    tvStatus.text = errorMessage
                    setState(VoiceState.IDLE)
                }
            }

            override fun onSilence() {
                runOnUiThread {
                    if (state == VoiceState.LISTENING || state == VoiceState.IDLE) {
                        handler.postDelayed({ if (!isFinishing) startListening() }, 300)
                    }
                }
            }
        })
    }

    // ─── V5 Agent pipeline ───────────────────────────────
    private fun processInput(input: String) {
        val mem = memory ?: return
        lifecycleScope.launch {
            mem.saveMessage("user", input)
            ContextEngine.inferAndUpdateGoal(input)

            setState(VoiceState.EXECUTING)
            val result = agentEngine.process(input)
            val response = result.response

            mem.saveMessage("assistant", response)
            respond(response)
        }
    }

    private fun respond(text: String, restartSoon: Boolean = false) {
        val v = voice
        runOnUiThread {
            tvResponse.text = text
            if (v == null) {
                setState(VoiceState.COMPLETED)
                return@runOnUiThread
            }
            setState(VoiceState.SPEAKING)
            v.speak(text) {
                runOnUiThread {
                    setState(VoiceState.COMPLETED)
                    handler.postDelayed({
                        if (!isFinishing) startListening()
                    }, if (restartSoon) 500L else 800L)
                }
            }
        }
    }

    // ─── State machine ────────────────────────────────────
    private fun setState(newState: VoiceState) {
        state = newState
        orbView.setState(newState)
        waveView.setState(newState)
        tvStatus.text = when (newState) {
            VoiceState.IDLE       -> "Tap to speak"
            VoiceState.LISTENING  -> "Listening..."
            VoiceState.THINKING   -> "Thinking..."
            VoiceState.SPEAKING   -> "Speaking..."
            VoiceState.EXECUTING  -> "Executing..."
            VoiceState.COMPLETED  -> "Done"
            VoiceState.ERROR      -> "Error"
        }
    }

    private fun startListening() {
        if (!hasAudioPermission()) { requestAudioPermission(); return }
        setState(VoiceState.LISTENING)
        recognition.startListening(continuous = true)
    }

    private fun stopListening() {
        recognition.stopListening()
        setState(VoiceState.IDLE)
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognition.destroy()
        voice?.stopSpeaking()
    }

    private fun applySavedTheme() {
        val theme = ThemeManager.getTheme(this)
        val styleName = ThemeManager.themeStyleName(theme)
        val resId = resources.getIdentifier(styleName, "style", packageName)
        if (resId != 0) setTheme(resId)
    }
}