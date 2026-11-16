package com.siraanabin.lootbox

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        findViewById<TextView>(R.id.titleText).text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        val view = findViewById<PlayerView>(R.id.playerView)
        player = ExoPlayer.Builder(this).build().also { p ->
            view.player = p
            p.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            p.prepare()
            p.playWhenReady = true
            p.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    override fun onStop() {
        player?.playWhenReady = false
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
    }
}