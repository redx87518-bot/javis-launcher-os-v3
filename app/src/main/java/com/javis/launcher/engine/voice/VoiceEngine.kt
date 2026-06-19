package com.javis.launcher.engine.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class VoiceEngine(private val context: Context) : TextToSpeech.OnInitListener {

    enum class TtsProvider { KOKORO_ONLINE, ANDROID_TTS }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
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
                    // Set male-leaning voice if available
                    val voices = t.voices
                    val maleVoice = voices?.firstOrNull { v ->
                        v.locale == Locale.US && v.name.lowercase().let {
                            it.contains("male") || it.contains("en-us-x-sfg") || it.contains("en-us-x-tpd")
                        }
                    }
                    maleVoice?.let { t.voice = it }
                    // Slightly deeper speech rate and pitch
                    t.setSpeechRate(0.95f)
                    t.setPitch(0.85f)
                }
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        onSpeakingStart?.invoke()
        val utteranceId = UUID.randomUUID().toString()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { onSpeakingStart?.invoke() }
            override fun onDone(id: String?) {
                onSpeakingEnd?.invoke()
                onDone?.invoke()
            }
            override fun onError(id: String?) { onSpeakingEnd?.invoke(); onDone?.invoke() }
        })

        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeakingEnd?.invoke()
    }

    fun isSpeaking() = tts?.isSpeaking == true

    fun isReady() = ttsReady

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        ttsReady = false
    }
}
