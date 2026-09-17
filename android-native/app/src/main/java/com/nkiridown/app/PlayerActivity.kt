package com.nkiridown.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
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

        val exo = ExoPlayer.Builder(this).build()
        player = exo

        val builder =
            MediaItem.Builder()
                .setUri(Uri.parse(url))
                .setMediaId(mediaId.ifBlank { url })

        subtitleUrl
            ?.takeIf {
                it.startsWith("http://") ||
                it.startsWith("https://")
            }
            ?.let { sub ->
                builder.setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration
                            .Builder(Uri.parse(sub))
                            .setMimeType("text/vtt")
                            .setLanguage("en")
                            .build()
                    )
                )
            }

        exo.setMediaItem(builder.build())
        exo.prepare()

        playbackStore.find(mediaId, season, episode)
            ?.takeIf {
                !it.completed &&
                it.positionMs > 0L
            }
            ?.let {
                exo.seekTo(it.positionMs)
            }

        exo.playWhenReady = true

        val view =
            PlayerView(this).apply {
                player = exo
                useController = true
                keepScreenOn = true
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
            }

        playerView = view
        setContentView(view)

        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    if (playbackState == Player.STATE_ENDED) {
                        saveProgress(completed = true)
                        tryAutoNext()
                    }
                }
            }
        )

        saveJob =
            lifecycleScope.launch {
                while (isActive) {
                    delay(5_000)
                    saveProgress(completed = false)
                }
            }
    }

    private fun saveProgress(completed: Boolean) {
        val p = player ?: return
        if (mediaId.isBlank()) return

        val duration =
            p.duration.takeIf { it > 0L } ?: 0L

        val position =
            if (completed && duration > 0L) {
                duration
            } else {
                p.currentPosition.coerceAtLeast(0L)
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
                    completed || (
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
                val api =
                    NkiriApi(
                        LocalStore(this@PlayerActivity)
                            .apiBaseUrl()
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
                        ?: response.sources.firstOrNull()
                        ?: return@runCatching

                if (
                    next.external ||
                    next.url.isNullOrBlank()
                ) return@runCatching

                player?.apply {
                    setMediaItem(
                        MediaItem.fromUri(
                            Uri.parse(next.url)
                        )
                    )
                    prepare()
                    playWhenReady = true
                }

                episode = nextEpisode
                episodeLabel =
                    "S${s.toString().padStart(2, '0')}E${nextEpisode.toString().padStart(2, '0')}"
            }
        }
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
        saveJob?.cancel()
        playerView?.player = null
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
        private const val EXTRA_EPISODE_LABEL = "episodeLabel"
        private const val EXTRA_SUBTITLE_URL = "subtitleUrl"

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
