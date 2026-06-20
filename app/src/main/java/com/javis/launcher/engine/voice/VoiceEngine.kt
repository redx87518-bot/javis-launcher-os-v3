package com.javis.launcher.engine.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.javis.launcher.engine.PersonalityEngine
import java.util.Locale
import java.util.UUID

/**
 * V4 VoiceEngine — Text-to-Speech with personality modes.
 *
 * Provider priority: Kokoro TTS (if installed) → higher-quality Android voice → standard Android TTS
 *
 * All TTS callbacks are posted to the main thread — safe to update UI directly.
 */
class VoiceEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val mainThread = Handler(Looper.getMainLooper())

    var onSpeakingStart: (() -> Unit)? = null
    var onSpeakingEnd: (() -> Unit)? = null

    init {
        initTTS()
    }

    private fun initTTS() {
        // Try Kokoro TTS engine first (user must install "Kokoro TTS" from Play Store)
        // If not installed, Android falls back to its built-in engine automatically
        tts = TextToSpeech(context, this, "dev.kokoro.tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { t ->
                val result = t.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = true
                    applyPersonalityVoice(t)
                }
            }
        } else {
            // Kokoro not available — fall back to default engine
            tts?.shutdown()
            tts = TextToSpeech(context, object : TextToSpeech.OnInitListener {
                override fun onInit(fallbackStatus: Int) {
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        tts?.let { t ->
                            val r = t.setLanguage(Locale.US)
                            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                                ttsReady = true
                                applyPersonalityVoice(t)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun applyPersonalityVoice(t: TextToSpeech) {
        // Prefer a deep male voice
        val voices = t.voices
        val preferredVoice = voices?.firstOrNull { v ->
            v.locale == Locale.US && v.name.lowercase().let { n ->
                n.contains("male") || n.contains("en-us-x-sfg") ||
                n.contains("en-us-x-tpd") || n.contains("en-us-x-tpc")
            }
        } ?: voices?.firstOrNull { v ->
            v.locale == Locale.US && !v.name.lowercase().contains("female")
        }
        preferredVoice?.let { t.voice = it }

        // Tune pitch + rate to personality
        when (PersonalityEngine.currentMode) {
            PersonalityEngine.Mode.JARVIS -> {
                t.setSpeechRate(0.92f)  // measured, deliberate
                t.setPitch(0.82f)       // deep, calm
            }
            PersonalityEngine.Mode.PROFESSIONAL -> {
                t.setSpeechRate(1.0f)
                t.setPitch(0.88f)
            }
            PersonalityEngine.Mode.FRIENDLY -> {
                t.setSpeechRate(1.05f)
                t.setPitch(0.95f)
            }
        }
    }

    /**
     * Speak [text]. [onDone] always fires on the main thread when done or on error.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) {
            mainThread.post { onDone?.invoke() }
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                mainThread.post { onSpeakingStart?.invoke() }
            }
            override fun onDone(id: String?) {
                mainThread.post {
                    onSpeakingEnd?.invoke()
                    onDone?.invoke()
                }
            }
            override fun onError(id: String?) {
                mainThread.post {
                    onSpeakingEnd?.invoke()
                    onDone?.invoke()
                }
            }
        })
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /** Refresh voice settings when personality changes. */
    fun refreshPersonality() {
        tts?.let { applyPersonalityVoice(it) }
    }

    fun stopSpeaking() {
        tts?.stop()
        mainThread.post { onSpeakingEnd?.invoke() }
    }

    fun isSpeaking() = tts?.isSpeaking == true
    fun isReady() = ttsReady

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        ttsReady = false
    }
}
