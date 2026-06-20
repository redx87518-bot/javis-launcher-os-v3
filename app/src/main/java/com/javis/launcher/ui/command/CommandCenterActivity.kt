package com.javis.launcher.ui.command

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DividerItemDecoration
import android.view.LayoutInflater
import android.view.ViewGroup
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.engine.PersonalityEngine
import com.javis.launcher.models.CommandLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CommandCenterActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_command_center)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        loadStatus()
        loadCommandLog()
    }

    private fun loadStatus() {
        val ai   = com.javis.launcher.engine.ai.AIEngine(this)
        val prov = ai.getActiveProvider()

        findViewById<TextView>(R.id.tv_ai_status).text =
            if (prov != null) "AI Provider: ${prov.name} ✓" else "AI Provider: Not configured"

        val voice = JavisApplication.instance.voiceEngine
        findViewById<TextView>(R.id.tv_voice_status).text =
            "TTS Engine: ${if (voice.isReady()) "Ready ✓" else "Initializing..."}"

        findViewById<TextView>(R.id.tv_personality_status).text =
            "Personality: ${PersonalityEngine.modeName()}"
    }

    private fun loadCommandLog() {
        lifecycleScope.launch {
            val logs = memory.getCommandLogs(100)
            val rv = findViewById<RecyclerView>(R.id.rv_command_log)
            rv.layoutManager = LinearLayoutManager(this@CommandCenterActivity)
                .apply { reverseLayout = true; stackFromEnd = true }
            rv.addItemDecoration(DividerItemDecoration(this@CommandCenterActivity, DividerItemDecoration.VERTICAL))
            rv.adapter = CommandLogAdapter(logs)

            val tvEmpty = findViewById<TextView>(R.id.tv_empty)
            tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────
    inner class CommandLogAdapter(private val logs: List<CommandLog>)
        : RecyclerView.Adapter<CommandLogAdapter.VH>() {

        private val fmt = SimpleDateFormat("MMM d HH:mm", Locale.getDefault())

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvAction: TextView = v.findViewById(R.id.tv_log_action)
            val tvDetail: TextView = v.findViewById(R.id.tv_log_detail)
            val tvTime:   TextView = v.findViewById(R.id.tv_log_time)
            val tvStatus: TextView = v.findViewById(R.id.tv_log_status)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_command_log, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val log = logs[position]
            holder.tvAction.text = log.action
            holder.tvDetail.text = log.detail.ifBlank { "—" }
            holder.tvTime.text   = fmt.format(Date(log.timestamp))
            holder.tvStatus.text = log.result
            holder.tvStatus.setTextColor(
                if (log.result.startsWith("✓") || log.result.contains("success", true))
                    0xFF00CC50.toInt() else 0xFFCC0000.toInt()
            )
        }

        override fun getItemCount() = logs.size
    }
}
