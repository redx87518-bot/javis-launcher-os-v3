package com.javis.launcher.ui.memory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.JavisApplication
import com.javis.launcher.R
import com.javis.launcher.models.Memory
import kotlinx.coroutines.launch

class MemoryActivity : AppCompatActivity() {

    private val memory get() = JavisApplication.instance.memoryEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        val rvMemory = findViewById<RecyclerView>(R.id.rv_memory)
        rvMemory.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val memories = memory.recallAll()
            val topApps = memory.getTopApps(5)
            val topContacts = memory.getTopContacts(5)

            val items = mutableListOf<MemoryItem>()
            if (memories.isNotEmpty()) {
                items.add(MemoryItem("STORED MEMORIES", "", isHeader = true))
                memories.forEach { m ->
                    items.add(MemoryItem(formatKey(m.key), m.value))
                }
            }
            if (topApps.isNotEmpty()) {
                items.add(MemoryItem("FREQUENT APPS", "", isHeader = true))
                topApps.forEach { a ->
                    items.add(MemoryItem(a.appName, "Used ${a.useCount} times"))
                }
            }
            if (topContacts.isNotEmpty()) {
                items.add(MemoryItem("FREQUENT CONTACTS", "", isHeader = true))
                topContacts.forEach { c ->
                    items.add(MemoryItem(c.name, "Called ${c.callCount} times"))
                }
            }
            if (items.isEmpty()) {
                items.add(MemoryItem("No memories yet", "Start talking to JAVIS to build memory.", isHeader = false))
            }

            rvMemory.adapter = MemoryAdapter(items)
        }
    }

    private fun formatKey(key: String) = key.replace("_", " ").replaceFirstChar { it.uppercase() }
}

data class MemoryItem(val key: String, val value: String, val isHeader: Boolean = false)

class MemoryAdapter(private val items: List<MemoryItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int) = if (items[position].isHeader) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_header, parent, false)
            HeaderVH(v)
        } else {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_memory, parent, false)
            MemVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is HeaderVH -> holder.tv.text = item.key
            is MemVH -> { holder.tvKey.text = item.key; holder.tvVal.text = item.value }
        }
    }

    override fun getItemCount() = items.size

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) { val tv: TextView = v.findViewById(R.id.tv_header) }
    class MemVH(v: View) : RecyclerView.ViewHolder(v) {
        val tvKey: TextView = v.findViewById(R.id.tv_key)
        val tvVal: TextView = v.findViewById(R.id.tv_value)
    }
}
