package com.siraanabin.lootbox

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray

/** Keeps every download in memory, saves them to SharedPreferences and notifies the UI. */
object DownloadStore {
    private val map = LinkedHashMap<String, DownloadItem>()
    private lateinit var prefs: SharedPreferences
    val live = MutableLiveData<List<DownloadItem>>(emptyList())

    @Synchronized
    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("lootbox_downloads", Context.MODE_PRIVATE)
        map.clear()
        try {
            val arr = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until arr.length()) {
                var item = DownloadItem.fromJson(arr.getJSONObject(i))
                if (item.status == Status.DOWNLOADING || item.status == Status.QUEUED) item = item.copy(status = Status.PAUSED, speed = 0L)
                map[item.id] = item
            }
        } catch (e: Exception) { map.clear() }
        publish()
    }

    @Synchronized fun all(): List<DownloadItem> = map.values.toList()
    @Synchronized fun get(id: String): DownloadItem? = map[id]

    @Synchronized
    fun put(item: DownloadItem, persist: Boolean = true) {
        map[item.id] = item
        if (persist) save()
        publish()
    }

    @Synchronized
    fun remove(id: String) {
        map.remove(id)
        save()
        publish()
    }

    private fun publish() { live.postValue(map.values.sortedByDescending { it.addedAt }) }
    private fun save() {
        val arr = JSONArray()
        map.values.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
