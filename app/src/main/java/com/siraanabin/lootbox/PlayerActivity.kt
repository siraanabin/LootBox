package com.siraanabin.lootbox

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var controls: View
    private lateinit var unlockOverlay: View
    private lateinit var seek: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var gestureText: TextView
    private lateinit var brightnessBar: SeekBar
    private lateinit var volumeBar: SeekBar
    private lateinit var playButton: TextView
    private lateinit var speedButton: TextView
    private lateinit var fitButton: TextView
    private lateinit var audio: AudioManager

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("lootbox_player", Context.MODE_PRIVATE) }
    private var userSeeking = false
    private var showRemaining = false
    private var locked = false
    private var fitMode = 0
    private var controlsVisible = true
    private var longPressing = false
    private var speedBeforeLongPress = 1f
    private var baseBrightness = 0.65f
    private var baseVolume = 0
    private var gestureStartPosition = 0L
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureMode = GestureMode.NONE
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private enum class GestureMode { NONE, SEEK, BRIGHTNESS, VOLUME }

    private val updateUi = object : Runnable {
        override fun run() {
            val p = player ?: return
            val duration = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            if (!userSeeking) {
                seek.progress = if (duration > 0) (p.currentPosition * 1000 / duration).toInt().coerceIn(0, 1000) else 0
            }
            currentTime.text = formatTime(p.currentPosition)
            totalTime.text = if (showRemaining && duration > 0) "-${formatTime((duration - p.currentPosition).coerceAtLeast(0))}" else formatTime(duration)
            if (p.currentPosition > 0L) savePosition()
            handler.postDelayed(this, 500)
        }
    }

    private val hideControls = Runnable {
        if (player?.isPlaying == true && !locked && !userSeeking) hideControlsNow()
    }
    private val hideGesture = Runnable { gestureText.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        playerView = findViewById(R.id.playerView)
        controls = findViewById(R.id.controlsOverlay)
        unlockOverlay = findViewById(R.id.unlockOverlay)
        seek = findViewById(R.id.seekBar)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        gestureText = findViewById(R.id.gestureText)
        brightnessBar = findViewById(R.id.brightnessBar)
        volumeBar = findViewById(R.id.volumeBar)
        playButton = findViewById(R.id.btnPlay)
        speedButton = findViewById(R.id.btnSpeed)
        fitButton = findViewById(R.id.btnFit)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        findViewById<TextView>(R.id.titleText).text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        setupSliders()
        setupPlayer(uri)
        setupButtons()
        setupGestures()
        updateFitLabel()
        updateSpeedLabel()
        handler.post(updateUi)
        scheduleHide()
    }

    private fun setupSliders() {
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val savedVolume = prefs.getInt("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume.coerceIn(0, maxVolume), 0)
        volumeBar.max = maxVolume
        volumeBar.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

        val savedBrightness = prefs.getInt("brightness", 65).coerceIn(5, 100)
        brightnessBar.max = 100
        brightnessBar.progress = savedBrightness
        setBrightness(savedBrightness)

        brightnessBar.setOnSeekBarChangeListener(simpleSlider { value ->
            setBrightness(value)
            prefs.edit().putInt("brightness", value).apply()
        })
        volumeBar.setOnSeekBarChangeListener(simpleSlider { value ->
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
            prefs.edit().putInt("volume", value).apply()
        })
    }

    private fun setupPlayer(uri: String) {
        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        fitMode = prefs.getInt("fitMode", 0).coerceIn(0, 2)
        applyFitMode()

        val p = ExoPlayer.Builder(this).build()
        player = p
        playerView.player = p
        p.setAudioAttributes(AudioAttributes.DEFAULT, true)
        p.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        val resume = prefs.getLong(positionKey(uri), 0L).coerceAtLeast(0L)
        if (resume > 0L) p.seekTo(resume)

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    scheduleHide()
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    showControls()
                }
                updatePlayIcon()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updatePlayIcon()
                    scheduleHide()
                }
                if (playbackState == Player.STATE_ENDED) showControls()
            }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                updateSpeedLabel()
            }
        })
        p.prepare()
        p.playWhenReady = true
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btnPlay).setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying || p.playWhenReady) p.pause() else p.play()
            showControls()
        }
        findViewById<View>(R.id.btnBack).setOnClickListener { seekRelative(-10_000L); showGesture("−10 seconds") }
        findViewById<View>(R.id.btnForward).setOnClickListener { seekRelative(10_000L); showGesture("+10 seconds") }
        findViewById<View>(R.id.btnRemaining).setOnClickListener {
            showRemaining = !showRemaining
            updateUi.run()
            scheduleHide()
        }
        fitButton.setOnClickListener {
            fitMode = (fitMode + 1) % 3
            applyFitMode()
            prefs.edit().putInt("fitMode", fitMode).apply()
            updateFitLabel()
            showGesture(arrayOf("FIT", "FILL", "CROP")[fitMode])
        }
        findViewById<View>(R.id.btnLock).setOnClickListener { lockScreen() }
        findViewById<View>(R.id.btnUnlock).setOnClickListener { unlockScreen() }
        speedButton.setOnClickListener {
            val p = player ?: return@setOnClickListener
            val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f)
            val current = p.playbackParameters.speed
            val nextSpeed = speeds.firstOrNull { it > current + 0.01f } ?: speeds.first()
            // Media3 applies speed in-place and preserves the playback timeline position.
            p.setPlaybackSpeed(nextSpeed)
            updateSpeedLabel()
            showGesture("${nextSpeed}×")
            scheduleHide()
        }
        findViewById<View>(R.id.btnPip).setOnClickListener { enterPip() }
    }

    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                gestureStartPosition = player?.currentPosition ?: 0L
                gestureStartX = e.x
                gestureStartY = e.y
                baseBrightness = brightnessBar.progress / 100f
                baseVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                gestureMode = GestureMode.NONE
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) unlockOverlay.visibility = View.VISIBLE else toggleControls()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val forward = e.x > playerView.width / 2f
                seekRelative(if (forward) 10_000L else -10_000L)
                showGesture(if (forward) "+10 seconds" else "−10 seconds")
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (locked) return
                val p = player ?: return
                longPressing = true
                speedBeforeLongPress = p.playbackParameters.speed
                p.setPlaybackSpeed(2f)
                showGesture("2× SPEED")
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (locked || e1 == null || longPressing) return locked
                val dx = e2.x - gestureStartX
                val dy = e2.y - gestureStartY
                if (gestureMode == GestureMode.NONE) {
                    if (abs(dx) < touchSlop && abs(dy) < touchSlop) return true
                    gestureMode = if (abs(dx) > abs(dy)) GestureMode.SEEK
                    else if (gestureStartX < playerView.width / 2f) GestureMode.BRIGHTNESS
                    else GestureMode.VOLUME
                    showControls()
                }
                when (gestureMode) {
                    GestureMode.SEEK -> {
                        val duration = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
                        val delta = (dx / playerView.width.coerceAtLeast(1) * 120_000L).toLong()
                        val target = (gestureStartPosition + delta).coerceIn(0L, duration)
                        player?.seekTo(target)
                        showGesture(formatTime(target))
                    }
                    GestureMode.BRIGHTNESS -> {
                        val value = (baseBrightness * 100f - dy / playerView.height.coerceAtLeast(1) * 100f).toInt().coerceIn(5, 100)
                        brightnessBar.progress = value
                        setBrightness(value)
                        prefs.edit().putInt("brightness", value).apply()
                        showGesture("☼  $value%")
                    }
                    GestureMode.VOLUME -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val value = (baseVolume - dy / playerView.height.coerceAtLeast(1) * max).toInt().coerceIn(0, max)
                        volumeBar.progress = value
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                        prefs.edit().putInt("volume", value).apply()
                        showGesture("VOL  ${value * 100 / max}%")
                    }
                    GestureMode.NONE -> Unit
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (longPressing) {
                    longPressing = false
                    player?.setPlaybackSpeed(speedBeforeLongPress)
                    showGesture("${speedBeforeLongPress}×")
                }
                if (gestureMode != GestureMode.NONE) scheduleHide()
                gestureMode = GestureMode.NONE
            }
            true
        }
    }

    private fun simpleSlider(change: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { if (fromUser) change(value) }
        override fun onStartTrackingTouch(bar: SeekBar) {
            handler.removeCallbacks(hideControls)
            showControls()
        }
        override fun onStopTrackingTouch(bar: SeekBar) { scheduleHide() }
    }

    private fun setBrightness(value: Int) {
        val params = window.attributes
        params.screenBrightness = (value / 100f).coerceIn(0.05f, 1f)
        window.attributes = params
    }

    private fun applyFitMode() {
        playerView.resizeMode = when (fitMode) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun updateFitLabel() { fitButton.text = arrayOf("FIT", "FILL", "CROP")[fitMode] }

    private fun updateSpeedLabel() {
        val speed = player?.playbackParameters?.speed ?: 1f
        speedButton.text = if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"
    }

    private fun updatePlayIcon() { playButton.text = if (player?.isPlaying == true) "Ⅱ" else "▶" }

    private fun seekRelative(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, duration))
        scheduleHide()
    }

    private fun toggleControls() {
        if (locked) return
        if (controlsVisible) hideControlsNow() else showControls()
    }

    private fun showControls() {
        if (locked) return
        handler.removeCallbacks(hideControls)
        controlsVisible = true
        if (controls.visibility != View.VISIBLE) {
            controls.alpha = 0f
            controls.translationY = 8f
            controls.visibility = View.VISIBLE
        }
        controls.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        if (player?.isPlaying == true) scheduleHide()
    }

    private fun hideControlsNow() {
        handler.removeCallbacks(hideControls)
        controlsVisible = false
        controls.animate().alpha(0f).translationY(8f).setDuration(160L).withEndAction {
            if (!controlsVisible && !locked) controls.visibility = View.GONE
        }.start()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (!locked && player?.isPlaying == true) handler.postDelayed(hideControls, 3_500L)
    }

    private fun lockScreen() {
        locked = true
        handler.removeCallbacks(hideControls)
        controls.animate().cancel()
        controls.visibility = View.GONE
        controlsVisible = false
        unlockOverlay.visibility = View.VISIBLE
        showGesture("SCREEN LOCKED")
    }

    private fun unlockScreen() {
        locked = false
        unlockOverlay.visibility = View.GONE
        showControls()
        showGesture("UNLOCKED")
    }

    private fun showGesture(message: String) {
        gestureText.text = message
        gestureText.visibility = View.VISIBLE
        gestureText.alpha = 1f
        handler.removeCallbacks(hideGesture)
        handler.postDelayed(hideGesture, 850L)
    }

    private fun savePosition() {
        val uri = intent.getStringExtra(EXTRA_URI) ?: return
        val position = player?.currentPosition ?: return
        prefs.edit().putLong(positionKey(uri), position).apply()
    }

    private fun positionKey(uri: String) = "pos_${uri.hashCode()}"

    private fun formatTime(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            } catch (_: Exception) { showGesture("PiP unavailable") }
        } else showGesture("PiP not supported")
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= 26 && player?.isPlaying == true) enterPip()
        super.onUserLeaveHint()
    }

    override fun onPause() { savePosition(); super.onPause() }

    override fun onStop() {
        savePosition()
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        savePosition()
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
