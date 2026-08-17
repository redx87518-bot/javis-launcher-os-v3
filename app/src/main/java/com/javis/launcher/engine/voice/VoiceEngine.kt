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

class VoiceEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val mainThread = Handler(Looper.getMainLooper())
    private var edgeTts: EdgeTts? = null

    var onSpeakingStart: (() -> Unit)? = null
    var onSpeakingEnd: (() -> Unit)? = null
    var onSpeakingError: ((String) -> Unit)? = null

    init {
        initTTS()
        edgeTts = EdgeTts(context)
    }

    private fun initTTS() {
        val prefs = context.getSharedPreferences("javis_voice_prefs", Context.MODE_PRIVATE)
        val engine = prefs.getString("tts_engine", "system") ?: "system"
        if (engine == "edge") {
            ttsReady = false
            return
        }
        tts = TextToSpeech(context, this)
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
        val voices = t.voices
        if (!voices.isNullOrEmpty()) {
            val prefs = context.getSharedPreferences("javis_voice_prefs", Context.MODE_PRIVATE)
            val savedVoice = prefs.getString("system_voice", null)

            val selectedVoice = if (savedVoice != null) {
                voices.find { it.name == savedVoice }
            } else {
                val preferredVoice = voices.firstOrNull { v ->
                    v.locale == Locale.US && v.name.lowercase().let { n ->
                        n.contains("male") || n.contains("en-us-x-sfg") ||
                            n.contains("en-us-x-tpd") || n.contains("en-us-x-tpc")
                    }
                } ?: voices.firstOrNull { v ->
                    v.locale == Locale.US && !v.name.lowercase().contains("female")
                }
            }
            selectedVoice?.let { t.voice = it }
        }

        when (PersonalityEngine.currentMode) {
            PersonalityEngine.Mode.JARVIS -> {
                t.setSpeechRate(0.92f)
                t.setPitch(0.82f)
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

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val prefs = context.getSharedPreferences("javis_voice_prefs", Context.MODE_PRIVATE)
        val engine = prefs.getString("tts_engine", "system") ?: "system"

        if (engine == "edge") {
            val voiceId = prefs.getString("edge_voice_id", "en-US-AriaNeural") ?: "en-US-AriaNeural"
            edgeTts?.speak(text, voiceId, onDone)
            return
        }

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
                    onSpeakingError?.invoke("TTS utterance error")
                    onDone?.invoke()
                }
            }
        })
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun refreshPersonality() {
        val prefs = context.getSharedPreferences("javis_voice_prefs", Context.MODE_PRIVATE)
        val engine = prefs.getString("tts_engine", "system") ?: "system"
        if (engine == "edge") {
            ttsReady = false
            tts?.shutdown()
            tts = null
            return
        }
        if (tts == null) {
            initTTS()
        } else {
            tts?.let { applyPersonalityVoice(it) }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        edgeTts?.stop()
        mainThread.post { onSpeakingEnd?.invoke() }
    }

    fun isSpeaking() = tts?.isSpeaking == true
    fun isReady(): Boolean {
        val prefs = context.getSharedPreferences("javis_voice_prefs", Context.MODE_PRIVATE)
        val engine = prefs.getString("tts_engine", "system") ?: "system"
        return if (engine == "edge") {
            edgeTts != null
        } else ttsReady
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        ttsReady = false
        edgeTts?.shutdown()
    }
}

