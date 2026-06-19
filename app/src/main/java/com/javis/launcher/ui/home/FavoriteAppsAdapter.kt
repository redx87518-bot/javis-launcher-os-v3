package com.javis.launcher.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.javis.launcher.R

class FavoriteAppsAdapter(
    private val apps: List<Pair<String, String>>,
    private val onTap: (String) -> Unit
) : RecyclerView.Adapter<FavoriteAppsAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.iv_app_icon)
        val label: TextView = itemView.findViewById(R.id.tv_app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (name, pkg) = apps[position]
        holder.label.text = name
        try {
            val icon = holder.itemView.context.packageManager.getApplicationIcon(pkg)
            holder.icon.setImageDrawable(icon)
        } catch (e: Exception) {
            holder.icon.setImageResource(R.drawable.ic_app_default)
        }
        holder.itemView.setOnClickListener { onTap(pkg) }
    }

    override fun getItemCount() = apps.size
}
