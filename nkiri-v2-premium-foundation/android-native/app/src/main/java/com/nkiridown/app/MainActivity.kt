package com.nkiridown.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NkiriTheme {
                NkiriV2App(
                    viewModel = viewModel,
                    onDownload = { source ->
                        viewModel.resolveSource(source) { ready ->
                            val state = viewModel.state.value
                            val title = state.title ?: return@resolveSource

                            if (ready.external) {
                                val page = ready.pageUrl ?: ready.url
                                if (page != null) {
                                    openExternalUrl(this, page)
                                } else {
                                    Toast.makeText(this, "No usable download source", Toast.LENGTH_LONG).show()
                                }
                                return@resolveSource
                            }

                            runCatching {
                                val id = queueDownload(
                                    context = this,
                                    source = ready,
                                    title = title.title,
                                    episodeLabel = state.episode?.label
                                )
                                viewModel.recordDownload(id, ready)
                            }.onSuccess {
                                Toast.makeText(this, "Added to TheNkiri Downloads", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(this, error.message ?: "Could not start download", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onPlay = { source ->
                        viewModel.resolveSource(source) { ready ->
                            val state = viewModel.state.value
                            val title = state.title ?: return@resolveSource
                            runCatching {
                                launchPlayer(
                                    context = this,
                                    source = ready,
                                    title = title.title,
                                    episodeLabel = state.episode?.label
                                )
                            }.onFailure { error ->
                                Toast.makeText(this, error.message ?: "Could not play source", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onOpenUrl = { url -> runCatching { openExternalUrl(this, url) } },
                    onShare = { shareApp(this) }
                )
            }
        }
    }
}
