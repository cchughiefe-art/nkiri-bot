package com.nkiridown.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment

data class DownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val reason: Int
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val label: String
        get() = when (status) {
            DownloadManager.STATUS_PENDING -> "Queued"
            DownloadManager.STATUS_RUNNING -> "Downloading"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_SUCCESSFUL -> "Ready offline"
            DownloadManager.STATUS_FAILED -> "Failed"
            else -> "Unknown"
        }
}

fun queueDownload(context: Context, source: SourceItem, title: String, episodeLabel: String? = null): Long {
    if (source.external) error("This provider returned a download page instead of a direct file.")
    val url = source.url ?: error("No download URL was returned.")
    val safeName = listOfNotNull(title, episodeLabel, source.quality.takeIf { it > 0 }?.let { "${it}p" })
        .joinToString(" - ").replace(Regex("""[\\/:*?"<>|]"""), "_").take(120)
    val extension = source.format.ifBlank { "mp4" }.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "mp4" }
    val mime = when (extension) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setDescription(listOfNotNull(episodeLabel, source.quality.takeIf { it > 0 }?.let { "${it}p" }, source.sizeText.takeIf { it.isNotBlank() }).joinToString(" • "))
        .setMimeType(mime)
        .addRequestHeader("User-Agent", "TheNkiri-Android/${BuildConfig.VERSION_NAME}")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$safeName.$extension")
    return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}

fun queryDownloadSnapshot(context: Context, downloadId: Long): DownloadSnapshot? {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        fun long(name: String) = cursor.getColumnIndex(name).takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
        fun int(name: String) = cursor.getColumnIndex(name).takeIf { it >= 0 }?.let(cursor::getInt) ?: 0
        return DownloadSnapshot(
            status = int(DownloadManager.COLUMN_STATUS),
            downloadedBytes = long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            totalBytes = long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            reason = int(DownloadManager.COLUMN_REASON)
        )
    }
    return null
}

fun cancelDownload(context: Context, downloadId: Long) {
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(downloadId)
}

fun openCompletedDownload(context: Context, record: DownloadRecord) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = manager.getUriForDownloadedFile(record.downloadId) ?: error("Downloaded file is not available.")
    context.startActivity(
        Intent(context, PlayerActivity::class.java)
            .putExtra("url", uri.toString())
            .putExtra("mediaId", record.mediaId)
            .putExtra("title", record.title)
            .putExtra("provider", "local")
            .putExtra("type", if (record.episodeLabel != null) "series" else "movie")
            .putExtra("episodeLabel", record.episodeLabel)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openCompletedDownload(context: Context, downloadId: Long) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = manager.getUriForDownloadedFile(downloadId) ?: error("Downloaded file is not available.")
    context.startActivity(
        Intent(context, PlayerActivity::class.java)
            .putExtra("url", uri.toString())
            .putExtra("mediaId", "download:$downloadId")
            .putExtra("title", "Downloaded video")
            .putExtra("provider", "local")
            .putExtra("type", "movie")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openExternalUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareApp(context: Context) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "Movies, series and K-Drama with TheNkiri: https://t.me/nkiridownbot")
    context.startActivity(Intent.createChooser(intent, "Share TheNkiri"))
}
