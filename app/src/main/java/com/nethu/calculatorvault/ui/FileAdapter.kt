package com.nethu.calculatorvault.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.nethu.calculatorvault.R
import com.nethu.calculatorvault.databinding.ItemFileBinding
import com.nethu.calculatorvault.vault.VaultItem
import com.nethu.calculatorvault.vault.VaultRepository
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class FileAdapter(
    private val ctx: Context,
    private val onOpen: (VaultItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root) { var boundId: String? = null }

    private var items: List<VaultItem> = emptyList()
    private val selected = linkedSetOf<String>()
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val dateFmt = DateFormat.getDateInstance()

    fun submit(list: List<VaultItem>) {
        items = list
        selected.retainAll(list.map { it.id }.toSet())
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun selectedItems(): List<VaultItem> = items.filter { it.id in selected }
    fun allSelected() = items.isNotEmpty() && selected.size == items.size

    fun toggleAll() {
        if (allSelected()) selected.clear() else selected.addAll(items.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun shutdown() { pool.shutdownNow() }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.boundId = item.id
        h.b.tvName.text = item.name
        h.b.tvMeta.text = Formatter.formatShortFileSize(ctx, item.size) + " · " + dateFmt.format(Date(item.addedAt))
        h.b.cbSelect.setOnCheckedChangeListener(null)
        h.b.cbSelect.isChecked = item.id in selected
        h.b.cbSelect.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(item.id) else selected.remove(item.id)
            onSelectionChanged(selected.size)
        }
        h.b.root.setOnClickListener { onOpen(item) }

        h.b.ivThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
        h.b.ivThumb.setImageResource(
            when { item.isImage -> R.drawable.ic_image; item.isVideo -> R.drawable.ic_movie; else -> R.drawable.ic_file }
        )
        if (item.isImage) {
            val cached = cache.get(item.id)
            if (cached != null) showThumb(h, cached) else pool.execute {
                val bmp = try { VaultRepository.decodeImage(ctx, item.id, 160) } catch (e: Exception) { null }
                if (bmp != null) {
                    cache.put(item.id, bmp)
                    main.post { if (h.boundId == item.id) showThumb(h, bmp) }
                }
            }
        }
    }

    private fun showThumb(h: VH, bmp: Bitmap) {
        h.b.ivThumb.scaleType = ImageView.ScaleType.CENTER_CROP
        h.b.ivThumb.setImageBitmap(bmp)
    }
}
