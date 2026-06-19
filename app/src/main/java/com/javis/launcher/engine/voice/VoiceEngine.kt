package com.javis.launcher.engine.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class VoiceEngine(private val context: Context) : TextToSpeech.OnInitListener {

    enum class TtsProvider { KOKORO_ONLINE, ANDROID_TTS }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val mainThread = Handler(Looper.getMainLooper())

    var onSpeakingStart: (() -> Unit)? = null
    var onSpeakingEnd: (() -> Unit)? = null
    var currentProvider = TtsProvider.ANDROID_TTS

    init {
        initAndroidTTS()
    }

    private fun initAndroidTTS() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { t ->
                val result = t.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsReady = true
                    // Prefer a male-sounding voice if the device has one
                    val voices = t.voices
                    val maleVoice = voices?.firstOrNull { v ->
                        v.locale == Locale.US && v.name.lowercase().let {
                            it.contains("male") || it.contains("en-us-x-sfg") || it.contains("en-us-x-tpd")
                        }
                    }
                    maleVoice?.let { t.voice = it }
                    t.setSpeechRate(0.95f)
                    t.setPitch(0.85f)
                }
            }
        }
    }

    /**
     * Speak [text] aloud. [onDone] is always called on the MAIN thread when speech finishes
     * (or on error). TTS callbacks arrive on a background thread, so we always post back.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) {
            // TTS not ready yet — call done immediately so callers aren't stuck
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
