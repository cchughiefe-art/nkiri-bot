package com.nkiridown.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment

fun queueDownload(
    context: Context,
    source: SourceItem,
    title: String,
    episodeLabel: String? = null
): Long {
    val url =
        source.url
            ?: error(
                "No download URL was returned."
            )

    val safeName =
        listOfNotNull(
            title,
            episodeLabel,
            source.quality
                .takeIf {
                    it > 0
                }
                ?.let {
                    "${it}p"
                }
        )
            .joinToString(
                " - "
            )
            .replace(
                Regex(
                    """[\\/:*?"<>|]"""
                ),
                "_"
            )
            .take(120)

    val extension =
        source.format
            .ifBlank {
                "mp4"
            }

    val request =
        DownloadManager.Request(
            Uri.parse(url)
        )
            .setTitle(title)
            .setDescription(
                listOfNotNull(
                    episodeLabel,
                    source.quality
                        .takeIf {
                            it > 0
                        }
                        ?.let {
                            "${it}p"
                        },
                    source.sizeText
                        .takeIf {
                            it.isNotBlank()
                        }
                )
                    .joinToString(
                        " • "
                    )
            )
            .setMimeType(
                "video/mp4"
            )
            .setNotificationVisibility(
                DownloadManager.Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "$safeName.$extension"
            )

    val manager =
        context.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

    return manager.enqueue(
        request
    )
}

fun openDownloads(
    context: Context
) {
    val intent =
        Intent(
            DownloadManager.ACTION_VIEW_DOWNLOADS
        )
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

    context.startActivity(intent)
}

fun openExternalUrl(
    context: Context,
    url: String
) {
    val intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

    context.startActivity(intent)
}

fun shareApp(
    context: Context
) {
    val intent =
        Intent(
            Intent.ACTION_SEND
        )
            .setType(
                "text/plain"
            )
            .putExtra(
                Intent.EXTRA_TEXT,
                "Movies, series and K-Drama with TheNkiri Bot: https://t.me/nkiridownbot"
            )

    context.startActivity(
        Intent.createChooser(
            intent,
            "Share TheNkiri"
        )
    )
}
