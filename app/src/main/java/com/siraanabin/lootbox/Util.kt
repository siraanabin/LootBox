package com.siraanabin.lootbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = -1
    do {
        v /= 1024.0
        i++
    } while (v >= 1024.0 && i < units.size - 1)
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

/** Time left, like "2m 10s left". Null when it cannot be estimated yet. */
fun DownloadItem.etaText(): String? {
    if (status != Status.DOWNLOADING) return null
    val sec = if (viaEngine) {
        eta
    } else {
        if (speed <= 0 || total <= downloaded) return null
        (total - downloaded) / speed
    }
    if (sec < 0) return null
    return when {
        sec >= 3600 -> "${sec / 3600}h ${(sec % 3600) / 60}m left"
        sec >= 60 -> "${sec / 60}m ${sec % 60}s left"
        else -> "${sec}s left"
    }
}

fun extractUrl(text: String): String =
    Regex("https?://\\S+").find(text)?.value ?: text.trim()

/** Returns an error message, or null when the link is fine. */
fun validateUrl(url: String): String? {
    if (url.isEmpty()) return "Paste a link first."
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase(Locale.US)
    if ((uri.scheme != "http" && uri.scheme != "https") || host.isNullOrEmpty()) {
        return "Enter a valid http or https link."
    }
    return null
}

private val MEDIA_HOSTS = listOf(
    "youtube.com", "youtu.be", "youtube-nocookie.com",
    "facebook.com", "fb.watch", "fb.com", "instagram.com", "instagr.am"
)

/** Sites that are handled by the yt-dlp engine instead of a plain file download. */
fun isMediaSite(url: String): Boolean {
    val host = try {
        Uri.parse(url).host?.lowercase(Locale.US)
    } catch (e: Exception) {
        null
    } ?: return false
    return MEDIA_HOSTS.any { host == it || host.endsWith(".$it") }
}

fun DownloadItem.statusText(): String = when (status) {
    Status.DOWNLOADING -> {
        val sb = StringBuilder()
        sb.append(progress).append("%")
        if (viaEngine) {
            // size is unknown while yt-dlp is working
        } else if (total > 0) sb.append("  •  ${formatSize(downloaded)} of ${formatSize(total)}")
        else sb.append("  •  ${formatSize(downloaded)}")
        if (speed > 0) sb.append("  •  ${formatSize(speed)}/s")
        val eta = etaText()
        if (eta != null) sb.append("  •  ").append(eta)
        sb.toString()
    }
    Status.PAUSED -> if (viaEngine) "Paused  •  $progress%" else "Paused  •  ${formatSize(downloaded)}"
    Status.QUEUED -> "Waiting…"
    Status.COMPLETED -> "Completed  •  ${formatSize(total)}"
    Status.FAILED -> error.ifEmpty { "Failed" }
}

fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

object FileActions {
    fun open(ctx: Context, item: DownloadItem) {
        if (item.uri.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(item.uri), item.mime.ifEmpty { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(ctx, "No app can open this file")
        }
    }

    fun share(ctx: Context, item: DownloadItem) {
        if (item.uri.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND)
            .setType(item.mime.ifEmpty { "*/*" })
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "Share"))
    }
}
