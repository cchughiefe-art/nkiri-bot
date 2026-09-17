package com.nkiridown.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class ManagedDownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class ManagedDownload(
    val id: String,
    val mediaId: String,
    val title: String,
    val episodeLabel: String?,
    val quality: Int,
    val sizeText: String,
    val url: String,
    val filePath: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: ManagedDownloadStatus,
    val error: String? = null,
    val createdAt: Long
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble())
                .coerceIn(0.0, 1.0).toFloat()
        } else 0f
}

object ManagedDownloads {
    private const val PREFS = "nkiri_managed_downloads"
    private const val KEY_TASKS = "tasks"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _tasks = MutableStateFlow<List<ManagedDownload>>(emptyList())
    val tasks: StateFlow<List<ManagedDownload>> = _tasks.asStateFlow()

    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val loaded = read(app).map {
            if (it.status == ManagedDownloadStatus.RUNNING ||
                it.status == ManagedDownloadStatus.QUEUED
            ) {
                it.copy(status = ManagedDownloadStatus.PAUSED)
            } else it
        }
        _tasks.value = loaded
        persist(app)
        initialized = true
    }

    fun state(context: Context): StateFlow<List<ManagedDownload>> {
        initialize(context)
        return tasks
    }

    fun enqueue(
        context: Context,
        source: SourceItem,
        mediaId: String,
        title: String,
        episodeLabel: String? = null
    ): String {
        initialize(context)
        if (source.external) error("This source can only be opened on the provider page.")
        val url = source.url ?: error("No direct download URL was returned.")

        val extension = source.format
            .ifBlank { "mp4" }
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .ifBlank { "mp4" }

        val safeTitle = listOfNotNull(
            title,
            episodeLabel,
            source.quality.takeIf { it > 0 }?.let { "${it}p" }
        )
            .joinToString(" - ")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(120)

        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "TheNkiri"
        )
        dir.mkdirs()

        val id = UUID.randomUUID().toString()
        val finalFile = File(dir, "$safeTitle-$id.$extension")

        val task = ManagedDownload(
            id = id,
            mediaId = mediaId,
            title = title,
            episodeLabel = episodeLabel,
            quality = source.quality,
            sizeText = source.sizeText,
            url = url,
            filePath = finalFile.absolutePath,
            downloadedBytes = 0L,
            totalBytes = source.size.takeIf { it > 0 } ?: 0L,
            status = ManagedDownloadStatus.QUEUED,
            createdAt = System.currentTimeMillis()
        )

        update(context.applicationContext) { listOf(task) + it }
        ensureForegroundService(context)
        start(context.applicationContext, id)
        return id
    }

    fun pause(context: Context, id: String) {
        initialize(context)
        jobs.remove(id)?.cancel()
        update(context.applicationContext) { list ->
            list.map {
                if (it.id == id &&
                    it.status != ManagedDownloadStatus.COMPLETED
                ) it.copy(status = ManagedDownloadStatus.PAUSED, error = null)
                else it
            }
        }
    }

    fun resume(context: Context, id: String) {
        initialize(context)
        ensureForegroundService(context)
        start(context.applicationContext, id)
    }

    fun retry(context: Context, id: String) {
        initialize(context)
        update(context.applicationContext) { list ->
            list.map {
                if (it.id == id) it.copy(
                    status = ManagedDownloadStatus.PAUSED,
                    error = null
                ) else it
            }
        }
        ensureForegroundService(context)
        start(context.applicationContext, id)
    }

    fun cancel(context: Context, id: String) {
        initialize(context)
        jobs.remove(id)?.cancel()
        val task = _tasks.value.firstOrNull { it.id == id }
        task?.let {
            File(it.filePath + ".part").delete()
            File(it.filePath).delete()
        }
        update(context.applicationContext) { list ->
            list.filterNot { it.id == id }
        }
    }

    fun clearCompleted(context: Context) {
        initialize(context)
        update(context.applicationContext) { list ->
            list.filterNot { it.status == ManagedDownloadStatus.COMPLETED }
        }
    }

    fun play(context: Context, task: ManagedDownload) {
        val file = File(task.filePath)
        if (!file.exists()) error("Downloaded file is missing.")

        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra("url", Uri.fromFile(file).toString())
                .putExtra("mediaId", task.mediaId)
                .putExtra("title", task.title)
                .putExtra("provider", "local")
                .putExtra(
                    "type",
                    if (task.episodeLabel != null) "series" else "movie"
                )
                .putExtra("episodeLabel", task.episodeLabel)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun start(context: Context, id: String) {
        if (jobs[id]?.isActive == true) return

        val existing = _tasks.value.firstOrNull { it.id == id } ?: return
        if (existing.status == ManagedDownloadStatus.COMPLETED) return

        update(context) { list ->
            list.map {
                if (it.id == id) it.copy(
                    status = ManagedDownloadStatus.QUEUED,
                    error = null
                ) else it
            }
        }

        jobs[id] = scope.launch {
            try {
                download(context, id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update(context) { list ->
                    list.map {
                        if (it.id == id) it.copy(
                            status = ManagedDownloadStatus.FAILED,
                            error = error.message ?: "Download failed"
                        ) else it
                    }
                }
            } finally {
                jobs.remove(id)
            }
        }
    }

    private suspend fun download(context: Context, id: String) {
        var task = _tasks.value.firstOrNull { it.id == id } ?: return
        val finalFile = File(task.filePath)
        val partFile = File(task.filePath + ".part")
        partFile.parentFile?.mkdirs()

        var existing = if (partFile.exists()) partFile.length() else 0L

        var requestBuilder = Request.Builder()
            .url(task.url)
            .header("User-Agent", "TheNkiri-Android/${BuildConfig.VERSION_NAME}")
            .header("Accept-Encoding", "identity")

        if (existing > 0L) {
            requestBuilder = requestBuilder.header("Range", "bytes=$existing-")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        response.use { res ->
            if (!res.isSuccessful) {
                throw IllegalStateException("Server returned HTTP ${res.code}")
            }

            val resumed = existing > 0L && res.code == 206

            if (existing > 0L && !resumed) {
                // Server ignored Range. Restart safely instead of corrupting the file.
                partFile.delete()
                existing = 0L
            }

            val contentLength = res.body?.contentLength()?.takeIf { it >= 0 } ?: 0L
            val total = parseTotalFromContentRange(
                res.header("Content-Range")
            ) ?: if (contentLength > 0L) existing + contentLength else task.totalBytes

            update(context) { list ->
                list.map {
                    if (it.id == id) it.copy(
                        status = ManagedDownloadStatus.RUNNING,
                        downloadedBytes = existing,
                        totalBytes = total.coerceAtLeast(it.totalBytes),
                        error = null
                    ) else it
                }
            }

            val body = res.body ?: error("Server returned an empty body.")
            val input = body.byteStream()

            RandomAccessFile(partFile, "rw").use { out ->
                out.seek(existing)
                val buffer = ByteArray(128 * 1024)
                var downloaded = existing
                var lastPersist = System.currentTimeMillis()
                var lastPersistBytes = downloaded

                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break

                    out.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    if (
                        now - lastPersist >= 500L ||
                        downloaded - lastPersistBytes >= 1024L * 1024L
                    ) {
                        update(context, persistNow = false) { list ->
                            list.map {
                                if (it.id == id) it.copy(
                                    status = ManagedDownloadStatus.RUNNING,
                                    downloadedBytes = downloaded,
                                    totalBytes = total.coerceAtLeast(it.totalBytes)
                                ) else it
                            }
                        }
                        lastPersist = now
                        lastPersistBytes = downloaded
                    }
                }

                out.fd.sync()
            }

            if (finalFile.exists()) finalFile.delete()

            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            val size = finalFile.length()

            update(context) { list ->
                list.map {
                    if (it.id == id) it.copy(
                        status = ManagedDownloadStatus.COMPLETED,
                        downloadedBytes = size,
                        totalBytes = maxOf(size, total),
                        error = null
                    ) else it
                }
            }
        }
    }

    private fun ensureForegroundService(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(
                    context.applicationContext,
                    DownloadForegroundService::class.java
                )
            )
        }
    }

    private fun parseTotalFromContentRange(value: String?): Long? {
        val total = value
            ?.substringAfter('/', "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "*" }
            ?: return null

        return total.toLongOrNull()
    }

    @Synchronized
    private fun update(
        context: Context,
        persistNow: Boolean = true,
        transform: (List<ManagedDownload>) -> List<ManagedDownload>
    ) {
        _tasks.value = transform(_tasks.value)
        if (persistNow) persist(context)
        else {
            // Progress is flushed frequently enough without blocking every network read.
            scope.launch {
                delay(750)
                persist(context)
            }
        }
    }

    @Synchronized
    private fun persist(context: Context) {
        val array = JSONArray()

        _tasks.value.take(100).forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("mediaId", task.mediaId)
                    .put("title", task.title)
                    .put("episodeLabel", task.episodeLabel)
                    .put("quality", task.quality)
                    .put("sizeText", task.sizeText)
                    .put("url", task.url)
                    .put("filePath", task.filePath)
                    .put("downloadedBytes", task.downloadedBytes)
                    .put("totalBytes", task.totalBytes)
                    .put("status", task.status.name)
                    .put("error", task.error)
                    .put("createdAt", task.createdAt)
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, array.toString())
            .apply()
    }

    private fun read(context: Context): List<ManagedDownload> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TASKS, "[]")
            ?: "[]"

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        ManagedDownload(
                            id = item.optString("id"),
                            mediaId = item.optString("mediaId"),
                            title = item.optString("title"),
                            episodeLabel = item.optString("episodeLabel")
                                .takeIf { it.isNotBlank() && it != "null" },
                            quality = item.optInt("quality"),
                            sizeText = item.optString("sizeText"),
                            url = item.optString("url"),
                            filePath = item.optString("filePath"),
                            downloadedBytes = item.optLong("downloadedBytes"),
                            totalBytes = item.optLong("totalBytes"),
                            status = runCatching {
                                ManagedDownloadStatus.valueOf(
                                    item.optString(
                                        "status",
                                        ManagedDownloadStatus.PAUSED.name
                                    )
                                )
                            }.getOrDefault(ManagedDownloadStatus.PAUSED),
                            error = item.optString("error")
                                .takeIf { it.isNotBlank() && it != "null" },
                            createdAt = item.optLong("createdAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
