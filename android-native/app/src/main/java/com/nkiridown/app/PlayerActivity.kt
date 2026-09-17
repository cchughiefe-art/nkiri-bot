package com.nkiridown.app

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "TheNkiri"

        setContent {
            NkiriTheme {
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    Text(title, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        factory = { context ->
                            val exo = ExoPlayer.Builder(context).build().also {
                                player = it
                                it.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                                it.prepare()
                                it.playWhenReady = true
                            }
                            PlayerView(context).apply {
                                this.player = exo
                                useController = true
                                keepScreenOn = true
                            }
                        }
                    )
                    DisposableEffect(Unit) {
                        onDispose { player?.release(); player = null }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= 26 && player?.isPlaying == true) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        fun intent(context: Context, url: String, title: String): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title)
    }
}

fun launchPlayer(context: Context, source: SourceItem, title: String, episodeLabel: String? = null) {
    if (source.external) {
        val page = source.pageUrl ?: source.url ?: error("No playable URL was returned.")
        openExternalUrl(context, page)
        return
    }
    val url = source.url ?: error("No playable URL was returned.")
    val label = listOfNotNull(title, episodeLabel).joinToString(" • ")
    context.startActivity(PlayerActivity.intent(context, url, label))
}
