package com.siraanabin.lootbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Foreground service that downloads files with pause / resume / cancel.
 * Files are written to cache as ".part" and moved to Downloads/LootBox when finished.
 */
class DownloadService : Service() {

    companion object {
        private const val ACTION_ADD = "add"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_CANCEL = "cancel"
        private const val EXTRA_URL = "url"
        private const val EXTRA_ID = "id"
        private const val EXTRA_QUALITY = "quality"

        private const val CH_PROGRESS = "progress"
        private const val CH_DONE = "done"
        private const val NOTIF_ID = 1001

        fun add(ctx: Context, url: String, quality: String = "") =
            send(ctx, ACTION_ADD) { putExtra(EXTRA_URL, url); putExtra(EXTRA_QUALITY, quality) }
        fun pause(ctx: Context, id: String) = send(ctx, ACTION_PAUSE) { putExtra(EXTRA_ID, id) }
        fun resume(ctx: Context, id: String) = send(ctx, ACTION_RESUME) { putExtra(EXTRA_ID, id) }
        fun cancel(ctx: Context, id: String) = send(ctx, ACTION_CANCEL) { putExtra(EXTRA_ID, id) }

        private fun send(ctx: Context, action: String, block: Intent.() -> Unit) {
            val intent = Intent(ctx, DownloadService::class.java).setAction(action)
            intent.block()
            ContextCompat.startForegroundService(ctx, intent)
        }
    }

    private class Control {
        @Volatile var pause = false
        @Volatile var cancel = false
    }

