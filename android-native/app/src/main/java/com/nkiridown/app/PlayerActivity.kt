package com.nkiridown.app

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
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
    private var currentMediaUrl: String = ""
    private var compatibilityDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }

        currentMediaUrl = url
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

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        saveProgress(completed = true)
                        tryAutoNext()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    showCompatibilityDialog(
                        mediaUrl = currentMediaUrl,
                        reason = error.errorCodeName
                    )
                }
            }
        )

        val resumePosition =
            playbackStore
                .find(mediaId, season, episode)
                ?.takeIf {
                    !it.completed &&
                        it.positionMs > 0L
                }
                ?.positionMs
                ?: 0L

        startMedia(url, resumePosition)

        saveJob =
            lifecycleScope.launch {
                while (isActive) {
                    delay(5_000)
                    saveProgress(completed = false)
                }
            }
    }

    private fun buildPlayerUi() {
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    android.graphics.Color.BLACK
                )
            }

        playerView =
            PlayerView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                useController = true
                controllerAutoShow = true
                controllerHideOnTouch = true
                setShowBuffering(
                    PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                )
                keepScreenOn = true
            }

        root.addView(playerView)

        val titleView =
            TextView(this).apply {
                text = this@PlayerActivity.title
                setTextColor(
                    android.graphics.Color.WHITE
                )
                setBackgroundColor(0x55000000)
                textSize = 16f
                setPadding(24, 14, 24, 14)
                maxLines = 1
            }

        root.addView(
            titleView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        setContentView(root)
    }

    private fun startMedia(
        url: String,
        resumePositionMs: Long = 0L
    ) {
        currentMediaUrl = url

        val itemBuilder =
            MediaItem.Builder()
                .setUri(Uri.parse(url))

        subtitleUrl
            ?.takeIf {
                it.startsWith("http://") ||
                    it.startsWith("https://")
            }
            ?.let { subUrl ->
                val mimeType =
                    when {
                        subUrl.contains(".vtt", ignoreCase = true) ->
                            MimeTypes.TEXT_VTT

                        subUrl.contains(".ssa", ignoreCase = true) ||
                            subUrl.contains(".ass", ignoreCase = true) ->
                            MimeTypes.TEXT_SSA

                        else ->
                            MimeTypes.APPLICATION_SUBRIP
                    }

                val subtitle =
                    MediaItem.SubtitleConfiguration
                        .Builder(Uri.parse(subUrl))
                        .setMimeType(mimeType)
                        .setLanguage("en")
                        .setSelectionFlags(
                            C.SELECTION_FLAG_DEFAULT
                        )
                        .build()

                itemBuilder.setSubtitleConfigurations(
                    listOf(subtitle)
                )
            }

        val p = player ?: return

        compatibilityDialogShowing = false
        p.setMediaItem(itemBuilder.build())
        p.prepare()

        if (resumePositionMs > 0L) {
            p.seekTo(resumePositionMs)
        }

        p.playWhenReady = true
    }

    private fun showCompatibilityDialog(
        mediaUrl: String,
        reason: String
    ) {
        if (
            isFinishing ||
            isDestroyed ||
            compatibilityDialogShowing
        ) {
            return
        }

        compatibilityDialogShowing = true
        player?.pause()

        val packInstalled =
            DeviceCompatibility.isPackInstalled(this)

        val actionLabel =
            if (packInstalled) {
                "Open Support"
            } else {
                "Install Support"
            }

        val message =
            buildString {
                append(
                    "This video uses a format your phone could not play normally."
                )
                append("\n\n")
                append("Phone detected: ")
                append(DeviceCompatibility.deviceLabel())
                append("\n\n")
                append("Recommended:\n")
                append("TheNkiri Compatibility Pack\n")
                append(DeviceCompatibility.deviceLabel())
                append("\n\n")
                append("Player error: ")
                append(reason)
            }

        AlertDialog.Builder(this)
            .setTitle(
                "Extra playback support needed"
            )
            .setMessage(message)
            .setPositiveButton(
                actionLabel
            ) { _, _ ->
                compatibilityDialogShowing = false

                val opened =
                    if (packInstalled) {
                        DeviceCompatibility.openInPack(
                            this,
                            mediaUrl,
                            title
                        )
                    } else {
                        DeviceCompatibility.openPackDownload(
                            this
                        )
                    }

                if (!opened) {
                    Toast.makeText(
                        this,
                        "Could not open playback support.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNeutralButton(
                "Open externally"
            ) { _, _ ->
                compatibilityDialogShowing = false

                if (
                    !DeviceCompatibility.openExternalPlayer(
                        this,
                        mediaUrl
                    )
                ) {
                    Toast.makeText(
                        this,
                        "No compatible external player found.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(
                "Cancel"
            ) { _, _ ->
                compatibilityDialogShowing = false
            }
            .setOnCancelListener {
                compatibilityDialogShowing = false
            }
            .show()
    }

    private fun saveProgress(
        completed: Boolean
    ) {
        val p = player ?: return
        if (mediaId.isBlank()) return

        val rawDuration = p.duration
        val duration =
            rawDuration
                .takeIf {
                    it != C.TIME_UNSET &&
                        it > 0L
                }
                ?: 0L

        val position =
            if (completed && duration > 0L) {
                duration
            } else {
                p.currentPosition
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
                updatedAt =
                    System.currentTimeMillis(),
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

    private fun tryAutoNext() {
        val s = season ?: return
        val e = episode ?: return

        if (type != "series") return

        lifecycleScope.launch {
            runCatching {
                val api =
                    NkiriApi(
                        LocalStore(
                            this@PlayerActivity
                        ).apiBaseUrl()
                    )

                val nextEpisode = e + 1

                val response =
                    api.sources(
                        id = mediaId,
                        season = s,
                        episode = nextEpisode
                    )

                val next =
                    response.selected
                        ?: response.sources
                            .firstOrNull()
                        ?: return@runCatching

                if (
                    next.external ||
                    next.url.isNullOrBlank()
                ) {
                    return@runCatching
                }

                episode = nextEpisode
                episodeLabel =
                    "S${s.toString().padStart(2, '0')}" +
                        "E${nextEpisode.toString().padStart(2, '0')}"

                startMedia(next.url!!)
            }
        }
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
        saveJob?.cancel()

        playerView.player = null
        player?.release()
        player = null

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
        private const val EXTRA_EPISODE_LABEL =
            "episodeLabel"
        private const val EXTRA_SUBTITLE_URL =
            "subtitleUrl"

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
            Intent(
                context,
                PlayerActivity::class.java
            )
                .putExtra(EXTRA_URL, url)
                .putExtra(
                    EXTRA_MEDIA_ID,
                    mediaId
                )
                .putExtra(
                    EXTRA_TITLE,
                    title
                )
                .putExtra(
                    EXTRA_POSTER,
                    poster
                )
                .putExtra(
                    EXTRA_PROVIDER,
                    provider
                )
                .putExtra(
                    EXTRA_TYPE,
                    type
                )
                .putExtra(
                    EXTRA_SEASON,
                    season ?: 0
                )
                .putExtra(
                    EXTRA_EPISODE,
                    episode ?: 0
                )
                .putExtra(
                    EXTRA_EPISODE_LABEL,
                    episodeLabel
                )
                .putExtra(
                    EXTRA_SUBTITLE_URL,
                    subtitleUrl
                )
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
                ?: error(
                    "No playable URL was returned."
                )

        openExternalUrl(
            context,
            page
        )
        return
    }

    val url =
        source.url
            ?: error(
                "No playable URL was returned."
            )

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
