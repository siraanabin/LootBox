package com.siraanabin.lootbox

import android.content.Context
import org.json.JSONArray

object RecycleStore {
    private const val PREFS = "lootbox_recycle_bin"
    private const val KEY = "items"

    @Synchronized
    fun all(ctx: Context): List<DownloadItem> {
        val out = mutableListOf<DownloadItem>()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out.add(DownloadItem.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {}
        return out.sortedByDescending { it.addedAt }
    }

    @Synchronized
    fun add(ctx: Context, item: DownloadItem) {
        val items = all(ctx).filterNot { it.id == item.id }.toMutableList()
        items.add(item)
        save(ctx, items)
    }

    @Synchronized
    fun remove(ctx: Context, id: String) {
        save(ctx, all(ctx).filterNot { it.id == id })
    }

    @Synchronized
    fun clear(ctx: Context) = save(ctx, emptyList())

    private fun save(ctx: Context, items: List<DownloadItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }
}
