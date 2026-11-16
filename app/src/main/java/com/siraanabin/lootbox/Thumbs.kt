package com.siraanabin.lootbox

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Loads video / image thumbnails (and video length) in the background. */
object Thumbs {
    private val cache = LruCache<String, Bitmap>(60)
    private val durations = ConcurrentHashMap<String, String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, item: DownloadItem, view: ImageView, durationView: TextView?) {
        val key = item.uri
        view.tag = key
        durationView?.visibility = View.GONE
        val canPreview = item.mime.startsWith("video/") || item.mime.startsWith("image/")
        if (!canPreview || key.isEmpty()) {
            showIcon(view)
            return
        }
        val cached = cache.get(key)
        if (cached != null) {
            view.clearColorFilter()
            view.scaleType = ImageView.ScaleType.CENTER_CROP
            view.setImageBitmap(cached)
            showDuration(durationView, durations[key])
            return
        }
        showIcon(view)
        val app = ctx.applicationContext
        pool.execute {
            var bmp: Bitmap? = null
            var dur: String? = null
            try {
                bmp = app.contentResolver.loadThumbnail(Uri.parse(key), Size(400, 300), null)
            } catch (e: Exception) {
            }
            if (item.mime.startsWith("video/")) {
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(app, Uri.parse(key))
                    val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    r.release()
                    if (ms != null) dur = formatDuration(ms)
                } catch (e: Exception) {
                }
            }
            val finalBmp = bmp
            val finalDur = dur
            main.post {
                if (finalBmp != null) cache.put(key, finalBmp)
                if (finalDur != null) durations[key] = finalDur
                if (view.tag == key) {
                    if (finalBmp != null) {
                        view.clearColorFilter()
                        view.scaleType = ImageView.ScaleType.CENTER_CROP
                        view.setImageBitmap(finalBmp)
                    }
                    showDuration(durationView, finalDur)
                }
            }
        }
    }

    private fun showIcon(view: ImageView) {
        view.scaleType = ImageView.ScaleType.CENTER_INSIDE
        view.setImageResource(R.drawable.ic_file)
        view.setColorFilter(view.context.getColor(R.color.muted))
    }

    private fun showDuration(tv: TextView?, text: String?) {
        if (tv == null) return
        if (text == null) {
            tv.visibility = View.GONE
        } else {
            tv.text = text
            tv.visibility = View.VISIBLE
        }
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
    }
}
