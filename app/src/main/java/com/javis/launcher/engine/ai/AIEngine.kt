package com.javis.launcher.engine.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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
        try {
            EncryptedSharedPreferences.create(
                "javis_ai_prefs",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("AIEngine", "EncryptedSharedPreferences failed, falling back to plain", e)
            context.getSharedPreferences("javis_ai_prefs", Context.MODE_PRIVATE)
        }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
- If the user asks you to do something on their device (open an app, set an alarm, call someone), tell them you've noted it and the local system will handle it, or simply describe the action in past tense.
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
        val name = prefs.getString("active_provider", null)
        if (name == null || name == "AUTO") {
            val autoSelected = autoSelectBestProvider()
            if (autoSelected != null) {
                prefs.edit().putString("active_provider", autoSelected.name).apply()
                return autoSelected
            }
            return null
        }
        return try { AIProvider.valueOf(name) } catch (e: Exception) { null }
    }

    fun isAutoMode(): Boolean {
        val name = prefs.getString("active_provider", "AUTO")
        return name == "AUTO" || name == "AUTO_FREE" || name == null
    }

    fun setAutoMode(enabled: Boolean) {
        prefs.edit()
            .putString("active_provider", if (enabled) "AUTO_FREE" else AIProvider.OPENROUTER.name)
            .apply()
    }

    private val freeModels = mapOf(
        AIProvider.OPENROUTER to listOf(
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-4-maverick:free",
            "mistralai/mistral-small-24b-instruct-2501:free"
        ),
        AIProvider.GROQ to listOf(
            "llama-3.1-8b-instant",
            "llama3-8b-8192",
            "gemma2-9b-it"
        ),
        AIProvider.DEEPSEEK to listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    )

    private fun autoSelectBestProvider(): AIProvider? {
        val configured = AIProvider.values().filter { getProviderConfig(it) != null }
        if (configured.isEmpty()) return null

        val freePriority = configured.sortedByDescending { provider ->
            val config = getProviderConfig(provider)
            val model = config?.model ?: defaultModel(provider)
            val isFreeModel = freeModels[provider]?.any { freeModel ->
                model.equals(freeModel, ignoreCase = true)
            } ?: false

            val priorityScore = when (provider) {
                AIProvider.GROQ -> if (isFreeModel) 30 else 20
                AIProvider.DEEPSEEK -> if (isFreeModel) 25 else 15
                AIProvider.OPENROUTER -> if (isFreeModel) 20 else 10
            }
            priorityScore
        }

        return freePriority.firstOrNull()
    }

    fun getProviderConfig(provider: AIProvider): ProviderConfig? {
        val key = prefs.getString("provider_${provider.name}_key", null) ?: return null
        val model = prefs.getString("provider_${provider.name}_model", null)
            ?: if (isAutoMode()) autoSelectFreeModel(provider) else defaultModel(provider)
            ?: defaultModel(provider)
        return ProviderConfig(provider, key, model)
    }

    private fun autoSelectFreeModel(provider: AIProvider): String {
        return freeModels[provider]?.firstOrNull() ?: defaultModel(provider)
    }

    private fun defaultModel(p: AIProvider) = when (p) {
        AIProvider.OPENROUTER -> "google/gemini-2.0-flash-exp:free"
        AIProvider.GROQ       -> "llama-3.1-8b-instant"
        AIProvider.DEEPSEEK   -> "deepseek-chat"
    }

    // ─── Main chat — V4: 50-message history, 1024 tokens, auto-failover ───
    suspend fun chat(
        userMessage: String,
        history: List<ConversationMessage> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val activeProvider = getActiveProvider()
            ?: return@withContext "I don't have an AI provider configured yet, Sir. Please add an API key in Settings."

        val allProviders = AIProvider.values().toMutableList()
        allProviders.remove(activeProvider)
        val providerOrder = listOf(activeProvider) + allProviders

        val fullSystemPrompt = buildString {
            append(baseSystemPrompt)
            append("\n\n")
            append(PersonalityEngine.systemPromptForMode())
        }

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", fullSystemPrompt))
        history.takeLast(50).forEach { msg ->
            messages.put(JSONObject().put("role", msg.role).put("content", msg.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        try {
            for (provider in providerOrder) {
                val config = getProviderConfig(provider) ?: continue
                val result = attemptChatWithRetry(messages, config)
                if (result != null) return@withContext PersonalityEngine.formatResponse(result)
            }
        } catch (e: Exception) {
            Log.e("AIEngine", "Chat failed", e)
        }

        return@withContext "I'm having trouble reaching any AI service right now. Please check your internet connection and API keys in Settings."
    }

    private fun attemptChat(messages: JSONArray, config: ProviderConfig): String? {
        return try {
            val body = JSONObject()
                .put("model", config.model)
                .put("messages", messages)
                .put("max_tokens", 1024)
                .put("temperature", 0.75)

            val (url, authHeader) = endpointFor(config.provider, config)
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .apply {
                    if (config.provider == AIProvider.OPENROUTER) {
                        addHeader("HTTP-Referer", "https://github.com/redx87518-bot/javis-launcher-os-v3")
                        addHeader("X-Title", "JAVIS Launcher")
                    }
                }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val start = System.currentTimeMillis()
            val resp = http.newCall(req).execute()
            val latency = System.currentTimeMillis() - start
            Log.d("AIEngine", "${config.provider} responded in ${latency}ms")

            if (!resp.isSuccessful) {
                Log.w("AIEngine", "${config.provider} HTTP ${resp.code}: ${resp.message}")
                return null
            }

            val json = JSONObject(resp.body?.string() ?: "{}")
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("AIEngine", "Provider ${config.provider} failed", e)
            null
        }
    }

    private suspend fun attemptChatWithRetry(
        messages: JSONArray,
        config: ProviderConfig,
        maxRetries: Int = 2
    ): String? {
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                val delayMs = (1000L * attempt).coerceAtMost(5000L)
                kotlinx.coroutines.delay(delayMs)
                Log.d("AIEngine", "Retry $attempt for ${config.provider} after ${delayMs}ms")
            }
            val result = attemptChat(messages, config)
            if (result != null) return result
        }
        return null
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
