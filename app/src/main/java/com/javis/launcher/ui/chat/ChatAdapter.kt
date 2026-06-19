package com.javis.launcher.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.R

data class ChatMessage(val text: String, val isUser: Boolean)

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUser: TextView? = itemView.findViewById(R.id.tv_user_msg)
        val tvJavis: TextView? = itemView.findViewById(R.id.tv_javis_msg)
    }

    override fun getItemViewType(position: Int) = if (messages[position].isUser) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == 0) R.layout.item_chat_user else R.layout.item_chat_javis
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        if (msg.isUser) holder.tvUser?.text = msg.text
        else holder.tvJavis?.text = msg.text
    }

    override fun getItemCount() = messages.size
}
