package com.siraanabin.lootbox

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.app.PictureInPictureParams
import android.util.Rational
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var root: View
    private lateinit var playerView: PlayerView
    private lateinit var controls: View
    private lateinit var seek: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var gestureText: TextView
    private lateinit var brightnessBar: SeekBar
    private lateinit var volumeBar: SeekBar
    private lateinit var audio: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var userSeeking = false
    private var showRemaining = false
    private var locked = false
    private var fitMode = 0
    private var controlsVisible = true
    private var longPressing = false
    private var baseBrightness = 0.5f
    private var baseVolume = 0
    private var gestureStartPosition = 0L
    private var gestureStartX = 0f
    private var gestureStartY = 0f

    private val prefs by lazy { getSharedPreferences("lootbox_player", Context.MODE_PRIVATE) }
    private val updateUi = object : Runnable {
        override fun run() {
            val p = player ?: return
            val duration = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            if (!userSeeking) seek.progress = if (duration > 0) (p.currentPosition * 1000 / duration).toInt() else 0
            currentTime.text = formatTime(p.currentPosition)
            totalTime.text = if (showRemaining && duration > 0) "-${formatTime((duration - p.currentPosition).coerceAtLeast(0))}" else formatTime(duration)
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        root = findViewById(R.id.playerRoot)
        playerView = findViewById(R.id.playerView)
        controls = findViewById(R.id.controlsOverlay)
        seek = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        gestureText = findViewById(R.id.gestureText)
        brightnessBar = findViewById(R.id.brightnessBar)
        volumeBar = findViewById(R.id.volumeBar)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        findViewById<TextView>(R.id.titleText).text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val savedVolume = prefs.getInt("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume.coerceIn(0, maxVolume), 0)
        volumeBar.max = maxVolume
        volumeBar.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val savedBrightness = prefs.getInt("brightness", 65).coerceIn(5, 100)
        brightnessBar.max = 100
        brightnessBar.progress = savedBrightness
        setBrightness(savedBrightness)

        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        fitMode = prefs.getInt("fitMode", 0)
        applyFitMode()

        val p = ExoPlayer.Builder(this).build()
        player = p
        playerView.player = p
        p.setAudioAttributes(AudioAttributes.DEFAULT, true)
        p.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        val resume = prefs.getLong("pos_${uri.hashCode()}", 0L)
        if (resume > 0) p.seekTo(resume)
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (!isPlaying) savePosition(uri)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) scheduleHide()
            }
        })
        p.prepare()
        p.playWhenReady = true

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { userSeeking = true; showControls() }
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val d = p.duration
                    if (d > 0 && d != C.TIME_UNSET) currentTime.text = formatTime(d * progress / 1000)
                }
            }
            override fun onStopTrackingTouch(bar: SeekBar) {
                val d = p.duration
                if (d > 0 && d != C.TIME_UNSET) p.seekTo(d * bar.progress / 1000)
                userSeeking = false
                scheduleHide()
            }
        })
        brightnessBar.setOnSeekBarChangeListener(simpleSlider { value ->
            setBrightness(value)
            prefs.edit().putInt("brightness", value).apply()
        })
        volumeBar.setOnSeekBarChangeListener(simpleSlider { value ->
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            prefs.edit().putInt("volume", value).apply()
        })

        findViewById<View>(R.id.btnPlay).setOnClickListener { togglePlay(); scheduleHide() }
        findViewById<View>(R.id.btnBack).setOnClickListener { p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0)); showGesture("−10s") }
        findViewById<View>(R.id.btnForward).setOnClickListener { p.seekTo(p.currentPosition + 10_000); showGesture("+10s") }
        findViewById<View>(R.id.btnRemaining).setOnClickListener { showRemaining = !showRemaining; updateUi.run() }
        findViewById<View>(R.id.btnFit).setOnClickListener { fitMode = (fitMode + 1) % 3; applyFitMode(); prefs.edit().putInt("fitMode", fitMode).apply(); showGesture(arrayOf("FIT", "FILL", "CROP")[fitMode]) }
        findViewById<View>(R.id.btnLock).setOnClickListener { locked = !locked; showGesture(if (locked) "SCREEN LOCKED" else "UNLOCKED"); controls.visibility = if (locked) View.GONE else View.VISIBLE }
        findViewById<View>(R.id.btnSpeed).setOnClickListener { p.setPlaybackSpeed(if (p.playbackParameters.speed == 1f) 1.5f else 1f); showGesture("${p.playbackParameters.speed}×") }
        findViewById<View>(R.id.btnPip).setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (_: Exception) { showGesture("PiP unavailable") }
            } else showGesture("PiP not supported")
        }
        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!locked) toggleControls()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val forward = e.x > playerView.width / 2f
                p.seekTo((p.currentPosition + if (forward) 10_000 else -10_000).coerceAtLeast(0))
                showGesture(if (forward) "+10s" else "−10s")
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (!locked) {
                    longPressing = true
                    p.setPlaybackSpeed(2f)
                    showGesture("2× SPEED")
                }
            }
            override fun onDown(e: MotionEvent): Boolean {
                gestureStartPosition = p.currentPosition
                gestureStartX = e.x
                gestureStartY = e.y
                baseBrightness = brightnessBar.progress / 100f
                baseVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (locked || e1 == null) return false
                val deltaX = e1.x - e2.x
                val deltaY = e1.y - e2.y
                if (abs(deltaX) > abs(deltaY)) {
                    val target = (gestureStartPosition + (deltaX / playerView.width * 120_000)).toLong().coerceAtLeast(0)
                    p.seekTo(target)
                    showGesture(formatTime(target))
                } else if (e1.x < playerView.width / 2f) {
                    val value = (baseBrightness * 100 - deltaY / playerView.height * 100).toInt().coerceIn(5, 100)
                    brightnessBar.progress = value
                    setBrightness(value)
                    prefs.edit().putInt("brightness", value).apply()
                    showGesture("☀  $value%")
                } else {
                    val value = (baseVolume - deltaY / playerView.height * maxVolume).toInt().coerceIn(0, maxVolume)
                    volumeBar.progress = value
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                    prefs.edit().putInt("volume", value).apply()
                    showGesture("VOL  ${value * 100 / maxVolume}%")
                }
                return true
            }
        })
        playerView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (longPressing) { longPressing = false; p.setPlaybackSpeed(1f); showGesture("1×") }
            }
            true
        }
        handler.post(updateUi)
    }

    private fun simpleSlider(change: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) change(value) }
        override fun onStartTrackingTouch(bar: SeekBar) { showControls() }
        override fun onStopTrackingTouch(bar: SeekBar) { scheduleHide() }
    }

    private fun setBrightness(value: Int) {
        val params = window.attributes
        params.screenBrightness = (value / 100f).coerceIn(0.05f, 1f)
        window.attributes = params
    }

    private fun applyFitMode() {
        playerView.resizeMode = when (fitMode) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun togglePlay() {
        val p = player ?: return
        p.playWhenReady = !p.isPlaying
        findViewById<TextView>(R.id.btnPlay).text = if (p.isPlaying) "Ⅱ" else "▶"
    }

    private fun toggleControls() {
        if (locked) return
        if (controlsVisible) controls.visibility = View.GONE else showControls()
        controlsVisible = !controlsVisible
    }

    private fun showControls() {
        if (locked) return
        controls.visibility = View.VISIBLE
        controlsVisible = true
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3000)
    }

    private val hideControls = Runnable {
        if (player?.isPlaying == true && !locked) {
            controls.visibility = View.GONE
            controlsVisible = false
        }
    }

    private fun showGesture(message: String) {
        gestureText.text = message
        gestureText.visibility = View.VISIBLE
        handler.removeCallbacks(hideGesture)
        handler.postDelayed(hideGesture, 900)
        showControls()
    }

    private val hideGesture = Runnable { gestureText.visibility = View.GONE }

    private fun savePosition(uri: String) {
        prefs.edit().putLong("pos_${uri.hashCode()}", player?.currentPosition ?: 0L).apply()
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0) / 1000).toInt()
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    override fun onUserLeaveHint() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && player?.isPlaying == true) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (_: Exception) { }
        }
        super.onUserLeaveHint()
    }

    override fun onStop() {
        val uri = intent.getStringExtra(EXTRA_URI)
        if (uri != null) savePosition(uri)
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
    }
}
