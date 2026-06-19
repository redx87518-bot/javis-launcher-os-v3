package com.javis.launcher.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.models.AIProvider
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
        setupMemorySection()
    }

    private fun setupProviderSection() {
        val spinnerProvider = findViewById<Spinner>(R.id.spinner_provider)
        val etApiKey = findViewById<EditText>(R.id.et_api_key)
        val etModel = findViewById<EditText>(R.id.et_model)
        val btnSaveProvider = findViewById<Button>(R.id.btn_save_provider)
        val tvProviderStatus = findViewById<TextView>(R.id.tv_provider_status)

        val providers = listOf("OpenRouter", "Groq", "DeepSeek")
        spinnerProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)

        // Load current
        val activeProvider = ai.getActiveProvider()
        if (activeProvider != null) {
            spinnerProvider.setSelection(AIProvider.values().indexOf(activeProvider))
            val config = ai.getProviderConfig(activeProvider)
            etApiKey.hint = "API key saved"
            etModel.setText(config?.model ?: "")
            tvProviderStatus.text = "Active: ${activeProvider.name}"
        }

        spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val p = AIProvider.values()[position]
                val config = ai.getProviderConfig(p)
                etModel.setText(config?.model ?: defaultModel(p))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSaveProvider.setOnClickListener {
            val provider = AIProvider.values()[spinnerProvider.selectedItemPosition]
            val key = etApiKey.text.toString().trim()
            val model = etModel.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Enter your API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ai.saveProvider(provider, key, model.ifBlank { defaultModel(provider) })
            tvProviderStatus.text = "Active: ${provider.name}"
            Toast.makeText(this, "${provider.name} saved successfully", Toast.LENGTH_SHORT).show()
            etApiKey.text.clear()
        }
    }

    private fun setupMemorySection() {
        val etName = findViewById<EditText>(R.id.et_user_name)
        val btnSaveName = findViewById<Button>(R.id.btn_save_name)
        val tvCurrentName = findViewById<TextView>(R.id.tv_current_name)
        val btnClearMemory = findViewById<Button>(R.id.btn_clear_memory)

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

        btnClearMemory.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear All Memory")
                .setMessage("This will erase all stored memories. Continue?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Clear memories by deleting all
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

    private fun defaultModel(p: AIProvider) = when (p) {
        AIProvider.OPENROUTER -> "openai/gpt-4o-mini"
        AIProvider.GROQ -> "llama3-8b-8192"
        AIProvider.DEEPSEEK -> "deepseek-chat"
    }
}
