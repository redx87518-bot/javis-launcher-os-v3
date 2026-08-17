package com.javis.launcher.engine.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class ElevenLabsTts(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null

    fun isReady(): Boolean = true

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        stop()
        val prefs = context.getSharedPreferences("javis_voice_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("eleven_api_key", "") ?: ""
        val voiceId = prefs.getString("eleven_voice_id", "21m00Tcm4TlvDq8ikWAM") ?: "21m00Tcm4TlvDq8ikWAM"

        if (apiKey.isBlank()) {
            Log.w("ElevenLabsTts", "No API key configured")
            onDone?.invoke()
            return
        }

        val url = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"

        val body = org.json.JSONObject()
            .put("text", text)
            .put("model_id", "eleven_multilingual_v2")
            .put("voice_settings", org.json.JSONObject()
                .put("stability", 0.5)
                .put("similarity_boost", 0.75)
            )
            .toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("ElevenLabsTts", "API error: ${response.code} ${response.message}")
                Log.e("ElevenLabsTts", "API error body: ${response.body?.string()}")
                onDone?.invoke()
                return
            }

            val inputStream = response.body?.byteStream() ?: run {
                onDone?.invoke()
                return
            }

            val tempFile = File(context.cacheDir, "eleven_${System.currentTimeMillis()}.mp3")
            tempFile.deleteOnExit()
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    onDone?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    tempFile.delete()
                    onDone?.invoke()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("ElevenLabsTts", "Speech failed", e)
            onDone?.invoke()
        } finally {
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.setOnErrorListener(null)
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun shutdown() {
        stop()
    }
}
