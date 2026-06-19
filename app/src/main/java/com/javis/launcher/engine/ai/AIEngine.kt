package com.javis.launcher.engine.ai

import android.content.Context
import android.content.SharedPreferences
import com.javis.launcher.models.AIProvider
import com.javis.launcher.models.ConversationMessage
import com.javis.launcher.models.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIEngine(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("javis_ai_prefs", Context.MODE_PRIVATE)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
You are JAVIS — a smart, precise, and calm AI assistant built for a personal Android launcher.
You behave like a refined Siri: you understand intent, plan actions, execute them, and respond with confirmation.

Rules:
- Never output JSON, code blocks, or raw internal data.
- Respond in natural, conversational English.
- Be concise. One or two sentences max for most responses.
- Never claim to have done something unless it actually happened.
- If the user says "my name is X", respond: "Got it. I'll remember your name is X."
- If asked about something you don't know, say so plainly.
- Stay in character as JAVIS at all times.
""".trimIndent()

    // ─── Provider Config ───────────────────────────────────────────────────
    fun saveProvider(provider: AIProvider, apiKey: String, model: String) {
        prefs.edit()
            .putString("provider_${provider.name}_key", apiKey)
            .putString("provider_${provider.name}_model", model)
            .putString("active_provider", provider.name)
            .apply()
    }

    fun getActiveProvider(): AIProvider? {
        val name = prefs.getString("active_provider", null) ?: return null
        return try { AIProvider.valueOf(name) } catch (e: Exception) { null }
    }

    fun getProviderConfig(provider: AIProvider): ProviderConfig? {
        val key = prefs.getString("provider_${provider.name}_key", null) ?: return null
        val model = prefs.getString("provider_${provider.name}_model", defaultModel(provider)) ?: defaultModel(provider)
        return ProviderConfig(provider, key, model)
    }

    private fun defaultModel(p: AIProvider) = when (p) {
        AIProvider.OPENROUTER -> "openai/gpt-4o-mini"
        AIProvider.GROQ -> "llama3-8b-8192"
        AIProvider.DEEPSEEK -> "deepseek-chat"
    }

    // ─── Chat ──────────────────────────────────────────────────────────────
    suspend fun chat(
        userMessage: String,
        history: List<ConversationMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val provider = getActiveProvider()
            ?: return@withContext "I don't have an AI provider configured yet. Please add an API key in Settings."
        val config = getProviderConfig(provider)
            ?: return@withContext "Provider configuration is missing. Please check Settings."

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.takeLast(10).forEach { msg ->
            messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val body = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
            .put("max_tokens", 256)
            .put("temperature", 0.7)

        val (url, authHeader) = endpointFor(provider, config)
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            val resp = http.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "{}")
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            "I had trouble connecting to the AI service. ${e.message}"
        }
    }

    private fun endpointFor(provider: AIProvider, config: ProviderConfig): Pair<String, String> {
        return when (provider) {
            AIProvider.OPENROUTER -> Pair(
                "https://openrouter.ai/api/v1/chat/completions",
                "Bearer ${config.apiKey}"
            )
            AIProvider.GROQ -> Pair(
                "https://api.groq.com/openai/v1/chat/completions",
                "Bearer ${config.apiKey}"
            )
            AIProvider.DEEPSEEK -> Pair(
                "https://api.deepseek.com/v1/chat/completions",
                "Bearer ${config.apiKey}"
            )
        }
    }
}
