package com.siraanabin.lootbox

import android.content.Context
import org.json.JSONArray

/** Local-only storage for bookmarked and recently submitted media links. */
object LinkStore {
    private const val PREFS = "lootbox_links"
    private const val BOOKMARKS = "bookmarks"
    private const val HISTORY = "history"

    fun bookmarks(ctx: Context): List<String> = read(ctx, BOOKMARKS)
    fun history(ctx: Context): List<String> = read(ctx, HISTORY)
    fun addBookmark(ctx: Context, url: String) = add(ctx, BOOKMARKS, url, 40)
    fun addHistory(ctx: Context, url: String) = add(ctx, HISTORY, url, 20)

    fun remove(ctx: Context, url: String, bookmark: Boolean) {
        val key = if (bookmark) BOOKMARKS else HISTORY
        write(ctx, key, read(ctx, key).filterNot { it == url })
    }

    private fun add(ctx: Context, key: String, url: String, limit: Int) {
        if (url.isBlank()) return
        write(ctx, key, (listOf(url) + read(ctx, key).filterNot { it == url }).take(limit))
    }

    private fun read(ctx: Context, key: String): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private fun write(ctx: Context, key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
    }
}
