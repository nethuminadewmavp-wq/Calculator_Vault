package com.nethu.calculatorvault.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nethu.calculatorvault.databinding.ItemAppBinding

data class AppEntry(val label: String, val pkg: String, val icon: Drawable)

class AppAdapter(
    private val checked: MutableSet<String>,          // shared with the activity
    private val onOpen: (AppEntry) -> Unit,
    private val onToggle: (AppEntry, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    private var items: List<AppEntry> = emptyList()

    fun submit(list: List<AppEntry>) { items = list; notifyDataSetChanged() }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val e = items[position]
        h.b.ivIcon.setImageDrawable(e.icon)
        h.b.tvLabel.text = e.label
        h.b.tvPkg.text = e.pkg
        h.b.cbList.setOnCheckedChangeListener(null)
        h.b.cbList.isChecked = e.pkg in checked
        h.b.cbList.setOnCheckedChangeListener { _, c -> onToggle(e, c) }
        h.b.root.setOnClickListener { onOpen(e) }
    }
}
