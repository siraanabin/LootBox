package com.siraanabin.lootbox

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

    private const val API = "https://api.github.com/repos/siraanabin/LootBox/releases/latest"

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
                    info.code > BuildConfig.VERSION_CODE -> askUpdate(activity, info)
                    else -> if (!silent) toast(activity, "You have the latest version")
                }
            }
        }
    }

    private fun fetch(): Info? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "LootBox")
        try {
            if (c.responseCode != 200) return null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val code = o.getString("tag_name").substringAfterLast('-').toIntOrNull() ?: return null
            val assets = o.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Info(code, "2.$code", a.getString("browser_download_url"))
                }
            }
            return null
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
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Downloading update")
            .setMessage("0%")
            .setCancelable(false)
            .show()
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
                var last = 0
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                if (p != last) {
                                    last = p
                                    activity.runOnUiThread { dialog.setMessage("$p%") }
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
