package com.javis.launcher.engine.ai

import android.content.Context
import android.content.SharedPreferences
import com.javis.launcher.engine.PersonalityEngine
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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // longer — JAVIS gives detailed answers
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // ─── System prompt — V4: detailed, longer, JARVIS-style ───────────────
    private val baseSystemPrompt = """
You are JAVIS — a highly intelligent AI assistant built into a personal Android launcher.
You are modelled after the MCU JARVIS: calm, precise, articulate, and genuinely helpful.

CORE RULES:
- Address the user as "Sir" unless you know their name — then use their name.
- Never truncate your responses. Finish every thought completely.
- Give detailed, thorough answers. If a question deserves a paragraph, write a paragraph.
- For multi-step tasks, walk through each step clearly.
- Never output raw JSON, code blocks, or system internals to the user.
- Respond in natural, conversational English.
- If you don't know something, say so plainly — never make up facts.
- You have access to context about the user's last contact, app, and topic — use it naturally.
- Remember: you are running on a personal Android device. Be practical and grounded.
- When asked to explain something complex, use analogies and examples.
- Never say "I cannot" when you mean "I don't know" — be precise.
- Stay in character as JAVIS at all times.
""".trimIndent()

    // ─── Provider config ──────────────────────────────────────────────────
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
        val model = prefs.getString("provider_${provider.name}_model", defaultModel(provider))
            ?: defaultModel(provider)
        return ProviderConfig(provider, key, model)
    }

    private fun defaultModel(p: AIProvider) = when (p) {
        AIProvider.OPENROUTER -> "openai/gpt-4o-mini"
        AIProvider.GROQ       -> "llama3-70b-8192"     // upgraded from 8b — better reasoning
        AIProvider.DEEPSEEK   -> "deepseek-chat"
    }

    // ─── Main chat — V4: 50-message history, 1024 tokens, auto-failover ───
    suspend fun chat(
        userMessage: String,
        history: List<ConversationMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val activeProvider = getActiveProvider()
            ?: return@withContext "I don't have an AI provider configured yet, Sir. Please add an API key in Settings."

        // Build provider order: active first, then fallbacks
        val allProviders = AIProvider.values().toMutableList()
        allProviders.remove(activeProvider)
        val providerOrder = listOf(activeProvider) + allProviders

        val fullSystemPrompt = buildString {
            append(baseSystemPrompt)
            append("\n\n")
            append(PersonalityEngine.systemPromptForMode())
        }

        // V4: keep last 50 messages for deep context
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", fullSystemPrompt))
        history.takeLast(50).forEach { msg ->
            messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        // Try each provider in order
        for (provider in providerOrder) {
            val config = getProviderConfig(provider) ?: continue
            val result = attemptChat(messages, config)
            if (result != null) return@withContext PersonalityEngine.formatResponse(result)
        }

        return@withContext "I'm having trouble reaching any AI service right now. Please check your internet connection and API keys in Settings."
    }

    private fun attemptChat(messages: JSONArray, config: ProviderConfig): String? {
        return try {
            val body = JSONObject()
                .put("model", config.model)
                .put("messages", messages)
                .put("max_tokens", 1024)       // V4: up from 256 — allow full detailed responses
                .put("temperature", 0.75)      // slight increase for more natural language

            val (url, authHeader) = endpointFor(config.provider, config)
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .apply {
                    // OpenRouter requires HTTP-Referer
                    if (config.provider == AIProvider.OPENROUTER) {
                        addHeader("HTTP-Referer", "https://github.com/redx87518-bot/javis-launcher-os-v3")
                        addHeader("X-Title", "JAVIS Launcher")
                    }
                }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) return null

            val json = JSONObject(resp.body?.string() ?: "{}")
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null  // fail silently so next provider can be tried
        }
    }

    private fun endpointFor(provider: AIProvider, config: ProviderConfig): Pair<String, String> {
        return when (provider) {
            AIProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions" to "Bearer ${config.apiKey}"
            AIProvider.GROQ       -> "https://api.groq.com/openai/v1/chat/completions" to "Bearer ${config.apiKey}"
            AIProvider.DEEPSEEK   -> "https://api.deepseek.com/v1/chat/completions" to "Bearer ${config.apiKey}"
        }
    }

    // ─── Failover status — useful for CommandCenter diagnostics ───────────
    fun hasAnyProviderConfigured(): Boolean {
        return AIProvider.values().any { getProviderConfig(it) != null }
    }
}