    private val controls = ConcurrentHashMap<String, Control>()
    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopped = false
        startForeground(NOTIF_ID, progressNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        val id = intent?.getStringExtra(EXTRA_ID).orEmpty()
        when (intent?.action) {
            ACTION_ADD -> {
                val url = intent?.getStringExtra(EXTRA_URL)?.trim().orEmpty()
                if (url.isNotEmpty()) {
                    DownloadStore.put(DownloadItem(id = UUID.randomUUID().toString().take(8), url = url, viaEngine = isMediaSite(url),
                            quality = intent?.getStringExtra(EXTRA_QUALITY).orEmpty()))
                }
            }
            ACTION_PAUSE -> {
                controls[id]?.pause = true
                if (DownloadStore.get(id)?.viaEngine == true) killEngine(id)
                val item = DownloadStore.get(id)
                val waiting = item != null && (item.status == Status.QUEUED ||
                    (item.status == Status.DOWNLOADING && !controls.containsKey(id)))
                if (item != null && waiting) {
                    DownloadStore.put(item.copy(status = Status.PAUSED))
                }
            }
            ACTION_RESUME -> {
                val item = DownloadStore.get(id)
                if (item != null) {
                    DownloadStore.put(item.copy(status = Status.QUEUED, error = "", speed = 0L))
                }
            }
            ACTION_CANCEL -> {
                val ctl = controls[id]
                if (ctl != null) {
                    ctl.cancel = true
                    if (DownloadStore.get(id)?.viaEngine == true) killEngine(id)
                } else {
                    File(partDir(), "$id.part").delete()
                    File(partDir(), id).deleteRecursively()
                    DownloadStore.remove(id)
                }
            }
        }
        pump()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopped = true
        pool.shutdownNow()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- queue

    private fun pump() {
        val prefs = Prefs.get(this)
        val max = if (prefs.getBoolean(Prefs.MULTI, true)) 3 else 1
        var running = controls.size
        val queued = DownloadStore.all()
            .filter { it.status == Status.QUEUED }
            .sortedBy { it.addedAt }
        for (item in queued) {
            if (controls.containsKey(item.id)) continue
            if (running >= max) break
            if (prefs.getBoolean(Prefs.WIFI, false) && !onWifi()) {
                DownloadStore.put(
                    item.copy(status = Status.FAILED, error = "Wi-Fi only is on. Connect to Wi-Fi and tap Retry.")
                )
                continue
            }
            val ctl = Control()
            controls[item.id] = ctl
            running++
            DownloadStore.put(item.copy(status = Status.DOWNLOADING, error = ""), persist = false)
            pool.execute {
                try {
                    runTask(item.id, ctl)
                } finally {
                    controls.remove(item.id)
                    main.post { pump() }
                }
            }
        }
        refreshOrStop()
    }

    private fun refreshOrStop() {
        val busy = DownloadStore.all().any { it.status == Status.DOWNLOADING || it.status == Status.QUEUED }
        if (!busy && controls.isEmpty()) {
            stopped = true
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun onWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    // ---------------------------------------------------------------- download

    private fun partDir(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        return File(base, "LootBox").apply { mkdirs() }
    }

    private fun runTask(id: String, ctl: Control) {
        var item = DownloadStore.get(id) ?: return
        if (item.viaEngine) {
            runEngine(id, ctl)
            return
        }
        val part = File(partDir(), "$id.part")
        var conn: HttpURLConnection? = null
        var done = 0L
        try {
            var existing = if (part.exists()) part.length() else 0L
            val c = URL(item.url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LootBox/1.0")
            c.setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0) c.setRequestProperty("Range", "bytes=$existing-")

            val code = c.responseCode
            if (code == 416 && existing > 0) {
                finish(item.copy(downloaded = existing, total = existing), part)
                return
            }
            if (code !in 200..299) throw IOException("Server returned HTTP $code")

            val type = c.contentType ?: ""
            if (type.contains("text/html", ignoreCase = true)) {
                // A normal web page: let yt-dlp try to find the video on it.
                DownloadStore.put(item.copy(viaEngine = true, status = Status.QUEUED), persist = true)
                c.disconnect()
                runEngine(id, ctl)
                return
            }

            val resumed = code == 206 && existing > 0
            if (!resumed) existing = 0L
            val len = c.contentLengthLong
            val total = if (resumed) {
                parseTotal(c.getHeaderField("Content-Range"), if (len > 0) existing + len else 0L)
            } else {
                maxOf(len, 0L)
            }
            val name = item.fileName.ifEmpty { resolveName(c, item.url, type) }
            item = item.copy(
                fileName = name,
                mime = mimeFor(name, type),
                total = total,
                downloaded = existing,
                status = Status.DOWNLOADING,
                error = ""
            )
            DownloadStore.put(item)

            done = existing
            var lastTick = SystemClock.elapsedRealtime()
            var lastBytes = done
            c.inputStream.use { input ->
                FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (!ctl.cancel && !ctl.pause) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastTick >= 500) {
                            val speed = (done - lastBytes) * 1000 / (now - lastTick)
                            lastTick = now
                            lastBytes = done
                            DownloadStore.put(item.copy(downloaded = done, speed = speed), persist = false)
                            main.post { refreshNotification() }
                        }
                    }
                }
            }

            when {
                ctl.cancel -> {
                    part.delete()
                    DownloadStore.remove(id)
                }
                ctl.pause -> {
                    DownloadStore.put(item.copy(downloaded = done, speed = 0L, status = Status.PAUSED))
                }
                else -> {
                    if (total > 0 && done < total) throw IOException("Connection lost before the file finished.")
                    finish(item.copy(downloaded = done), part)
                }
            }
        } catch (e: Exception) {
            when {
                ctl.cancel -> {
                    part.delete()
                    DownloadStore.remove(id)
                }
                ctl.pause -> {
                    val cur = DownloadStore.get(id)
                    if (cur != null) {
                        DownloadStore.put(cur.copy(downloaded = done, speed = 0L, status = Status.PAUSED))
                    }
                }
                else -> {
                    if (scheduleRetry(id, e)) return
                    val cur = DownloadStore.get(id) ?: return
                    val failed = cur.copy(
                        status = Status.FAILED,
                        speed = 0L,
                        error = e.message ?: "Download failed"
                    )
                    DownloadStore.put(failed)
                    main.post { notifyDone(failed, false) }
                }
            }
        } finally {
            conn?.disconnect()
        }
    }


    // ---------------------------------------------------------------- yt-dlp engine

