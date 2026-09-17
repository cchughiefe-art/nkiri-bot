package com.nkiridown.compat

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class CompatPlayerActivity : Activity() {
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var surface: SurfaceView? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val mediaUrl =
            intent.getStringExtra("url")
                ?: intent.dataString
                ?: run {
                    finish()
                    return
                }

        window.addFlags(
            WindowManager.LayoutParams
                .FLAG_KEEP_SCREEN_ON
        )

        buildUi()

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

        val media =
            Media(
                vlc,
                Uri.parse(mediaUrl)
            )

        media.setHWDecoderEnabled(
            true,
            false
        )
        media.addOption(
            ":network-caching=1800"
        )

        mp.media = media
        media.release()
        mp.play()
    }

    private fun buildUi() {
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    android.graphics.Color.BLACK
                )
            }

        val video =
            SurfaceView(this)

        surface = video

        root.addView(
            video,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams
                    .MATCH_PARENT,
                ViewGroup.LayoutParams
                    .MATCH_PARENT
            )
        )

        val close =
            Button(this).apply {
                text = "Back"
                setOnClickListener {
                    finish()
                }
            }

        root.addView(
            close,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams
                    .WRAP_CONTENT,
                ViewGroup.LayoutParams
                    .WRAP_CONTENT,
                Gravity.TOP or
                    Gravity.START
            ).apply {
                setMargins(
                    18,
                    18,
                    0,
                    0
                )
            }
        )

        setContentView(root)
    }

    override fun onPause() {
        player?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        player?.vlcVout?.detachViews()
        player?.stop()
        player?.release()
        player = null

        libVlc?.release()
        libVlc = null

        super.onDestroy()
    }
}
