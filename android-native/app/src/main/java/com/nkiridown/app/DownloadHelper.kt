package com.nkiridown.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

fun queueDownload(
    context: Context,
    source: SourceItem,
    title: String,
    episodeLabel: String? = null
): Long {
    val url = source.url ?: error("No download URL was returned.")

    val base = listOfNotNull(
        title,
        episodeLabel,
        source.quality.takeIf { it > 0 }?.let { "${it}p" }
    ).joinToString(" - ")
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .take(120)

    val extension = source.format.ifBlank { "mp4" }
    val filename = "$base.$extension"

    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setDescription(
            listOfNotNull(
                episodeLabel,
                source.quality.takeIf { it > 0 }?.let { "${it}p" },
                source.sizeText.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
        )
        .setMimeType("video/mp4")
        .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            filename
        )

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return manager.enqueue(request)
}
