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
                NkiriApp(
                    viewModel = viewModel,
                    onDownload = { source, title, episodeLabel ->
                        runCatching {
                            queueDownload(
                                context = this,
                                source = source,
                                title = title,
                                episodeLabel = episodeLabel
                            )
                        }.onSuccess {
                            Toast.makeText(
                                this,
                                "Download started",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(
                                this,
                                error.message ?: "Could not start download",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }
}
