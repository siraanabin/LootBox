package com.siraanabin.lootbox

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.siraanabin.lootbox.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    private var _b: FragmentLibraryBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentLibraryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        b.recycleBtn.setOnClickListener { showRecycleBin() }
        val adapter = LibraryAdapter(
            onOpen = { FileActions.open(ctx, it) },
            onMenu = { anchor, item -> showMenu(anchor, item) }
        )
        b.list.layoutManager = GridLayoutManager(ctx, 2)
        b.list.adapter = adapter

        DownloadStore.live.observe(viewLifecycleOwner) { list ->
            val done = list.filter { it.status == Status.COMPLETED }
            adapter.submitList(done)
            b.emptyText.visibility = if (done.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showMenu(anchor: View, item: DownloadItem) {
        val ctx = requireContext()
        val menu = PopupMenu(ctx, anchor)
        menu.menu.add(0, 1, 0, "Open")
        menu.menu.add(0, 2, 1, "Share")
        menu.menu.add(0, 3, 2, "Rename")
        menu.menu.add(0, 4, 3, "Move to Recycle Bin")
        menu.setOnMenuItemClickListener { m ->
            when (m.itemId) {
                1 -> FileActions.open(ctx, item)
                2 -> FileActions.share(ctx, item)
                3 -> rename(item)
                4 -> confirmDelete(item)
            }
            true
        }
        menu.show()
    }

    private fun rename(item: DownloadItem) {
        val ctx = requireContext()
        val input = EditText(ctx)
        input.setText(item.fileName)
        input.setSelection(item.fileName.length)
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.fileName) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                        }
                        ctx.contentResolver.update(Uri.parse(item.uri), values, null, null)
                        DownloadStore.put(item.copy(fileName = newName))
                    } catch (e: Exception) {
                        toast(ctx, "Could not rename this file")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(item: DownloadItem) {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Move to Recycle Bin?")
            .setMessage(item.fileName)
            .setPositiveButton("Move") { _, _ ->
                RecycleStore.add(ctx, item)
                DownloadStore.remove(item.id)
                toast(ctx, "Moved to Recycle Bin")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRecycleBin() {
        val ctx = requireContext()
        val items = RecycleStore.all(ctx)
        if (items.isEmpty()) {
            MaterialAlertDialogBuilder(ctx).setTitle("Recycle Bin")
                .setMessage("Recycle Bin is empty")
                .setPositiveButton("OK", null).show()
            return
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Recycle Bin · ${items.size}")
            .setItems(items.map { it.title }.toTypedArray()) { _, which ->
                val item = items[which]
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(item.title)
                    .setItems(arrayOf("Restore to Library", "Delete permanently", "Cancel")) { _, action ->
                        when (action) {
                            0 -> {
                                DownloadStore.put(item.copy(status = Status.COMPLETED))
                                RecycleStore.remove(ctx, item.id)
                                toast(ctx, "File restored")
                            }
                            1 -> MaterialAlertDialogBuilder(ctx)
                                .setTitle("Delete permanently?")
                                .setMessage("This cannot be undone.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ ->
                                    try { ctx.contentResolver.delete(Uri.parse(item.uri), null, null) } catch (_: Exception) {}
                                    RecycleStore.remove(ctx, item.id)
                                    toast(ctx, "Deleted permanently")
                                }.show()
                        }
                    }.show()
            }
            .setNegativeButton("Close", null)
            .setNeutralButton("Empty Bin") { _, _ ->
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("Empty Recycle Bin?")
                    .setMessage("All files in the bin will be permanently deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Empty") { _, _ ->
                        RecycleStore.all(ctx).forEach { item ->
                            try { ctx.contentResolver.delete(Uri.parse(item.uri), null, null) } catch (_: Exception) {}
                        }
                        RecycleStore.clear(ctx)
                        toast(ctx, "Recycle Bin emptied")
                    }.show()
            }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
