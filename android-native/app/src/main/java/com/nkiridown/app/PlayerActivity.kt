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
import android.widget.ProgressBar
import android.widget.LinearLayout
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
    private var pendingCompatibilityRequest: CompatibilityPlaybackRequest? = null
    private var compatibilityInstallDialog: AlertDialog? = null

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

        val requestedResumePosition =
            intent.getLongExtra(
                EXTRA_RESUME_POSITION_MS,
                -1L
            )

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

                        if (
                            AppPreferences(this@PlayerActivity)
                                .autoPlayNext()
                        ) {
                            tryAutoNext()
                        } else {
                            showNextEpisodePrompt()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val request = compatibilityRequest(currentMediaUrl)

                    if (
                        CompatibilityModuleManager.isInstalled(this@PlayerActivity) &&
                        CompatibilityModuleManager.launch(this@PlayerActivity, request)
                    ) {
                        finish()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                        return
                    }

                    showCompatibilityDialog(
                        mediaUrl = currentMediaUrl,
                        reason = error.errorCodeName
                    )
                }
            }
        )

        val resumePosition =
            if (
                requestedResumePosition >= 0L
            ) {
                requestedResumePosition
            } else {
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
            }

        startMedia(
            url,
            resumePosition
        )

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

        val request = compatibilityRequest(mediaUrl)

        AlertDialog.Builder(this)
            .setTitle("Extra playback support needed")
            .setMessage(
                "This video needs extra playback support.\n\n" +
                    "Phone detected: ${CompatibilityModuleManager.deviceLabel()}\n\n" +
                    "The support engine installs inside TheNkiri. " +
                    "It does not create a second launcher app.\n\n" +
                    "Player error: $reason"
            )
            .setPositiveButton("Install Support") { _, _ ->
                compatibilityDialogShowing = false
                pendingCompatibilityRequest = request

                if (
                    CompatibilityModuleManager.canInstallSplits(this)
                ) {
                    startCompatibilityInstall(request)
                } else {
                    CompatibilityModuleManager.requestInstallPermission(this)
                }
            }
            .setNeutralButton("Open externally") { _, _ ->
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
            .setNegativeButton("Cancel") { _, _ ->
                compatibilityDialogShowing = false
            }
            .setOnCancelListener {
                compatibilityDialogShowing = false
            }
            .show()
    }

    private fun compatibilityRequest(
        mediaUrl: String
    ): CompatibilityPlaybackRequest =
        CompatibilityPlaybackRequest(
            url = mediaUrl,
            mediaId = mediaId,
            title = title,
            poster = poster,
            provider = provider,
            type = type,
            season = season,
            episode = episode,
            episodeLabel = episodeLabel,
            subtitleUrl = subtitleUrl
        )

    private fun startCompatibilityInstall(
        request: CompatibilityPlaybackRequest
    ) {
        if (
            compatibilityInstallDialog?.isShowing == true
        ) {
            return
        }

        val wrapper =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 28, 48, 12)
            }

        val progress =
            ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                max = 100
                progress = 0
            }

        val label =
            TextView(this).apply {
                text = "Preparing playback support…"
                setPadding(0, 18, 0, 0)
            }

        wrapper.addView(
            progress,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        wrapper.addView(label)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("TheNkiri Playback Support")
                .setView(wrapper)
                .setCancelable(false)
                .create()

        compatibilityInstallDialog = dialog
        dialog.show()

        lifecycleScope.launch {
            runCatching {
                CompatibilityModuleManager.downloadAndInstall(
                    this@PlayerActivity,
                    request
                ) { percent ->
                    progress.progress = percent
                    label.text =
                        if (percent < 90) {
                            "Downloading support… $percent%"
                        } else {
                            "Preparing installation…"
                        }
                }
            }.onFailure { error ->
                compatibilityInstallDialog?.dismiss()
                compatibilityInstallDialog = null

                Toast.makeText(
                    this@PlayerActivity,
                    error.message
                        ?: "Could not install playback support.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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

    private fun showNextEpisodePrompt() {
        val s = season ?: return
        val e = episode ?: return
        if (type != "series") return

        AlertDialog.Builder(this)
            .setTitle("Episode finished")
            .setMessage("Play the next episode now?")
            .setPositiveButton("Play next") { _, _ ->
                tryAutoNext()
            }
            .setNegativeButton("Not now", null)
            .show()
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

    override fun onResume() {
        super.onResume()

        val pending =
            pendingCompatibilityRequest
                ?: return

        if (
            CompatibilityModuleManager.canInstallSplits(this)
        ) {
            pendingCompatibilityRequest = null
            startCompatibilityInstall(pending)
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

        private const val EXTRA_RESUME_POSITION_MS =
            "resumePositionMs"

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
            subtitleUrl: String?,
            resumePositionMs: Long = -1L
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
                .putExtra(
                    EXTRA_RESUME_POSITION_MS,
                    resumePositionMs
                )
    }
}

fun launchPlayer(
    context: Context,
    source: SourceItem,
    title: TitleInfo,
    episode: EpisodeItem? = null,
    resumePositionMs: Long = -1L
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
            subtitleUrl = subtitleUrl,
            resumePositionMs = resumePositionMs
        )
    )
}
