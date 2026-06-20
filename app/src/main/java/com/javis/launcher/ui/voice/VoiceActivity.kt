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
import com.javis.launcher.engine.ThinkingEngine
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.execution.ExecutionEngine
import com.javis.launcher.engine.execution.ExecutionResult
import com.javis.launcher.engine.voice.SpeechRecognitionEngine
import com.javis.launcher.models.VoiceState
import kotlinx.coroutines.launch

class VoiceActivity : AppCompatActivity() {

    private lateinit var recognition: SpeechRecognitionEngine
    private lateinit var execution: ExecutionEngine
    private lateinit var ai: AIEngine
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

    // ─── Speech Callbacks ─────────────────────────────────────────────────
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
                    // Only restart if we're not in the middle of processing
                    if (state == VoiceState.LISTENING || state == VoiceState.IDLE) {
                        handler.postDelayed({ if (!isFinishing) startListening() }, 300)
                    }
                }
            }
        })
    }

    // ─── V4 ThinkingEngine pipeline ───────────────────────────────────────
    private fun processInput(input: String) {
        lifecycleScope.launch {
            memory.saveMessage("user", input)
            ContextEngine.inferAndUpdateGoal(input)

            val thought = ThinkingEngine.think(input)
            ContextEngine.updateAction(thought.intentResult.action)

            when (thought.category) {
                ThinkingEngine.Category.LOCAL_ACTION -> {
                    setState(VoiceState.EXECUTING)
                    val result = execution.execute(thought.intentResult)
                    when (result) {
                        is ExecutionResult.Success -> {
                            memory.saveMessage("assistant", result.message)
                            respond(result.message)
                        }
                        is ExecutionResult.NeedsConfirmation -> {
                            memory.saveMessage("assistant", result.message)
                            respond(result.message, restartSoon = true)
                        }
                        is ExecutionResult.Failure -> {
                            if (result.message == "CHAT") {
                                // Wasn't really local — escalate to AI
                                sendToAI(thought.enrichedPrompt ?: input)
                            } else {
                                memory.saveMessage("assistant", result.message)
                                respond(result.message)
                            }
                        }
                    }
                }

                ThinkingEngine.Category.MEMORY_QUERY -> {
                    val result = execution.execute(thought.intentResult)
                    val msg = (result as? ExecutionResult.Success)?.message
                        ?: (result as? ExecutionResult.Failure)?.message
                        ?: "I don't have that stored."
                    memory.saveMessage("assistant", msg)
                    respond(msg)
                }

                ThinkingEngine.Category.AI_CONVERSATION,
                ThinkingEngine.Category.HYBRID -> {
                    sendToAI(thought.enrichedPrompt ?: input)
                }
            }
        }
    }

    private suspend fun sendToAI(prompt: String) {
        val history  = memory.getRecentHistory(50)
        val response = ai.chat(prompt, history)
        memory.saveMessage("assistant", response)
        memory.logCommand("AI Chat", prompt.take(50), "✓ Responded")
        respond(response)
    }

    private fun respond(text: String, restartSoon: Boolean = false) {
        runOnUiThread {
            tvResponse.text = text
            setState(VoiceState.SPEAKING)
            voice.speak(text) {
                runOnUiThread {
                    setState(VoiceState.COMPLETED)
                    handler.postDelayed({
                        if (!isFinishing) startListening()
                    }, if (restartSoon) 500L else 800L)
                }
            }
        }
    }

    // ─── State machine ────────────────────────────────────────────────────
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
        voice.stopSpeaking()
    }
}
