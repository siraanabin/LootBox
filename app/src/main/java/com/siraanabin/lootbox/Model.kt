package com.siraanabin.lootbox

import org.json.JSONObject

enum class Status { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String = "",
    val total: Long = 0L,
    val downloaded: Long = 0L,
    val status: Status = Status.QUEUED,
    val speed: Long = 0L,
    val error: String = "",
    val uri: String = "",
    val mime: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val eta: Long = -1L,
    val viaEngine: Boolean = false,
    val quality: String = ""
) {
    val progress: Int get() = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
    val title: String get() = fileName.ifEmpty { url }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("url", url); put("fileName", fileName); put("total", total)
        put("downloaded", downloaded); put("status", status.name); put("error", error)
        put("uri", uri); put("mime", mime); put("addedAt", addedAt)
        put("viaEngine", viaEngine); put("quality", quality)
    }

    companion object {
        fun fromJson(o: JSONObject): DownloadItem = DownloadItem(
            id = o.getString("id"), url = o.getString("url"), fileName = o.optString("fileName"),
            total = o.optLong("total"), downloaded = o.optLong("downloaded"),
            status = try { Status.valueOf(o.optString("status", "PAUSED")) } catch (e: Exception) { Status.PAUSED },
            error = o.optString("error"), uri = o.optString("uri"), mime = o.optString("mime"),
            addedAt = o.optLong("addedAt", System.currentTimeMillis()),
            viaEngine = o.optBoolean("viaEngine", false), quality = o.optString("quality")
        )
    }
}
