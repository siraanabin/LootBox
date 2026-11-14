package com.siraanabin.lootbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.siraanabin.lootbox.databinding.ItemVideoBinding

class LibraryAdapter(
    private val onOpen: (DownloadItem) -> Unit,
    private val onMenu: (View, DownloadItem) -> Unit
) : ListAdapter<DownloadItem, LibraryAdapter.VH>(Diff) {

    class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root)

    private object Diff : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(a: DownloadItem, b: DownloadItem): Boolean = a.id == b.id
        override fun areContentsTheSame(a: DownloadItem, b: DownloadItem): Boolean = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b
        b.name.text = item.title
        b.size.text = formatSize(item.total)
        Thumbs.load(b.root.context, item, b.thumb, b.duration)
        b.root.setOnClickListener { onOpen(item) }
        b.more.setOnClickListener { onMenu(b.more, item) }
    }
}
