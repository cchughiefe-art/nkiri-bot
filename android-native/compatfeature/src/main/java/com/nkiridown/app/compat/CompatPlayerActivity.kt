package com.nkiridown.app.compat

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.nkiridown.app.CompatibilityModuleManager
import com.nkiridown.app.PlaybackRecord
import com.nkiridown.app.PlaybackStore
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class CompatPlayerActivity : ComponentActivity() {
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var surface: SurfaceView? = null
    private var playPause: Button? = null
    private var seekBar: SeekBar? = null
    private var timeLabel: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private val playbackStore by lazy { PlaybackStore(this) }

    private var mediaUrl: String = ""
    private var mediaId: String = ""
    private var title: String = "TheNkiri"
    private var poster: String? = null
    private var provider: String = "moviex"
    private var type: String = "movie"
    private var season: Int? = null
    private var episode: Int? = null
    private var episodeLabel: String? = null
    private var subtitleUrl: String? = null
    private var userSeeking = false

    private val ticker =
        object : Runnable {
            override fun run() {
                val mp = player

                if (mp != null) {
                    val length = mp.length.coerceAtLeast(0L)
                    val current = mp.time.coerceAtLeast(0L)

                    if (!userSeeking && length > 0L) {
                        seekBar?.progress =
                            (
                                current.toDouble() /
                                    length.toDouble() *
                                    1000.0
                                )
                                .toInt()
                                .coerceIn(0, 1000)
                    }

                    playPause?.text =
                        if (mp.isPlaying) "Pause" else "Play"

                    timeLabel?.text =
                        "${formatTime(current)} / ${formatTime(length)}"

                    saveProgress(false)
                }

                handler.postDelayed(this, 1000L)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaUrl =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_URL
            )
                ?: run {
                    finish()
                    return
                }

        mediaId =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_MEDIA_ID
            ).orEmpty()

        title =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_TITLE
            ) ?: "TheNkiri"

        poster =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_POSTER
            )

        provider =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_PROVIDER
            ) ?: "moviex"

        type =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_TYPE
            ) ?: "movie"

        season =
            intent.getIntExtra(
                CompatibilityModuleManager.EXTRA_SEASON,
                0
            ).takeIf { it > 0 }

        episode =
            intent.getIntExtra(
                CompatibilityModuleManager.EXTRA_EPISODE,
                0
            ).takeIf { it > 0 }

        episodeLabel =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_EPISODE_LABEL
            )

        subtitleUrl =
            intent.getStringExtra(
                CompatibilityModuleManager.EXTRA_SUBTITLE_URL
            )

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        hideSystemUi()
        buildUi()
        startPlayback()
        handler.post(ticker)

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun buildUi() {
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }

        val video =
            SurfaceView(this)

        surface = video

        root.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val topBar =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 10, 14, 10)
                setBackgroundColor(0x77000000)
            }

        val back =
            Button(this).apply {
                text = "Back"
                setOnClickListener { finish() }
            }

        val titleView =
            TextView(this).apply {
                text = this@CompatPlayerActivity.title
                setTextColor(Color.WHITE)
                textSize = 16f
                maxLines = 1
                setPadding(12, 0, 0, 0)
            }

        topBar.addView(back)
        topBar.addView(
            titleView,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val controls =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10, 8, 10, 8)
                setBackgroundColor(0xAA111318.toInt())
            }

        val rewind =
            Button(this).apply {
                text = "-10"
                setOnClickListener {
                    val mp = player ?: return@setOnClickListener
                    mp.time =
                        (mp.time - 10_000L)
                            .coerceAtLeast(0L)
                }
            }

        val toggle =
            Button(this).apply {
                text = "Pause"
                setOnClickListener {
                    val mp = player ?: return@setOnClickListener

                    if (mp.isPlaying) {
                        mp.pause()
                    } else {
                        mp.play()
                    }
                }
            }

        playPause = toggle

        val forward =
            Button(this).apply {
                text = "+10"
                setOnClickListener {
                    val mp = player ?: return@setOnClickListener
                    val length = mp.length.coerceAtLeast(0L)

                    mp.time =
                        if (length > 0L) {
                            (mp.time + 10_000L)
                                .coerceAtMost(length)
                        } else {
                            mp.time + 10_000L
                        }
                }
            }

        val seek =
            SeekBar(this).apply {
                max = 1000

                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) = Unit

                        override fun onStartTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                            userSeeking = true
                        }

                        override fun onStopTrackingTouch(
                            seekBar: SeekBar?
                        ) {
                            val mp = player
                            val length =
                                mp?.length
                                    ?.coerceAtLeast(0L)
                                    ?: 0L

                            if (mp != null && length > 0L) {
                                mp.time =
                                    (
                                        length.toDouble() *
                                            (
                                                (seekBar?.progress ?: 0) /
                                                    1000.0
                                                )
                                        )
                                        .toLong()
                            }

                            userSeeking = false
                        }
                    }
                )
            }

        seekBar = seek

        val timing =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                text = "00:00 / 00:00"
                textSize = 12f
                setPadding(8, 0, 0, 0)
            }

        timeLabel = timing

        controls.addView(rewind)
        controls.addView(toggle)
        controls.addView(forward)

        controls.addView(
            seek,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        controls.addView(timing)

        root.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }

    private fun startPlayback() {
        val vlc =
            LibVLC(
                this,
                arrayListOf(
                    "--network-caching=1800",
                    "--file-caching=800"
                )
            )

        libVlc = vlc

        val mp =
            MediaPlayer(vlc)

        player = mp

        surface?.let { video ->
            mp.vlcVout.setVideoView(video)
            mp.vlcVout.attachViews()
        }

        mp.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EndReached ->
                    saveProgress(true)

                MediaPlayer.Event.EncounteredError ->
                    runOnUiThread {
                        timeLabel?.text =
                            "Playback failed"
                    }
            }
        }

        val media =
            Media(
                vlc,
                Uri.parse(mediaUrl)
            )

        media.setHWDecoderEnabled(
            true,
            false
        )

        media.addOption(":network-caching=1800")
        media.addOption(":file-caching=800")

        mp.media = media
        media.release()
        mp.play()

        val resume =
            playbackStore
                .find(
                    mediaId,
                    season,
                    episode
                )
                ?.takeIf {
                    !it.completed &&
                        it.positionMs > 0L
                }
                ?.positionMs
                ?: 0L

        if (resume > 0L) {
            handler.postDelayed(
                {
                    player?.time = resume
                },
                500L
            )
        }

        subtitleUrl
            ?.takeIf {
                it.startsWith("http://") ||
                    it.startsWith("https://")
            }
            ?.let { subtitle ->
                handler.postDelayed(
                    {
                        runCatching {
                            player?.addSlave(
                                Media.Slave.Type.Subtitle,
                                Uri.parse(subtitle),
                                true
                            )
                        }
                    },
                    800L
                )
            }
    }

    private fun saveProgress(
        completed: Boolean
    ) {
        if (mediaId.isBlank()) return

        val mp = player ?: return

        val duration =
            mp.length
                .coerceAtLeast(0L)

        val position =
            if (completed && duration > 0L) {
                duration
            } else {
                mp.time
                    .coerceAtLeast(0L)
            }

        playbackStore.save(
            PlaybackRecord(
                mediaId = mediaId,
                title = title,
                poster = poster,
                provider = provider,
                type = type,
                season = season,
                episode = episode,
                episodeLabel = episodeLabel,
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
                completed =
                    completed ||
                        (
                            duration > 0L &&
                                position >=
                                (duration * 0.95)
                                    .toLong()
                            )
            )
        )
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (
            android.os.Build.VERSION.SDK_INT >= 26 &&
            player?.isPlaying == true
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(
                        Rational(16, 9)
                    )
                    .build()
            )
        }
    }

    override fun onPause() {
        saveProgress(false)

        if (!isInPictureInPictureMode) {
            player?.pause()
        }

        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveProgress(false)

        player?.vlcVout?.detachViews()
        player?.stop()
        player?.release()
        player = null

        libVlc?.release()
        libVlc = null

        super.onDestroy()

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds =
            ms.coerceAtLeast(0L) /
                1000L

        val hours =
            totalSeconds /
                3600L

        val minutes =
            (
                totalSeconds %
                    3600L
                ) /
                60L

        val seconds =
            totalSeconds %
                60L

        return if (hours > 0L) {
            "%d:%02d:%02d".format(
                hours,
                minutes,
                seconds
            )
        } else {
            "%02d:%02d".format(
                minutes,
                seconds
            )
        }
    }
}
