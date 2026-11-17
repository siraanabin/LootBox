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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentDownloadsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = DownloadsAdapter { action, item -> handle(action, item) }
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.itemAnimator = null
        b.list.adapter = adapter

        DownloadStore.live.observe(viewLifecycleOwner) { list ->
            val active = list.filter {
                it.status == Status.QUEUED || it.status == Status.DOWNLOADING || it.status == Status.PAUSED
            }
            val done = list.filter { it.status == Status.COMPLETED }
            val failed = list.filter { it.status == Status.FAILED }

            val rows = ArrayList<Row>()
            if (active.isNotEmpty()) {
                rows.add(Row.Header("Active Downloads"))
                active.forEach { rows.add(Row.Entry(it)) }
            }
            if (done.isNotEmpty()) {
                rows.add(Row.Header("Completed"))
                done.forEach { rows.add(Row.Entry(it)) }
            }
            if (failed.isNotEmpty()) {
                rows.add(Row.Header("Failed"))
                failed.forEach { rows.add(Row.Entry(it)) }
            }
            adapter.submitList(rows)
            b.emptyText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
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
