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
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.models.AIProvider
import com.javis.launcher.ui.voice.VoiceDiagnosticsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine
    private lateinit var ai: AIEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ai = AIEngine(this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        setupProviderSection()
        setupPersonalitySection()
        setupMemorySection()
        setupRoutineSection()
        setupDiagnosticsSection()
    }

    // ─── AI Provider ──────────────────────────────────────────────────────
    private fun setupProviderSection() {
        val spinnerProvider = findViewById<Spinner>(R.id.spinner_provider)
        val etApiKey        = findViewById<EditText>(R.id.et_api_key)
        val etModel         = findViewById<EditText>(R.id.et_model)
        val btnSave         = findViewById<Button>(R.id.btn_save_provider)
        val tvStatus        = findViewById<TextView>(R.id.tv_provider_status)

        val providers = listOf("OpenRouter", "Groq", "DeepSeek")
        spinnerProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)

        val activeProvider = ai.getActiveProvider()
        if (activeProvider != null) {
            spinnerProvider.setSelection(AIProvider.values().indexOf(activeProvider))
            val config = ai.getProviderConfig(activeProvider)
            etApiKey.hint = "API key saved ✓"
            etModel.setText(config?.model ?: "")
            tvStatus.text = "Active: ${activeProvider.name}"
        }

        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val p = AIProvider.values()[position]
                val config = ai.getProviderConfig(p)
                etModel.setText(config?.model ?: defaultModel(p))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener {
            val provider = AIProvider.values()[spinnerProvider.selectedItemPosition]
            val key      = etApiKey.text.toString().trim()
            val model    = etModel.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Enter your API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ai.saveProvider(provider, key, model.ifBlank { defaultModel(provider) })
            tvStatus.text = "Active: ${provider.name}"
            Toast.makeText(this, "${provider.name} saved successfully ✓", Toast.LENGTH_SHORT).show()
            etApiKey.text.clear()
            etApiKey.hint = "API key saved ✓"
        }
    }

    // ─── Personality Mode (V4) ────────────────────────────────────────────
    private fun setupPersonalitySection() {
        val rgPersonality = findViewById<RadioGroup>(R.id.rg_personality)
        val tvPersonality = findViewById<TextView>(R.id.tv_personality_status)

        // Set current mode
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
            JavisApplication.instance.voiceEngine.refreshPersonality()
            tvPersonality.text = "Current: ${PersonalityEngine.modeName()}"
            Toast.makeText(this, "${PersonalityEngine.modeName()} mode activated.", Toast.LENGTH_SHORT).show()
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
