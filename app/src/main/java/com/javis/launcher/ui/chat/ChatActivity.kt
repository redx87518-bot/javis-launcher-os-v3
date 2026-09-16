package com.javis.launcher.ui.chat

import android.os.Bundle
import android.util.Log
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
import com.javis.launcher.util.ThemeManager
import com.javis.launcher.engine.ThinkingEngine
import com.javis.launcher.engine.ai.AIEngine
import com.javis.launcher.engine.agent.AgentEngine
import com.javis.launcher.engine.agent.AgentResult
import com.javis.launcher.engine.context.ContextEngine
import com.javis.launcher.engine.execution.ExecutionEngine
import com.javis.launcher.engine.execution.ExecutionResult
import com.javis.launcher.engine.whatsapp.WhatsmeowWhatsAppClient
import com.javis.launcher.engine.whatsapp.tools.*
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine
    private val voice  get() = JavisApplication.instance.voiceEngine
    private lateinit var ai: AIEngine
    private lateinit var execution: ExecutionEngine
    private lateinit var agentEngine: AgentEngine
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var rvChat:    RecyclerView
    private lateinit var etInput:   EditText
    private lateinit var btnSend:   ImageButton
    private lateinit var tvProvider: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rvChat    = findViewById(R.id.rv_chat)
        etInput   = findViewById(R.id.et_input)
        btnSend   = findViewById(R.id.btn_send)
        tvProvider = findViewById(R.id.tv_provider)

        ai        = AIEngine(this)
        execution = ExecutionEngine(this)
        agentEngine = AgentEngine(this)

        // Register WhatsApp tools with AgentEngine
        try {
            val whatsappClient = WhatsmeowWhatsAppClient(this)
            agentEngine.registerWhatsAppTools(listOf(
                WhatsAppConnectionTool(whatsappClient),
                WhatsAppLinkTool(whatsappClient),
                WhatsAppDisconnectTool(whatsappClient),
                WhatsAppFindContactTool(whatsappClient),
                WhatsAppGetChatTool(whatsappClient),
                WhatsAppGetRecentMessagesTool(whatsappClient),
                WhatsAppGetUnreadMessagesTool(whatsappClient),
                WhatsAppSearchMessagesTool(whatsappClient),
                WhatsAppGetMessageTool(whatsappClient),
                WhatsAppSendMessageTool(whatsappClient),
                WhatsAppReplyTool(whatsappClient)
            ))
        } catch (e: Exception) {
            Log.e("ChatActivity", "Failed to register WhatsApp tools", e)
        }

        adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = adapter

        val mem = memory
        if (mem != null) {
            lifecycleScope.launch {
                val history = mem.getRecentHistory(50)
                val start = messages.size
                history.forEach { msg ->
                    messages.add(ChatMessage(msg.content, msg.role == "user"))
                }
                if (messages.size > start) {
                    adapter.notifyItemRangeInserted(start, messages.size - start)
                }
                if (messages.isNotEmpty()) rvChat.scrollToPosition(messages.size - 1)
            }
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

        val mem = memory
        val v = voice
        if (mem == null || v == null) {
            Log.d("ChatActivity", "Memory or voice engine not available")
            return
        }

        lifecycleScope.launch {
            mem.saveMessage("user", text)
            ContextEngine.inferAndUpdateGoal(text)

            // V5: Use AgentEngine as primary path
            val result = agentEngine.process(text)
            val response = result.response

            mem.saveMessage("assistant", response)
            addMessage(response, isUser = false)
            if (v.isReady()) {
                v.speak(response)
            } else {
                Log.d("ChatActivity", "Voice not ready, skipping speech")
            }
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(ChatMessage(text, isUser))
        adapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    private fun applySavedTheme() {
        val theme = ThemeManager.getTheme(this)
        val styleName = ThemeManager.themeStyleName(theme)
        val resId = resources.getIdentifier(styleName, "style", packageName)
        if (resId != 0) setTheme(resId)
    }
}