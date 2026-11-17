package com.siraanabin.lootbox

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** In-app updates (from GitHub Releases) and the yt-dlp engine update. */
object Updater {

    private const val MANIFEST = "https://raw.githubusercontent.com/siraanabin/LootBox/fix/top-gap-only/version.json"

    private class Info(val code: Int, val name: String, val apkUrl: String)

    // ------------------------------------------------------------ app update

    fun check(activity: AppCompatActivity, silent: Boolean) {
        thread(name = "update-check") {
            val info = try {
                fetch()
            } catch (e: Exception) {
                null
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                when {
                    info == null -> if (!silent) toast(activity, "Could not check for updates")
                    isNewer(info) -> askUpdate(activity, info)
                    else -> if (!silent) toast(activity, "Latest check: installed \${BuildConfig.VERSION_NAME} (\${BuildConfig.VERSION_CODE}), server \${info.name} (\${info.code})")
                }
            }
        }
    }

    // Android's integer versionCode is the authoritative update comparison.
    private fun isNewer(info: Info): Boolean =
        info.code > BuildConfig.VERSION_CODE

    private fun fetch(): Info? {
        // Bypass GitHub raw/CDN caches so the app always reads the newest release manifest.
        val manifestUrl = "$MANIFEST?cacheBust=${System.currentTimeMillis()}"
        val c = URL(manifestUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "LootBox")
        c.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
        c.setRequestProperty("Pragma", "no-cache")
        try {
            if (c.responseCode != 200) return null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val code = o.optInt("versionCode", -1)
            val name = o.optString("versionName")
            val apkUrl = o.optString("apkUrl")
            if (code <= 0 || name.isBlank() || apkUrl.isBlank()) return null
            return Info(code, name, apkUrl)
        } finally {
            c.disconnect()
        }
    }

    private fun askUpdate(activity: AppCompatActivity, info: Info) {
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage("LootBox ${info.name} is ready. You have ${BuildConfig.VERSION_NAME}.")
            .setPositiveButton("Update") { _, _ -> download(activity, info) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun download(activity: AppCompatActivity, info: Info) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            toast(activity, "Allow LootBox to install apps, then press Update again")
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_progress, null)
        val progressBar = view.findViewById<ProgressBar>(R.id.updateProgressBar)
        val percentText = view.findViewById<TextView>(R.id.updateProgressPercent)
        val statusText = view.findViewById<TextView>(R.id.updateProgressStatus)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        view.alpha = 0f
        view.scaleX = 0.96f
        view.scaleY = 0.96f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()

        thread(name = "update-download") {
            var error: String? = null
            val file = File(File(activity.cacheDir, "updates").apply { mkdirs() }, "LootBox.apk")
            try {
                val c = URL(info.apkUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "LootBox")
                if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                var done = 0L
                var last = -1
                activity.runOnUiThread {
                    progressBar.isIndeterminate = total <= 0
                    if (total <= 0) {
                        percentText.text = "…"
                        statusText.text = "Downloading update"
                    }
                }
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (p != last) {
                                    last = p
                                    activity.runOnUiThread {
                                        progressBar.setProgress(p, true)
                                        percentText.text = "$p%"
                                        statusText.text = "Downloading LootBox ${info.name}"
                                    }
                                }
                            }
                        }
                    }
                }
                c.disconnect()
            } catch (e: Exception) {
                error = e.message ?: "failed"
            }
            activity.runOnUiThread {
                dialog.dismiss()
                if (error != null) {
                    toast(activity, "Update download failed: $error")
                } else {
                    install(activity, file)
                }
            }
        }
    }

    private fun install(activity: AppCompatActivity, file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(intent)
    }

    // ------------------------------------------------------------ yt-dlp engine update

    /** Updates yt-dlp inside the app. Uses reflection so it does not depend on the library's exact API. */
    fun updateEngine(activity: AppCompatActivity) {
        thread(name = "engine-update") {
            val msg = try {
                if (!LootBoxApp.awaitEngine()) throw IllegalStateException("Engine is not ready")
                val yd = YoutubeDL.getInstance()
                val m = yd.javaClass.methods.first { it.name == "updateYoutubeDL" }
                val types = m.parameterTypes
                val args = arrayOfNulls<Any>(types.size)
                args[0] = activity.applicationContext
                if (types.size > 1) args[1] = stableChannel(types[1])
                val res = m.invoke(yd, *args)
                when (res?.toString()) {
                    null -> "Engine updated"
                    else -> if (res.toString().contains("ALREADY", true)) "Engine is already up to date"
                    else "Engine updated"
                }
            } catch (e: Throwable) {
                val cause = e.cause ?: e
                "Engine update failed: ${cause.message ?: cause.javaClass.simpleName}"
            }
            activity.runOnUiThread { toast(activity, msg) }
        }
    }

    private fun stableChannel(type: Class<*>): Any? {
        // Kotlin "object STABLE" inside the channel class
        try {
            return Class.forName(type.name + "\$STABLE").getField("INSTANCE").get(null)
        } catch (e: Throwable) {
        }
        // Companion property or static field
        try {
            return type.getField("STABLE").get(null)
        } catch (e: Throwable) {
        }
        try {
            val comp = type.getField("Companion").get(null)
            return comp.javaClass.getMethod("getSTABLE").invoke(comp)
        } catch (e: Throwable) {
        }
        throw IllegalStateException("Update channel not found")
    }
}
