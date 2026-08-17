package com.javis.launcher.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.PersonalityEngine
import com.javis.launcher.engine.RoutineLearningEngine
import com.javis.launcher.engine.WhatsAppEngine
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.engine.voice.VoiceEngine
import com.javis.launcher.models.AIProvider
import com.javis.launcher.ui.voice.VoiceDiagnosticsActivity
import com.javis.launcher.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine!!
    private val voice get() = JavisApplication.instance.voiceEngine!!
    private lateinit var ai: AIEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySavedTheme()
        setContentView(R.layout.activity_settings)
        ai = AIEngine(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        setupProviderSection()
        setupThemeSection()
        setupPersonalitySection()
        setupVoiceSection()
        setupMemorySection()
        setupRoutineSection()
        setupWhatsAppSection()
        setupDiagnosticsSection()
    }

    private fun applySavedTheme() {
        val theme = ThemeManager.getTheme(this)
        val styleName = ThemeManager.themeStyleName(theme)
        val resId = resources.getIdentifier(styleName, "style", packageName)
        if (resId != 0) setTheme(resId)
    }

    // ─── AI Provider ──────────────────────────────────────────────────────
    private fun setupProviderSection() {
        val rgProvider = findViewById<RadioGroup>(R.id.rg_provider)
        val etApiKey    = findViewById<EditText>(R.id.et_api_key)
        val etModel     = findViewById<EditText>(R.id.et_model)
        val btnSave     = findViewById<Button>(R.id.btn_save_provider)
        val tvStatus    = findViewById<TextView>(R.id.tv_provider_status)

        val isAuto = ai.isAutoMode()
        val activeProvider = ai.getActiveProvider()

        rgProvider.setOnCheckedChangeListener(null)
        rgProvider.check(
            when {
                isAuto -> R.id.rb_provider_auto
                activeProvider == AIProvider.OPENROUTER -> R.id.rb_provider_openrouter
                activeProvider == AIProvider.GROQ -> R.id.rb_provider_groq
                activeProvider == AIProvider.DEEPSEEK -> R.id.rb_provider_deepseek
                else -> R.id.rb_provider_auto
            }
        )

        updateProviderFields(activeProvider)

        rgProvider.setOnCheckedChangeListener { _, checkedId ->
            val selectedProvider = when (checkedId) {
                R.id.rb_provider_openrouter -> AIProvider.OPENROUTER
                R.id.rb_provider_groq -> AIProvider.GROQ
                R.id.rb_provider_deepseek -> AIProvider.DEEPSEEK
                else -> null
            }

            if (selectedProvider == null) {
                ai.setAutoMode(true)
                tvStatus.text = "Auto mode: will use best available provider"
                etApiKey.isEnabled = true
                etModel.isEnabled = true
            } else {
                ai.setAutoMode(false)
                val config = ai.getProviderConfig(selectedProvider)
                updateProviderFields(selectedProvider)
                tvStatus.text = if (config != null) {
                    "Active: ${selectedProvider.name} (configured)"
                } else {
                    "Active: ${selectedProvider.name} (add API key below)"
                }
            }
        }

        btnSave.setOnClickListener {
            val selectedProvider = when (rgProvider.checkedRadioButtonId) {
                R.id.rb_provider_openrouter -> AIProvider.OPENROUTER
                R.id.rb_provider_groq -> AIProvider.GROQ
                R.id.rb_provider_deepseek -> AIProvider.DEEPSEEK
                else -> null
            }

            val key = etApiKey.text.toString().trim()
            val model = etModel.text.toString().trim()

            if (selectedProvider == null) {
                ai.setAutoMode(true)
                tvStatus.text = "Auto mode enabled"
                Toast.makeText(this, "Auto mode enabled", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (key.isBlank()) {
                Toast.makeText(this, "Enter your API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ai.saveProvider(selectedProvider, key, model.ifBlank { defaultModel(selectedProvider) })
            ai.setAutoMode(false)
            tvStatus.text = "Active: ${selectedProvider.name}"
            Toast.makeText(this, "${selectedProvider.name} saved successfully ✓", Toast.LENGTH_SHORT).show()
            etApiKey.text.clear()
            etApiKey.hint = "API key saved ✓"
        }
    }

    private fun updateProviderFields(provider: AIProvider?) {
        val etApiKey = findViewById<EditText>(R.id.et_api_key)
        val etModel = findViewById<EditText>(R.id.et_model)

        if (provider != null) {
            val config = ai.getProviderConfig(provider)
            etModel.setText(config?.model ?: defaultModel(provider))
            etApiKey.hint = if (config != null) "API key saved ✓" else "API Key"
        } else {
            etModel.setText("")
            etApiKey.hint = "API Key"
        }
    }

    // ─── Theme (V4) ──────────────────────────────────────────────────────
    private fun setupThemeSection() {
        val rgTheme = findViewById<RadioGroup>(R.id.rg_theme)
        val current = ThemeManager.getTheme(this)
        val checkedId = when (current) {
            ThemeManager.THEME_BLUE -> R.id.rb_theme_blue
            ThemeManager.THEME_GREEN -> R.id.rb_theme_green
            ThemeManager.THEME_PURPLE -> R.id.rb_theme_purple
            ThemeManager.THEME_ORANGE -> R.id.rb_theme_orange
            else -> R.id.rb_theme_red
        }
        rgTheme.check(checkedId)

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.rb_theme_blue -> ThemeManager.THEME_BLUE
                R.id.rb_theme_green -> ThemeManager.THEME_GREEN
                R.id.rb_theme_purple -> ThemeManager.THEME_PURPLE
                R.id.rb_theme_orange -> ThemeManager.THEME_ORANGE
                else -> ThemeManager.THEME_RED
            }
            ThemeManager.setTheme(this, theme)
            Toast.makeText(this, "Theme updated. Restart JAVIS to apply fully.", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

    // ─── Personality Mode (V4) ────────────────────────────────────────────
    private fun setupPersonalitySection() {
        val rgPersonality = findViewById<RadioGroup>(R.id.rg_personality)
        val tvPersonality = findViewById<TextView>(R.id.tv_personality_status)

        val currentId = when (PersonalityEngine.currentMode) {
            PersonalityEngine.Mode.JARVIS        -> R.id.rb_jarvis
            PersonalityEngine.Mode.PROFESSIONAL  -> R.id.rb_professional
            PersonalityEngine.Mode.FRIENDLY      -> R.id.rb_friendly
        }
        rgPersonality.check(currentId)
        tvPersonality.text = "Current: ${PersonalityEngine.modeName()}"

        rgPersonality.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.rb_professional -> PersonalityEngine.Mode.PROFESSIONAL
                R.id.rb_friendly     -> PersonalityEngine.Mode.FRIENDLY
                else                 -> PersonalityEngine.Mode.JARVIS
            }
            PersonalityEngine.currentMode = newMode
            voice?.refreshPersonality()
            tvPersonality.text = "Current: ${PersonalityEngine.modeName()}"
            Toast.makeText(this, "${PersonalityEngine.modeName()} mode activated.", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Voice & Eleven Labs (V4) ────────────────────────────────────────
    private fun setupVoiceSection() {
        val rgTts = findViewById<RadioGroup>(R.id.rg_tts_engine)
        val etElevenKey = findViewById<EditText>(R.id.et_eleven_api_key)
        val etElevenVoice = findViewById<EditText>(R.id.et_eleven_voice_id)
        val btnSaveVoice = findViewById<Button>(R.id.btn_save_voice)
        val tvVoiceStatus = findViewById<TextView>(R.id.tv_voice_status)

        val prefs = getSharedPreferences("javis_voice_prefs", MODE_PRIVATE)
        val savedEngine = prefs.getString("tts_engine", "system")
        val savedKey = prefs.getString("eleven_api_key", "")
        val savedVoice = prefs.getString("eleven_voice_id", "21m00Tcm4TlvDq8ikWAM")

        etElevenKey.setText(savedKey)
        etElevenVoice.setText(savedVoice)

        val checkedId = when (savedEngine) {
            "eleven" -> R.id.rb_tts_eleven
            else -> R.id.rb_tts_system
        }
        rgTts.check(checkedId)
        tvVoiceStatus.text = when (savedEngine) {
            "eleven" -> "Eleven Labs Active"
            else -> "System TTS Active"
        }

        rgTts.setOnCheckedChangeListener { _, checkedId ->
            val engine = if (checkedId == R.id.rb_tts_eleven) "eleven" else "system"
            prefs.edit().putString("tts_engine", engine).apply()
            tvVoiceStatus.text = if (engine == "eleven") "Eleven Labs Active" else "System TTS Active"
            voice?.refreshPersonality()
            Toast.makeText(this, "TTS engine updated.", Toast.LENGTH_SHORT).show()
        }

        btnSaveVoice.setOnClickListener {
            val key = etElevenKey.text.toString().trim()
            val voiceId = etElevenVoice.text.toString().trim().ifBlank { "21m00Tcm4TlvDq8ikWAM" }
            prefs.edit()
                .putString("eleven_api_key", key)
                .putString("eleven_voice_id", voiceId)
                .apply()
            Toast.makeText(this, "Eleven Labs settings saved ✓", Toast.LENGTH_SHORT).show()
            voice?.refreshPersonality()
        }
    }

    // ─── Identity / Memory ────────────────────────────────────────────────
    private fun setupMemorySection() {
        val etName       = findViewById<EditText>(R.id.et_user_name)
        val btnSaveName  = findViewById<Button>(R.id.btn_save_name)
        val tvCurrentName = findViewById<TextView>(R.id.tv_current_name)
        val btnClear     = findViewById<Button>(R.id.btn_clear_memory)

        lifecycleScope.launch {
            val name = memory.getUserName()
            tvCurrentName.text = if (name != null) "Current: $name" else "Not set"
        }

        btnSaveName.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isBlank()) return@setOnClickListener
            memory.setUserName(name)
            tvCurrentName.text = "Current: $name"
            etName.text.clear()
            Toast.makeText(this, "Name saved: $name", Toast.LENGTH_SHORT).show()
        }

        btnClear.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear All Memory")
                .setMessage("This will erase all stored memories and conversation history. Continue?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val all = memory.recallAll()
                        all.forEach { m -> memory.forget(m.key) }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "Memory cleared.", Toast.LENGTH_SHORT).show()
                            tvCurrentName.text = "Not set"
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ─── Routine Learning Insights (V4) ───────────────────────────────────
    private fun setupRoutineSection() {
        val tvInsights = findViewById<TextView>(R.id.tv_routine_insights)
        lifecycleScope.launch {
            val apps     = memory.getTopApps(3)
            val contacts = memory.getTopContacts(3)
            tvInsights.text = RoutineLearningEngine.getInsightText(apps, contacts)
        }
    }

    // ─── WhatsApp Integration (V5) ─────────────────────────────────────────
    private fun setupWhatsAppSection() {
        val switchEnabled = findViewById<Switch>(R.id.switch_whatsapp)
        val tvWhatsAppStatus = findViewById<TextView>(R.id.tv_whatsapp_status)
        val btnOpenNotificationSettings = findViewById<Button>(R.id.btn_open_notification_settings)

        val prefs = getSharedPreferences("javis_whatsapp_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        switchEnabled.isChecked = isEnabled
        WhatsAppEngine.setEnabled(isEnabled)
        tvWhatsAppStatus.text = if (isEnabled) "Enabled" else "Disabled"

        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            WhatsAppEngine.setEnabled(isChecked)
            prefs.edit().putBoolean("enabled", isChecked).apply()
            tvWhatsAppStatus.text = if (isChecked) "Enabled" else "Disabled"
            Toast.makeText(this, "WhatsApp integration ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        btnOpenNotificationSettings.setOnClickListener {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS")
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "Please enable notification access in Settings > Apps > Special Access", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── Diagnostics ──────────────────────────────────────────────────────
    private fun setupDiagnosticsSection() {
        findViewById<Button>(R.id.btn_voice_diagnostics).setOnClickListener {
            startActivity(Intent(this, VoiceDiagnosticsActivity::class.java))
        }
    }

    private fun defaultModel(p: AIProvider) = when (p) {
        AIProvider.OPENROUTER -> "openai/gpt-4o-mini"
        AIProvider.GROQ       -> "llama3-70b-8192"
        AIProvider.DEEPSEEK   -> "deepseek-chat"
    }
}
