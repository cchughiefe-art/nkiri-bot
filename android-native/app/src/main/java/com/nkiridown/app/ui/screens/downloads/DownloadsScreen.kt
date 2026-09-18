package com.nkiridown.app.ui.screens.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nkiridown.app.ManagedDownload
import com.nkiridown.app.ManagedDownloadStatus
import com.nkiridown.app.ManagedDownloads
import com.nkiridown.app.ui.components.EmptyState
import com.nkiridown.app.ui.components.SectionHeader
import com.nkiridown.app.ui.theme.*

@Composable
fun DownloadsScreen(
    onLegacyClear: () -> Unit
) {
    val context = LocalContext.current
    val tasks by ManagedDownloads.state(context).collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        ManagedDownloads.clearCompleted(context)
                        onLegacyClear()
                        confirmClear = false
                    }
                ) {
                    Text("Clear", color = NkiriDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", color = NkiriMuted)
                }
            },
            title = { Text("Clear completed downloads?") },
            text = { Text("This removes completed items from the download list.") }
        )
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "Downloads",
                subtitle = "Offline files and active transfers",
                action = if (tasks.any { it.status == ManagedDownloadStatus.COMPLETED }) "Clear done" else null,
                onAction = if (tasks.any { it.status == ManagedDownloadStatus.COMPLETED }) {
                    { confirmClear = true }
                } else null
            )
        }

        if (tasks.isEmpty()) {
            item {
                EmptyState("No downloads yet", "Choose a quality from any movie or episode to save it offline.")
            }
        }

        items(tasks, key = { it.id }) { task ->
            DownloadCard(task)
        }
    }
}

@Composable
private fun DownloadCard(task: ManagedDownload) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = NkiriCard,
        shape = NkiriShapes.medium,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                task.title,
                color = NkiriText,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    task.episodeLabel,
                    task.quality.takeIf { it > 0 }?.let { "${it}p" },
                    task.sizeText.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                color = NkiriMuted
            )

            Spacer(Modifier.height(10.dp))

            if (task.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = NkiriViolet,
                    trackColor = NkiriElevated
                )
                Spacer(Modifier.height(7.dp))
            }

            val bytesText =
                if (task.totalBytes > 0L) {
                    "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}"
                } else {
                    formatBytes(task.downloadedBytes)
                }

            val speed =
                task.speedBytesPerSecond.takeIf { it > 0L }?.let { "${formatBytes(it)}/s" }

            val eta =
                task.etaSeconds?.takeIf { it > 0L }?.let { seconds ->
                    when {
                        seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m left"
                        seconds >= 60 -> "${seconds / 60}m left"
                        else -> "${seconds}s left"
                    }
                }

            Text(
                listOfNotNull(bytesText, speed, eta).joinToString(" • "),
                color = NkiriMuted,
                style = MaterialTheme.typography.bodySmall
            )

            task.error?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = NkiriDanger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))

            when (task.status) {
                ManagedDownloadStatus.RUNNING,
                ManagedDownloadStatus.QUEUED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { ManagedDownloads.pause(context, task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Pause")
                        }
                        OutlinedButton(
                            onClick = { ManagedDownloads.cancel(context, task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = NkiriDanger)
                            Spacer(Modifier.width(5.dp))
                            Text("Cancel", color = NkiriDanger)
                        }
                    }
                }

                ManagedDownloadStatus.PAUSED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { ManagedDownloads.resume(context, task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Resume")
                        }
                        OutlinedButton(
                            onClick = { ManagedDownloads.cancel(context, task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete", color = NkiriDanger)
                        }
                    }
                }

                ManagedDownloadStatus.FAILED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { ManagedDownloads.retry(context, task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Retry")
                        }
                        OutlinedButton(
                            onClick = { ManagedDownloads.cancel(context, task.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete", color = NkiriDanger)
                        }
                    }
                }

                ManagedDownloadStatus.COMPLETED -> {
                    Button(
                        onClick = {
                            runCatching {
                                ManagedDownloads.play(context, task)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Watch in TheNkiri")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> "%.2f GB".format(bytes / gb)
        bytes >= mb -> "%.1f MB".format(bytes / mb)
        bytes >= kb -> "%.0f KB".format(bytes / kb)
        else -> "$bytes B"
    }
}
