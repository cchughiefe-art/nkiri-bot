package com.nkiridown.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerActivity : ComponentActivity() {
    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private var surface: SurfaceView? = null
    private var saveJob: Job? = null

    private val playbackStore by lazy { PlaybackStore(this) }

    private var mediaId: String = ""
    private var title: String = ""
    private var poster: String? = null
    private var provider: String = "moviex"
    private var type: String = "movie"
    private var season: Int? = null
    private var episode: Int? = null
    private var episodeLabel: String? = null
    private var subtitleUrl: String? = null

    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var titleText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var userSeeking = false

    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgressUi()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        mediaId = intent.getStringExtra(EXTRA_MEDIA_ID).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE) ?: "TheNkiri"
        poster = intent.getStringExtra(EXTRA_POSTER)
        provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "moviex"
        type = intent.getStringExtra(EXTRA_TYPE) ?: "movie"
        season = intent.getIntExtra(EXTRA_SEASON, 0).takeIf { it > 0 }
        episode = intent.getIntExtra(EXTRA_EPISODE, 0).takeIf { it > 0 }
        episodeLabel = intent.getStringExtra(EXTRA_EPISODE_LABEL)
        subtitleUrl = intent.getStringExtra(EXTRA_SUBTITLE_URL)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        buildPlayerUi()

        val vlc = LibVLC(
            this,
            arrayListOf(
                "--network-caching=1800",
                "--file-caching=800",
                "--clock-jitter=0",
                "--clock-synchro=0"
            )
        )
        libVLC = vlc

        val mp = MediaPlayer(vlc)
        player = mp

        surface?.let { video ->
            mp.vlcVout.setVideoView(video)
            mp.vlcVout.attachViews()
        }

        mp.setEventListener { event ->
            runOnUiThread {
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        playButton.text = "Pause"
                    }
                    MediaPlayer.Event.Paused,
                    MediaPlayer.Event.Stopped -> {
                        playButton.text = "Play"
                    }
                    MediaPlayer.Event.EndReached -> {
                        playButton.text = "Play"
                        saveProgress(completed = true)
                        tryAutoNext()
                    }
                }
            }
        }

        startMedia(url)

        playbackStore.find(mediaId, season, episode)
            ?.takeIf { !it.completed && it.positionMs > 0L }
            ?.let { record ->
                handler.postDelayed({
                    player?.time = record.positionMs
                }, 650)
            }

        playButton.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
        }

        findViewById<Button>(ID_BACK_10).setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.time = (p.time - 10_000L).coerceAtLeast(0L)
        }

        findViewById<Button>(ID_FORWARD_10).setOnClickListener {
            val p = player ?: return@setOnClickListener
            val length = p.length.takeIf { it > 0L } ?: Long.MAX_VALUE
            p.time = (p.time + 10_000L).coerceAtMost(length)
        }

        findViewById<Button>(ID_CLOSE).setOnClickListener {
            finish()
        }

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val p = player
                    val duration = p?.length ?: 0L
                    val progress = seekBar?.progress ?: 0
                    if (p != null && duration > 0L) {
                        p.time = (duration * progress / 1000L)
                    }
                    userSeeking = false
                }

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) = Unit
            }
        )

        handler.post(progressTicker)

        saveJob = lifecycleScope.launch {
            while (isActive) {
                delay(5_000)
                saveProgress(completed = false)
            }
        }
    }

    private fun buildPlayerUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val video = SurfaceView(this)
        surface = video
        root.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0x66000000)
        }

        val close = Button(this).apply {
            id = ID_CLOSE
            text = "Back"
        }

        titleText = TextView(this).apply {
            text = this@PlayerActivity.title
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setPadding(16, 0, 0, 0)
            maxLines = 1
        }

        top.addView(close)
        top.addView(
            titleText,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        root.addView(
            top,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 18)
            setBackgroundColor(0x77000000)
        }

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        positionText = TextView(this).apply {
            text = "00:00"
            setTextColor(android.graphics.Color.WHITE)
        }

        durationText = TextView(this).apply {
            text = "00:00"
            setTextColor(android.graphics.Color.LTGRAY)
        }

        seekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
        }

        timeRow.addView(positionText)
        timeRow.addView(
            seekBar,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        timeRow.addView(durationText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val back10 = Button(this).apply {
            id = ID_BACK_10
            text = "-10s"
        }

        playButton = Button(this).apply {
            text = "Pause"
        }

        val forward10 = Button(this).apply {
            id = ID_FORWARD_10
            text = "+10s"
        }

        controls.addView(back10)
        controls.addView(playButton)
        controls.addView(forward10)

        bottom.addView(timeRow)
        bottom.addView(controls)

        root.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }

    private fun startMedia(url: String) {
        val vlc = libVLC ?: return
        val mp = player ?: return

        val media = Media(vlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=1800")

        subtitleUrl
            ?.takeIf {
                it.startsWith("http://") ||
                    it.startsWith("https://")
            }
            ?.let {
                media.addOption(":sub-file=$it")
            }

        mp.media = media
        media.release()
        mp.play()
    }

    private fun updateProgressUi() {
        val p = player ?: return
        val duration = p.length.takeIf { it > 0L } ?: 0L
        val position = p.time.coerceAtLeast(0L)

        if (!userSeeking && duration > 0L) {
            seekBar.progress =
                ((position.toDouble() / duration.toDouble()) * 1000.0)
                    .toInt()
                    .coerceIn(0, 1000)
        }

        positionText.text = formatTime(position)
        durationText.text = formatTime(duration)
    }

    private fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L

        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    private fun saveProgress(completed: Boolean) {
        val p = player ?: return
        if (mediaId.isBlank()) return

        val duration = p.length.takeIf { it > 0L } ?: 0L
        val position =
            if (completed && duration > 0L) duration
            else p.time.coerceAtLeast(0L)

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
                                position >= (duration * 0.95).toLong()
                            )
            )
        )
    }

    private fun tryAutoNext() {
        val s = season ?: return
        val e = episode ?: return
        if (type != "series") return

        lifecycleScope.launch {
            runCatching {
                val api = NkiriApi(LocalStore(this@PlayerActivity).apiBaseUrl())
                val nextEpisode = e + 1
                val response = api.sources(
                    id = mediaId,
                    season = s,
                    episode = nextEpisode
                )
                val next =
                    response.selected
                        ?: response.sources.firstOrNull()
                        ?: return@runCatching

                if (next.external || next.url.isNullOrBlank()) {
                    return@runCatching
                }

                episode = nextEpisode
                episodeLabel =
                    "S${s.toString().padStart(2, '0')}E${nextEpisode.toString().padStart(2, '0')}"

                startMedia(next.url!!)
            }
        }
    }

    private fun hideSystemUi() {
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
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    override fun onPause() {
        saveProgress(completed = false)
        super.onPause()
    }

    override fun onStop() {
        if (!isInPictureInPictureMode) {
            player?.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        saveProgress(completed = false)
        handler.removeCallbacks(progressTicker)
        saveJob?.cancel()

        player?.vlcVout?.detachViews()
        player?.stop()
        player?.release()
        player = null

        libVLC?.release()
        libVLC = null

        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_MEDIA_ID = "mediaId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_POSTER = "poster"
        private const val EXTRA_PROVIDER = "provider"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_SEASON = "season"
        private const val EXTRA_EPISODE = "episode"
        private const val EXTRA_EPISODE_LABEL = "episodeLabel"
        private const val EXTRA_SUBTITLE_URL = "subtitleUrl"

        private const val ID_CLOSE = 3001
        private const val ID_BACK_10 = 3002
        private const val ID_FORWARD_10 = 3003

        fun intent(
            context: Context,
            url: String,
            mediaId: String,
            title: String,
            poster: String?,
            provider: String,
            type: String,
            season: Int?,
            episode: Int?,
            episodeLabel: String?,
            subtitleUrl: String?
        ): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_MEDIA_ID, mediaId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_POSTER, poster)
                .putExtra(EXTRA_PROVIDER, provider)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_SEASON, season ?: 0)
                .putExtra(EXTRA_EPISODE, episode ?: 0)
                .putExtra(EXTRA_EPISODE_LABEL, episodeLabel)
                .putExtra(EXTRA_SUBTITLE_URL, subtitleUrl)
    }
}

fun launchPlayer(
    context: Context,
    source: SourceItem,
    title: TitleInfo,
    episode: EpisodeItem? = null
) {
    if (source.external) {
        val page =
            source.pageUrl
                ?: source.url
                ?: error("No playable URL was returned.")

        openExternalUrl(context, page)
        return
    }

    val url =
        source.url
            ?: error("No playable URL was returned.")

    val subtitleUrl =
        title.subtitles
            .takeIf {
                it.startsWith("http://") ||
                    it.startsWith("https://")
            }

    context.startActivity(
        PlayerActivity.intent(
            context = context,
            url = url,
            mediaId = title.id,
            title = title.title,
            poster = title.poster,
            provider = title.provider,
            type = title.type,
            season = episode?.season,
            episode = episode?.episode,
            episodeLabel = episode?.label,
            subtitleUrl = subtitleUrl
        )
    )
}
