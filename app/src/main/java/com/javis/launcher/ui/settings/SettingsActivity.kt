package com.javis.launcher.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import com.javis.launcher.engine.voice.EdgeTts
import com.javis.launcher.engine.voice.VoiceEngine
import com.javis.launcher.models.AIProvider
import com.javis.launcher.ui.voice.VoiceDiagnosticsActivity
import com.javis.launcher.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
        setupAppearanceSection()
        setupPersonalitySection()
        setupVoiceSection()
        setupMemorySection()
        setupRoutineSection()
        setupWhatsAppSection()
        setupAppSelectionSection()
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

    // ─── Appearance (Dark/Light + Wallpaper) ─────────────────────────────
    private fun setupAppearanceSection() {
        val rgMode = findViewById<RadioGroup>(R.id.rg_mode)
        val btnWallpaper = findViewById<Button>(R.id.btn_wallpaper)
        val ivWallpaper = findViewById<ImageView>(R.id.iv_wallpaper)

        val currentMode = ThemeManager.getMode(this)
        val modeCheckedId = if (currentMode == ThemeManager.MODE_LIGHT) R.id.rb_mode_light else R.id.rb_mode_dark
        rgMode.check(modeCheckedId)

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_mode_light) ThemeManager.MODE_LIGHT else ThemeManager.MODE_DARK
            ThemeManager.setMode(this, mode)
            Toast.makeText(this, "Display mode updated. Restart to apply.", Toast.LENGTH_SHORT).show()
            recreate()
        }

        val wallpaperFile = File(filesDir, "javis_wallpaper.jpg")
        if (wallpaperFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(wallpaperFile.absolutePath)
            ivWallpaper.setImageBitmap(bitmap)
        }

        btnWallpaper.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_WALLPAPER)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WALLPAPER && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val ivWallpaper = findViewById<ImageView>(R.id.iv_wallpaper)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()

                        val wallpaperFile = File(filesDir, "javis_wallpaper.jpg")
                        FileOutputStream(wallpaperFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }

                        withContext(Dispatchers.Main) {
                            ivWallpaper.setImageBitmap(bitmap)
                            Toast.makeText(this@SettingsActivity, "Wallpaper updated!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Wallpaper save failed", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val REQUEST_WALLPAPER = 1001
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

    // ─── Voice Engine (V5) ────────────────────────────────────────────────
    private fun setupVoiceSection() {
        val rgTts = findViewById<RadioGroup>(R.id.rg_tts_engine)
        val spinnerVoice = findViewById<Spinner>(R.id.spinner_voice)
        val tvVoiceStatus = findViewById<TextView>(R.id.tv_voice_status)
        val btnTestVoice = findViewById<Button>(R.id.btn_test_voice)

        val prefs = getSharedPreferences("javis_voice_prefs", MODE_PRIVATE)
        val savedEngine = prefs.getString("tts_engine", "system") ?: "system"
        val savedVoice = prefs.getString("edge_voice_id", "en-GB-RyanNeural") ?: "en-GB-RyanNeural"

        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            EdgeTts.VOICES.map { "${it.first} — ${it.second}" })
        spinnerVoice.adapter = voiceAdapter

        val voiceIndex = EdgeTts.VOICES.indexOfFirst { it.first == savedVoice }
        spinnerVoice.setSelection(if (voiceIndex >= 0) voiceIndex else 0)

        val checkedId = when (savedEngine) {
            "edge" -> R.id.rb_tts_edge
            else -> R.id.rb_tts_system
        }
        rgTts.check(checkedId)
        tvVoiceStatus.text = when (savedEngine) {
            "edge" -> "Online TTS (${EdgeTts.getVoiceDisplayName(savedVoice)})"
            else -> "Offline TTS (Android TTS)"
        }

        spinnerVoice.isEnabled = savedEngine == "edge"

        rgTts.setOnCheckedChangeListener { _, checkedId ->
            val engine = when (checkedId) {
                R.id.rb_tts_edge -> "edge"
                else -> "system"
            }
            prefs.edit().putString("tts_engine", engine).apply()
            spinnerVoice.isEnabled = engine == "edge"

            val currentVoice = prefs.getString("edge_voice_id", "en-GB-RyanNeural") ?: "en-GB-RyanNeural"
            tvVoiceStatus.text = when (engine) {
                "edge" -> "Online TTS (${EdgeTts.getVoiceDisplayName(currentVoice)})"
                else -> "Offline TTS (Android TTS)"
            }
            voice?.refreshPersonality()
            Toast.makeText(this, "TTS engine updated.", Toast.LENGTH_SHORT).show()
        }

        spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedVoice = EdgeTts.VOICES[position].first
                prefs.edit().putString("edge_voice_id", selectedVoice).apply()
                val engine = prefs.getString("tts_engine", "system") ?: "system"
                tvVoiceStatus.text = when (engine) {
                    "edge" -> "Online TTS (${EdgeTts.getVoiceDisplayName(selectedVoice)})"
                    else -> "Offline TTS (Android TTS)"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnTestVoice.setOnClickListener {
            val engine = prefs.getString("tts_engine", "system") ?: "system"
            if (engine == "edge") {
                val voiceId = prefs.getString("edge_voice_id", "en-GB-RyanNeural") ?: "en-GB-RyanNeural"
                Toast.makeText(this, "Testing Online TTS (Edge TTS)...", Toast.LENGTH_SHORT).show()
                voice?.speak("Hello, Sir. This is JARVIS online voice. How do you like my British accent?")
            } else {
                Toast.makeText(this, "Testing Offline TTS (System TTS)...", Toast.LENGTH_SHORT).show()
                voice?.speak("Hello, Sir. This is JARVIS offline voice. Fully local, no internet required.")
            }
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

    // ─── App Selection ────────────────────────────────────────────────────
    private fun setupAppSelectionSection() {
        val btnSelectApps = findViewById<Button>(R.id.btn_select_apps)
        val tvSelectedApps = findViewById<TextView>(R.id.tv_selected_apps)

        val prefs = getSharedPreferences("javis_apps", MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()

        btnSelectApps.setOnClickListener {
            val pm = packageManager
            val allApps = pm.getInstalledApplications(0)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { pm.getApplicationLabel(it).toString() to it.packageName }
                .sortedBy { it.first }

            val appNames = allApps.map { it.first }.toTypedArray()
            val selectedIndices = selectedApps.mapNotNull { pkg ->
                allApps.indexOfFirst { it.second == pkg }.takeIf { it >= 0 }
            }.toSet()

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select apps to show on home screen")
                .setMultiChoiceItems(appNames, selectedIndices.toBooleanArray()) { _, index, isChecked ->
                    val pkg = allApps[index].second
                    val current = prefs.getStringSet("selected_packages", mutableSetOf()) ?: mutableSetOf()
                    if (isChecked) {
                        current.add(pkg)
                    } else {
                        current.remove(pkg)
                    }
                    prefs.edit().putStringSet("selected_packages", current).apply()
                    updateSelectedAppsText(tvSelectedApps, current)
                }
                .setPositiveButton("Done") { _, _ ->
                    Toast.makeText(this, "App selection updated", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        updateSelectedAppsText(tvSelectedApps, selectedApps)
    }

    private fun updateSelectedAppsText(tv: TextView, selectedApps: Set<String>) {
        if (selectedApps.isEmpty()) {
            tv.text = "No apps selected. Showing most used apps."
        } else {
            val pm = packageManager
            val names = selectedApps.mapNotNull { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: Exception) { null }
            }
            tv.text = "Selected: ${names.joinToString(", ")}"
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