    private fun killEngine(id: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Throwable) {
        }
    }

    private val retries = ConcurrentHashMap<String, Int>()

    private fun isNetworkError(e: Throwable): Boolean {
        if (e is java.net.SocketException || e is java.net.SocketTimeoutException ||
            e is java.net.UnknownHostException || e is javax.net.ssl.SSLException
        ) return true
        val m = (e.message ?: "").lowercase()
        return listOf(
            "connection lost", "timed out", "unable to resolve", "urlopen", "connection reset",
            "name or service", "temporary failure", "network is unreachable"
        ).any { it in m }
    }

    /** Retries up to 3 times after a network error. Returns true when a retry was scheduled. */
    private fun scheduleRetry(id: String, e: Throwable): Boolean {
        if (!isNetworkError(e)) return false
        val n = retries[id] ?: 0
        if (n >= 3) {
            retries.remove(id)
            return false
        }
        retries[id] = n + 1
        val cur = DownloadStore.get(id) ?: return false
        DownloadStore.put(cur.copy(status = Status.DOWNLOADING, speed = 0L, eta = -1L, error = ""), persist = false)
        main.postDelayed({
            val c = DownloadStore.get(id)
            if (c != null && c.status == Status.DOWNLOADING && !controls.containsKey(id)) {
                DownloadStore.put(c.copy(status = Status.QUEUED), persist = false)
                pump()
            }
        }, 5000L * (n + 1))
        return true
    }

    private fun qualityFormat(item: DownloadItem): List<String> = when (
        item.quality.ifEmpty { Prefs.get(this).getString(Prefs.QUALITY, "best") }
    ) {
        "1080" -> listOf("-f", "bv*[height<=1080]+ba/b[height<=1080]/b", "--merge-output-format", "mp4")
        "720" -> listOf("-f", "bv*[height<=720]+ba/b[height<=720]/b", "--merge-output-format", "mp4")
        "480" -> listOf("-f", "bv*[height<=480]+ba/b[height<=480]/b", "--merge-output-format", "mp4")
        "audio" -> listOf("-f", "ba/b", "-x", "--audio-format", "mp3")
        else -> listOf("-f", "bv*+ba/b", "--merge-output-format", "mp4")
    }

    private fun runEngine(id: String, ctl: Control) {
        val item = DownloadStore.get(id) ?: return
        val dir = File(partDir(), id).apply { mkdirs() }
        DownloadStore.put(item.copy(status = Status.DOWNLOADING, total = 1000L, error = ""), persist = false)
        try {
            if (!LootBoxApp.awaitEngine()) {
                throw IOException("Download engine is not ready: " + LootBoxApp.engineError.ifEmpty { "timed out" })
            }
            val req = YoutubeDLRequest(item.url)
            val opts = qualityFormat(item)
            var i = 0
            while (i < opts.size) {
                if (opts[i].startsWith("-") && i + 1 < opts.size && !opts[i + 1].startsWith("-")) {
                    req.addOption(opts[i], opts[i + 1]); i += 2
                } else {
                    req.addOption(opts[i]); i++
                }
            }
            req.addOption("-o", "${dir.absolutePath}/%(title).80B.%(ext)s")
            val playlist = Prefs.get(this).getBoolean(Prefs.PLAYLIST, false)
            req.addOption(if (playlist) "--yes-playlist" else "--no-playlist")
            req.addOption("--no-mtime")
            val cookies = File(filesDir, "cookies.txt")
            if (cookies.exists()) req.addOption("--cookies", cookies.absolutePath)
            req.addOption("--newline")

            var lastEmit = 0L
            YoutubeDL.getInstance().execute(req, id) { progress, eta, line ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastEmit < 400) return@execute
                lastEmit = now
                val cur = DownloadStore.get(id) ?: return@execute
                if (cur.status != Status.DOWNLOADING) return@execute
                val p = if (progress < 0f) 0f else progress.coerceAtMost(100f)
                var name = cur.fileName
                val m = Regex("Destination: (.+)").find(line)
                if (m != null) {
                    name = File(m.groupValues[1].trim()).name
                        .removeSuffix(".part").replace(Regex("\\.f\\d+(?=\\.)"), "")
                }
                DownloadStore.put(
                    cur.copy(total = 1000L, downloaded = (p * 10).toLong(), eta = eta, fileName = name),
                    persist = false
                )
                main.post { refreshNotification() }
            }

            if (ctl.cancel) {
                dir.deleteRecursively()
                DownloadStore.remove(id)
                return
            }
            if (ctl.pause) {
                DownloadStore.get(id)?.let { DownloadStore.put(it.copy(speed = 0L, eta = -1L, status = Status.PAUSED)) }
                return
            }
            val files = dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp") }
                ?.sortedBy { it.name }
                .orEmpty()
            if (files.isEmpty()) throw IOException("Download finished but no file was created.")
            val cur = DownloadStore.get(id) ?: return
            val main1 = if (playlist) files.first() else files.maxByOrNull { it.length() }!!
            finish(cur.copy(fileName = main1.name, mime = mimeFor(main1.name, ""), eta = -1L), main1)
            if (playlist) {
                files.filter { it != main1 }.forEach { f ->
                    val extra = DownloadItem(
                        id = UUID.randomUUID().toString().take(8), url = cur.url, fileName = f.name,
                        mime = mimeFor(f.name, ""), viaEngine = true, status = Status.COMPLETED
                    )
                    finish(extra, f)
                }
            }
            dir.deleteRecursively()
        } catch (e: Throwable) {
            when {
                ctl.cancel -> {
                    dir.deleteRecursively()
                    DownloadStore.remove(id)
                }
                ctl.pause -> {
                    DownloadStore.get(id)?.let { DownloadStore.put(it.copy(speed = 0L, eta = -1L, status = Status.PAUSED)) }
                }
                else -> {
                    if (scheduleRetry(id, e)) return
                    val cur = DownloadStore.get(id) ?: return
                    val failed = cur.copy(status = Status.FAILED, speed = 0L, eta = -1L, error = friendlyError(e))
                    DownloadStore.put(failed)
                    main.post { notifyDone(failed, false) }
                }
            }
        }
    }

    private fun friendlyError(e: Throwable): String {
        val raw = (e.message ?: "Download failed").lines().lastOrNull { it.isNotBlank() } ?: "Download failed"
        val low = raw.lowercase()
        return when {
            "login" in low || "cookies" in low || "private" in low || "sign in" in low ->
                "This video needs a login or is private, so it can't be downloaded."
            "unsupported url" in low -> "This link is not supported."
            "unable to resolve" in low || "network" in low || "urlopen" in low ->
                "Network problem. Check your internet and tap Retry."
            else -> raw.take(160)
        }
    }

    /** Moves the finished file into the public Downloads/LootBox folder. */
    private fun finish(item: DownloadItem, part: File) {
        val size = part.length()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName.ifEmpty { "download_${item.id}" })
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime.ifEmpty { "application/octet-stream" })
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LootBox")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create the file in Downloads.")
        try {
            val out = contentResolver.openOutputStream(uri) ?: throw IOException("Could not open the new file.")
            out.use { o -> part.inputStream().use { it.copyTo(o) } }
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            contentResolver.update(uri, published, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
        part.delete()
        retries.remove(item.id)
        val finished = item.copy(
            status = Status.COMPLETED,
            downloaded = size,
            total = size,
            speed = 0L,
            error = "",
            uri = uri.toString()
        )
        DownloadStore.put(finished)
        main.post { notifyDone(finished, true) }
    }

    private fun parseTotal(contentRange: String?, fallback: Long): Long {
        if (contentRange == null) return fallback
        val tail = contentRange.substringAfter('/', "")
        return tail.toLongOrNull() ?: fallback
    }

    private fun resolveName(conn: HttpURLConnection, url: String, type: String): String {
        var raw: String? = null
        val cd = conn.getHeaderField("Content-Disposition")
        if (cd != null) {
            val m = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)
            raw = m?.groupValues?.get(1)
        }
        if (raw.isNullOrBlank()) raw = Uri.parse(url).lastPathSegment
        val source = raw ?: ""
        val decoded = try {
            URLDecoder.decode(source, "UTF-8")
        } catch (e: Exception) {
            source
        }
        var name = decoded.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(120)
        if (name.isEmpty()) name = "download"
        if (!name.contains('.')) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type.substringBefore(';').trim())
            if (ext != null) name += ".$ext"
        }
        return name
    }

    /** Prefer the type from the file extension so Android does not add ".bin". */
    private fun mimeFor(name: String, serverType: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val byExt = if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) else null
        if (byExt != null) return byExt
        val t = serverType.substringBefore(';').trim()
        return if (t.isNotEmpty() && !t.equals("application/octet-stream", ignoreCase = true)) {
            t
        } else {
            "application/octet-stream"
        }
    }

    // ---------------------------------------------------------------- notifications

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(): Notification {
        val active = DownloadStore.all().filter { it.status == Status.DOWNLOADING }
        val first = active.firstOrNull()
        val title = if (active.size > 1) "Downloading ${active.size} files" else (first?.title ?: "LootBox")
        val text = when {
            first == null -> "Getting ready…"
            first.total > 0 -> "${first.progress}%  •  ${formatSize(first.downloaded)}"
            else -> formatSize(first.downloaded)
        }
        val b = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
        if (first != null && first.total > 0) b.setProgress(100, first.progress, false)
        else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun refreshNotification() {
        if (stopped) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, progressNotification())
    }

    private fun notifyDone(item: DownloadItem, ok: Boolean) {
        if (!Prefs.get(this).getBoolean(Prefs.NOTIFY, true)) return
        val n = NotificationCompat.Builder(this, CH_DONE)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(if (ok) "Download complete" else "Download failed")
            .setContentText(item.title)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), n)
    }
}
