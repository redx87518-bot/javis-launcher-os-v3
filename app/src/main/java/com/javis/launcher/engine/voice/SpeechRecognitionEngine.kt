package com.javis.launcher.engine.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Uses Android SpeechRecognizer API directly — NO Google popup/UI.
 * Provides continuous listening with callbacks.
 */
class SpeechRecognitionEngine(private val context: Context) {

    interface RecognitionCallback {
        fun onListeningStarted()
        fun onPartialResult(partial: String)
        fun onResult(text: String)
        fun onError(errorCode: Int, errorMessage: String)
        fun onSilence()
    }

    private var recognizer: SpeechRecognizer? = null
    private var callback: RecognitionCallback? = null
    private var isListening = false
    private var shouldContinue = false
    private var restartCount = 0
    private var lastRestartTime = 0L

    fun setCallback(cb: RecognitionCallback) {
        callback = cb
    }

    fun startListening(continuous: Boolean = true) {
        shouldContinue = continuous
        initRecognizer()
        beginListening()
    }

    fun stopListening() {
        shouldContinue = false
        isListening = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    private fun initRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                callback?.onListeningStarted()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = results?.firstOrNull() ?: return
                callback?.onPartialResult(partial)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                restartCount = 0
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    callback?.onResult(text)
                } else {
                    callback?.onSilence()
                }
            }

            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    else -> "Recognition error ($error)"
                }
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    callback?.onSilence()
                    if (shouldContinue &&
                        restartCount < 5 &&
                        System.currentTimeMillis() - lastRestartTime > 2000
                    ) {
                        restartCount++
                        lastRestartTime = System.currentTimeMillis()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (shouldContinue) beginListening()
                        }, 500)
                    } else if (shouldContinue) {
                        callback?.onError(error, "Too many restarts")
                    }
                } else {
                    callback?.onError(error, msg)
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun beginListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            callback?.onError(-1, "Failed to start recognizer: ${e.message}")
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    fun isListening() = isListening

    fun pauseListening() {
        recognizer?.stopListening()
        isListening = false
    }

    fun resumeListening() {
        if (shouldContinue && !isListening) {
            beginListening()
        }
    }

    fun destroy() {
        shouldContinue = false
        recognizer?.destroy()
        recognizer = null
    }
}
