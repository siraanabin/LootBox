package com.siraanabin.lootbox

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.siraanabin.lootbox.databinding.ItemActiveBinding
import com.siraanabin.lootbox.databinding.ItemDoneBinding
import com.siraanabin.lootbox.databinding.ItemFailedBinding
import com.siraanabin.lootbox.databinding.ItemHeaderBinding

sealed class Row {
    data class Header(val title: String) : Row()
    data class Entry(val item: DownloadItem) : Row()
}
class DownloadsAdapter(private val onAction: (Action, DownloadItem) -> Unit) : ListAdapter<Row, RecyclerView.ViewHolder>(Diff) {
    enum class Action { PAUSE, RESUME, CANCEL, RETRY, OPEN, COPY }
    private class HeaderVH(val b: ItemHeaderBinding) : RecyclerView.ViewHolder(b.root)
    private class ActiveVH(val b: ItemActiveBinding) : RecyclerView.ViewHolder(b.root)
    private class DoneVH(val b: ItemDoneBinding) : RecyclerView.ViewHolder(b.root)
    private class FailedVH(val b: ItemFailedBinding) : RecyclerView.ViewHolder(b.root)
    private object Diff : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(a: Row, b: Row): Boolean = when {
            a is Row.Header && b is Row.Header -> a.title == b.title
            a is Row.Entry && b is Row.Entry -> a.item.id == b.item.id && kind(a.item) == kind(b.item)
            else -> false
        }
        override fun areContentsTheSame(a: Row, b: Row): Boolean = a == b
    }
    companion object {
        private fun kind(item: DownloadItem): Int = when (item.status) {
            Status.COMPLETED -> 2
            Status.FAILED -> 3
            else -> 1
        }
    }
    override fun getItemViewType(position: Int): Int = when (val r = getItem(position)) {
        is Row.Header -> 0
        is Row.Entry -> kind(r.item)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderVH(ItemHeaderBinding.inflate(inflater, parent, false))
            2 -> DoneVH(ItemDoneBinding.inflate(inflater, parent, false))
            3 -> FailedVH(ItemFailedBinding.inflate(inflater, parent, false))
            else -> ActiveVH(ItemActiveBinding.inflate(inflater, parent, false))
        }
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderVH -> holder.b.title.text = (getItem(position) as Row.Header).title
            is ActiveVH -> bindActive(holder.b, (getItem(position) as Row.Entry).item)
            is DoneVH -> bindDone(holder.b, (getItem(position) as Row.Entry).item)
            is FailedVH -> bindFailed(holder.b, (getItem(position) as Row.Entry).item)
        }
    }
    private fun bindActive(b: ItemActiveBinding, item: DownloadItem) {
        b.name.text = item.title
        b.progress.progress = item.progress
        b.speed.text = when (item.status) {
            Status.DOWNLOADING -> if (item.viaEngine) item.etaText() ?: "Downloading…" else if (item.speed > 0) "Speed: ${formatSize(item.speed)}/s" + (item.etaText()?.let { "  •  $it" } ?: "") else "Starting…"
            Status.PAUSED -> "Paused"
            else -> "Waiting…"
        }
        b.size.text = if (item.viaEngine) "${item.progress}%" else if (item.total > 0) "${formatSize(item.downloaded)} of ${formatSize(item.total)}" else formatSize(item.downloaded)
        val paused = item.status == Status.PAUSED
        b.toggle.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        b.toggle.contentDescription = if (paused) "Resume" else "Pause"
        b.toggle.setOnClickListener { onAction(if (paused) Action.RESUME else Action.PAUSE, item) }
        b.cancel.setOnClickListener { onAction(Action.CANCEL, item) }
        b.more.setOnClickListener { v ->
            val menu = PopupMenu(v.context, v)
            menu.menu.add("Copy link")
            menu.setOnMenuItemClickListener { onAction(Action.COPY, item); true }
            menu.show()
        }
    }
    private fun bindDone(b: ItemDoneBinding, item: DownloadItem) {
        b.name.text = item.title
        b.size.text = formatSize(item.total)
        Thumbs.load(b.root.context, item, b.thumb, null)
        b.root.setOnClickListener { onAction(Action.OPEN, item) }
    }
    private fun bindFailed(b: ItemFailedBinding, item: DownloadItem) {
        b.name.text = item.title
        b.error.text = item.error.ifEmpty { "Download failed" }
        b.retry.setOnClickListener { onAction(Action.RETRY, item) }
        b.remove.setOnClickListener { onAction(Action.CANCEL, item) }
    }
}
