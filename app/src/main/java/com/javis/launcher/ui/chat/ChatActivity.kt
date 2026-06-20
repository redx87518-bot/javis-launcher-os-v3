package com.javis.launcher.ui.chat

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.ThinkingEngine
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.execution.ExecutionEngine
import com.javis.launcher.engine.execution.ExecutionResult
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine
    private val voice  get() = JavisApplication.instance.voiceEngine
    private lateinit var ai: AIEngine
    private lateinit var execution: ExecutionEngine
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var rvChat:    RecyclerView
    private lateinit var etInput:   EditText
    private lateinit var btnSend:   ImageButton
    private lateinit var tvProvider: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rvChat    = findViewById(R.id.rv_chat)
        etInput   = findViewById(R.id.et_input)
        btnSend   = findViewById(R.id.btn_send)
        tvProvider = findViewById(R.id.tv_provider)

        ai        = AIEngine(this)
        execution = ExecutionEngine(this)

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = adapter

        // V4: load up to 50 recent messages for context
        lifecycleScope.launch {
            val history = memory.getRecentHistory(50)
            history.forEach { msg ->
                messages.add(ChatMessage(msg.content, msg.role == "user"))
            }
            adapter.notifyDataSetChanged()
            if (messages.isNotEmpty()) rvChat.scrollToPosition(messages.size - 1)
        }

        val provider = ai.getActiveProvider()
        tvProvider.text = if (provider != null) "AI: ${provider.name}" else "AI: Not configured — go to Settings"

        btnSend.setOnClickListener { sendMessage() }
        etInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendMessage(); true
            } else false
        }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return
        etInput.text.clear()

        addMessage(text, isUser = true)

        lifecycleScope.launch {
            memory.saveMessage("user", text)
            ContextEngine.inferAndUpdateGoal(text)

            val thought = ThinkingEngine.think(text)
            ContextEngine.updateAction(thought.intentResult.action)

            val response: String = when (thought.category) {
                ThinkingEngine.Category.LOCAL_ACTION -> {
                    val result = execution.execute(thought.intentResult)
                    when (result) {
                        is ExecutionResult.Success           -> result.message
                        is ExecutionResult.NeedsConfirmation -> result.message
                        is ExecutionResult.Failure           ->
                            if (result.message == "CHAT") {
                                val history = memory.getRecentHistory(50)
                                ai.chat(thought.enrichedPrompt ?: text, history)
                            } else result.message
                    }
                }
                ThinkingEngine.Category.MEMORY_QUERY -> {
                    val result = execution.execute(thought.intentResult)
                    (result as? ExecutionResult.Success)?.message
                        ?: (result as? ExecutionResult.Failure)?.message
                        ?: "I don't have that stored."
                }
                ThinkingEngine.Category.AI_CONVERSATION,
                ThinkingEngine.Category.HYBRID -> {
                    val history = memory.getRecentHistory(50)
                    ai.chat(thought.enrichedPrompt ?: text, history)
                }
            }

            memory.saveMessage("assistant", response)
            addMessage(response, isUser = false)
            voice.speak(response)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(ChatMessage(text, isUser))
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }
}
