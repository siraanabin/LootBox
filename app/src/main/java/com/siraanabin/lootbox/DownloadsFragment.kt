package com.siraanabin.lootbox

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.siraanabin.lootbox.databinding.FragmentDownloadsBinding

class DownloadsFragment : Fragment() {
    private var _b: FragmentDownloadsBinding? = null
    private val b get() = _b!!
    private var selectedFilter = "All"
    private var currentItems: List<DownloadItem> = emptyList()
    private lateinit var adapter: DownloadsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentDownloadsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DownloadsAdapter { action, item -> handle(action, item) }
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.itemAnimator = null
        b.list.adapter = adapter
        fun choose(filter: String) {
            selectedFilter = filter
            listOf(b.filterAll, b.filterActive, b.filterDone, b.filterFailed).forEach { chip ->
                val active = chip.tag == filter
                chip.isSelected = active
                chip.setTextColor(requireContext().getColor(if (active) android.R.color.white else R.color.text))
                chip.setBackgroundResource(if (active) R.drawable.bg_button else R.drawable.bg_chip)
            }
            render()
        }
        b.filterAll.tag = "All"
        b.filterActive.tag = "Active"
        b.filterDone.tag = "Completed"
        b.filterFailed.tag = "Failed"
        b.filterAll.setOnClickListener { choose("All") }
        b.filterActive.setOnClickListener { choose("Active") }
        b.filterDone.setOnClickListener { choose("Completed") }
        b.filterFailed.setOnClickListener { choose("Failed") }
        choose("All")
        DownloadStore.live.observe(viewLifecycleOwner) {
            currentItems = it
            render()
        }
    }

    private fun render() {
        if (!::adapter.isInitialized) return
        val active = currentItems.filter { it.status == Status.QUEUED || it.status == Status.DOWNLOADING || it.status == Status.PAUSED }
        val done = currentItems.filter { it.status == Status.COMPLETED }
        val failed = currentItems.filter { it.status == Status.FAILED }
        val rows = ArrayList<Row>()
        if (selectedFilter == "All" || selectedFilter == "Active") {
            if (active.isNotEmpty()) {
                rows.add(Row.Header("Active Downloads"))
                active.forEach { rows.add(Row.Entry(it)) }
            }
        }
        if (selectedFilter == "All" || selectedFilter == "Completed") {
            if (done.isNotEmpty()) {
                rows.add(Row.Header("Completed"))
                done.forEach { rows.add(Row.Entry(it)) }
            }
        }
        if (selectedFilter == "All" || selectedFilter == "Failed") {
            if (failed.isNotEmpty()) {
                rows.add(Row.Header("Failed"))
                failed.forEach { rows.add(Row.Entry(it)) }
            }
        }
        adapter.submitList(rows)
        b.emptyText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        b.emptyText.text = if (currentItems.isEmpty()) "Your downloads will appear here" else "No $selectedFilter downloads"
    }

    private fun handle(action: DownloadsAdapter.Action, item: DownloadItem) {
        val ctx = requireContext()
        when (action) {
            DownloadsAdapter.Action.PAUSE -> DownloadService.pause(ctx, item.id)
            DownloadsAdapter.Action.RESUME, DownloadsAdapter.Action.RETRY -> DownloadService.resume(ctx, item.id)
            DownloadsAdapter.Action.CANCEL -> DownloadService.cancel(ctx, item.id)
            DownloadsAdapter.Action.OPEN -> FileActions.open(ctx, item)
            DownloadsAdapter.Action.COPY -> {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("link", item.url))
                toast(ctx, "Link copied")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
