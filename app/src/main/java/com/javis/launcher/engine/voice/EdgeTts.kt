package com.javis.launcher.engine.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class EdgeTts(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private val mainThread = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        const val TAG = "EdgeTts"

        val VOICES = listOf(
            "en-US-AriaNeural" to "Aria (US Female, Natural)",
            "en-US-GuyNeural" to "Guy (US Male, Natural)",
            "en-US-JennyNeural" to "Jenny (US Female, Friendly)",
            "en-US-DavisNeural" to "Davis (US Male, Calm)",
            "en-US-JaneNeural" to "Jane (US Female, Professional)",
            "en-US-JasonNeural" to "Jason (US Male, Warm)",
            "en-GB-SoniaNeural" to "Sonia (UK Female, Elegant)",
            "en-GB-RyanNeural" to "Ryan (UK Male, Crisp)",
            "en-AU-NatashaNeural" to "Natasha (AU Female, Bright)",
            "en-AU-WilliamNeural" to "William (AU Male, Friendly)",
            "en-IN-NeerjaNeural" to "Neerja (IN Female, Warm)",
            "en-IN-PrabhatNeural" to "Prabhat (IN Male, Calm)"
        )

        fun getVoiceDisplayName(voiceId: String): String {
            return VOICES.find { it.first == voiceId }?.second ?: voiceId
        }
    }

    fun isReady(): Boolean = true

    fun speak(text: String, voiceId: String = "en-US-AriaNeural", onDone: (() -> Unit)? = null) {
        stop()
        executor.execute {
            try {
                val prefs = context.getSharedPreferences("javis_voice_prefs", Context.MODE_PRIVATE)
                val savedVoice = prefs.getString("edge_voice_id", voiceId) ?: voiceId

                val token = fetchAuthToken()
                if (token == null) {
                    Log.w(TAG, "Failed to get Edge TTS auth token")
                    mainThread.post { onDone?.invoke() }
                    return@execute
                }

                val audioPath = requestTtsAudio(token, text, savedVoice)
                if (audioPath == null) {
                    Log.w(TAG, "Failed to get TTS audio")
                    mainThread.post { onDone?.invoke() }
                    return@execute
                }

                playAudio(audioPath, onDone)
            } catch (e: Exception) {
                Log.e(TAG, "Edge TTS failed", e)
                mainThread.post { onDone?.invoke() }
            }
        }
    }

    private fun fetchAuthToken(): String? {
        return try {
            val request = Request.Builder()
                .url("https://edge.microsoft.com/translate/auth")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Auth request failed: ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            JSONObject(body).optString("token", null)
        } catch (e: Exception) {
            Log.e(TAG, "Auth token fetch failed", e)
            null
        }
    }

    private fun requestTtsAudio(token: String, text: String, voiceId: String): String? {
        return try {
            val bodyJson = JSONObject().apply {
                put("text", text)
                put("voice", voiceId)
                put("format", "audio-24khz-48kbitrate-mono-mp3")
            }

            val request = Request.Builder()
                .url("https://edge.microsoft.com/translate/text?category=tts")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "TTS request failed: ${response.code} ${response.message}")
                return null
            }

            val contentType = response.header("Content-Type", "")
            if (contentType.contains("audio") || contentType.contains("octet-stream")) {
                val tempFile = File(context.cacheDir, "edge_tts_${System.currentTimeMillis()}.mp3")
                tempFile.deleteOnExit()
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.absolutePath
            } else {
                val body = response.body?.string()
                Log.w(TAG, "Unexpected TTS response: $body")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS audio request failed", e)
            null
        }
    }

    private fun playAudio(filePath: String, onDone: (() -> Unit)?) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    File(filePath).delete()
                    mainThread.post { onDone?.invoke() }
                }
                setOnErrorListener { _, _, _ ->
                    File(filePath).delete()
                    mainThread.post { onDone?.invoke() }
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback failed", e)
            File(filePath).delete()
            mainThread.post { onDone?.invoke() }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun shutdown() {
        stop()
        executor.shutdownNow()
    }
}
