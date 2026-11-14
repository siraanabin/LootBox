package com.siraanabin.lootbox

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val DARK = "dark_theme"
    const val WIFI = "wifi_only"
    const val MULTI = "multi_downloads"
    const val NOTIFY = "notify_done"
    const val QUALITY = "video_quality"
    const val PLAYLIST = "playlist_download"

    val QUALITY_KEYS = arrayOf("best", "1080", "720", "480", "audio")
    val QUALITY_LABELS = arrayOf("Best available", "Up to 1080p", "Up to 720p", "Up to 480p", "Audio only (MP3)")

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("lootbox_prefs", Context.MODE_PRIVATE)
}
