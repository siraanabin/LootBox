package com.siraanabin.lootbox

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlin.concurrent.thread

class LootBoxApp : Application() {

    companion object {
        @Volatile var engineReady = false
        @Volatile var engineError: String = ""
        private val lock = java.lang.Object()

        /** Blocks until the yt-dlp engine finished starting. Returns true when it is usable. */
        fun awaitEngine(timeoutMs: Long = 60_000): Boolean {
            val end = System.currentTimeMillis() + timeoutMs
            synchronized(lock) {
                while (!engineReady && engineError.isEmpty() && System.currentTimeMillis() < end) {
                    lock.wait(500)
                }
            }
            return engineReady
        }

        fun markEngine(ok: Boolean, err: String = "") {
            synchronized(lock) {
                engineReady = ok
                engineError = err
                lock.notifyAll()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        DownloadStore.init(this)
        thread(name = "ytdlp-init") {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
                markEngine(true)
            } catch (e: Throwable) {
                Log.e("LootBox", "yt-dlp init failed", e)
                markEngine(false, e.message ?: "yt-dlp failed to start")
            }
        }
        val dark = Prefs.get(this).getBoolean(Prefs.DARK, true)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
