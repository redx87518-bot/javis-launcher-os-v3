package com.javis.launcher.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.execution.ExecutionEngine
import com.javis.launcher.engine.execution.ExecutionResult
import com.javis.launcher.engine.intent.IntentAnalyzer
import com.javis.launcher.engine.voice.SpeechRecognitionEngine
import com.javis.launcher.models.JavisAction
import com.javis.launcher.models.VoiceState
import kotlinx.coroutines.launch

class VoiceActivity : AppCompatActivity() {

    private lateinit var recognition: SpeechRecognitionEngine
    private lateinit var execution: ExecutionEngine
    private lateinit var ai: AIEngine
    private val voice get() = JavisApplication.instance.voiceEngine
    private val memory get() = JavisApplication.instance.memoryEngine

    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var tvResponse: TextView
    private lateinit var orbView: OrbView
    private lateinit var waveView: VoiceWaveView

    private var state = VoiceState.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var pendingContacts: List<com.javis.launcher.models.Contact> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)

        tvStatus = findViewById(R.id.tv_status)
        tvTranscript = findViewById(R.id.tv_transcript)
        tvResponse = findViewById(R.id.tv_response)
        orbView = findViewById(R.id.orb_view)
        waveView = findViewById(R.id.wave_view)

        recognition = SpeechRecognitionEngine(this)
        execution = ExecutionEngine(this)
        ai = AIEngine(this)

        setupVoiceCallbacks()

        // Tap orb to start/stop
        orbView.setOnClickListener {
            if (state == VoiceState.IDLE || state == VoiceState.COMPLETED) {
                startListening()
            } else if (state == VoiceState.LISTENING) {
                stopListening()
            }
        }

        // Back button
        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }

        if (hasAudioPermission()) startListening()
        else requestAudioPermission()
    }

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
                    if (state == VoiceState.LISTENING) {
                        // Auto-restart in continuous mode
                        handler.postDelayed({ if (state == VoiceState.LISTENING || state == VoiceState.IDLE) startListening() }, 300)
                    }
                }
            }
        })
    }

    private fun processInput(input: String) {
        lifecycleScope.launch {
            memory.saveMessage("user", input)

            val intent = IntentAnalyzer.analyze(input)
            ContextEngine.updateAction(intent.action)

            if (intent.action == JavisAction.CHAT) {
                // Send to AI
                val history = memory.getRecentHistory(10)
                val response = ai.chat(input, history)
                memory.saveMessage("assistant", response)
                respond(response)
            } else {
                val result = execution.execute(intent)
                when (result) {
                    is ExecutionResult.Success -> {
                        memory.saveMessage("assistant", result.message)
                        respond(result.message)
                    }
                    is ExecutionResult.NeedsConfirmation -> {
                        memory.saveMessage("assistant", result.message)
                        respond(result.message, restartListening = true)
                    }
                    is ExecutionResult.Failure -> {
                        if (result.message == "CHAT") {
                            val history = memory.getRecentHistory(10)
                            val response = ai.chat(input, history)
                            memory.saveMessage("assistant", response)
                            respond(response)
                        } else {
                            memory.saveMessage("assistant", result.message)
                            respond(result.message)
                        }
                    }
                }
            }
        }
    }

    private fun respond(text: String, restartListening: Boolean = false) {
        runOnUiThread {
            tvResponse.text = text
            setState(VoiceState.SPEAKING)
            voice.speak(text) {
                runOnUiThread {
                    setState(VoiceState.COMPLETED)
                    handler.postDelayed({
                        if (!isFinishing) startListening()
                    }, 800)
                }
            }
        }
    }

    private fun setState(newState: VoiceState) {
        state = newState
        orbView.setState(newState)
        waveView.setState(newState)
        tvStatus.text = when (newState) {
            VoiceState.IDLE -> "Tap to speak"
            VoiceState.LISTENING -> "Listening..."
            VoiceState.THINKING -> "Thinking..."
            VoiceState.SPEAKING -> "Speaking..."
            VoiceState.EXECUTING -> "Executing..."
            VoiceState.COMPLETED -> "Done"
            VoiceState.ERROR -> "Error"
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
        voice.stopSpeaking()
    }
}
