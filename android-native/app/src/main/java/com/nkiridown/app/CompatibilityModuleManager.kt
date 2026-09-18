package com.nkiridown.app

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class CompatibilityPlaybackRequest(
    val url: String,
    val mediaId: String,
    val title: String,
    val poster: String?,
    val provider: String,
    val type: String,
    val season: Int?,
    val episode: Int?,
    val episodeLabel: String?,
    val subtitleUrl: String?
)

object CompatibilityModuleManager {
    const val MODULE_NAME = "compatfeature"
    private const val ACTIVITY_CLASS =
        "com.nkiridown.app.compat.CompatPlayerActivity"

    const val ACTION_INSTALL_STATUS =
        "com.nkiridown.app.COMPAT_INSTALL_STATUS"

    const val EXTRA_URL = "url"
    const val EXTRA_MEDIA_ID = "mediaId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_POSTER = "poster"
    const val EXTRA_PROVIDER = "provider"
    const val EXTRA_TYPE = "type"
    const val EXTRA_SEASON = "season"
    const val EXTRA_EPISODE = "episode"
    const val EXTRA_EPISODE_LABEL = "episodeLabel"
    const val EXTRA_SUBTITLE_URL = "subtitleUrl"

    private const val PREFS = "nkiri_compat_pending"
    private const val KEY_PENDING = "pending"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    fun isInstalled(context: Context): Boolean =
        context.applicationInfo
            .splitNames
            ?.any {
                it.equals(
                    MODULE_NAME,
                    ignoreCase = true
                )
            }
            ?: false

    fun deviceLabel(): String =
        when {
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "ARM64"
            Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "ARM32"
            Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "x86_64"
            Build.SUPPORTED_ABIS.any { it == "x86" } -> "x86"
            else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
        }

    private fun abiAssetName(): String =
        when {
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } ->
                "TheNkiri-compatfeature-arm64-v8a.apk"

            Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } ->
                "TheNkiri-compatfeature-armeabi-v7a.apk"

            else ->
                error(
                    "The internal playback engine currently supports ARM64 and ARM32 Android devices."
                )
        }

    private fun releaseBaseUrl(): String =
        "${BuildConfig.COMPAT_SPLIT_ROOT_URL.trimEnd('/')}/compat-${BuildConfig.VERSION_CODE}"

    private fun assetUrl(name: String): String =
        "${releaseBaseUrl()}/$name"

    fun canInstallSplits(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 26) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun requestInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 26) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    }

    fun launch(
        context: Context,
        request: CompatibilityPlaybackRequest
    ): Boolean {
        if (!isInstalled(context)) return false

        return runCatching {
            val intent =
                Intent()
                    .setClassName(
                        context.packageName,
                        ACTIVITY_CLASS
                    )
                    .putPlaybackRequest(request)

            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

            if (context is Activity) {
                @Suppress("DEPRECATION")
                context.overridePendingTransition(0, 0)
            }

            true
        }.getOrDefault(false)
    }

    suspend fun downloadAndInstall(
        context: Context,
        request: CompatibilityPlaybackRequest,
        onProgress: (Int) -> Unit
    ) {
        require(canInstallSplits(context)) {
            "Install permission is required."
        }

        savePending(context, request)

        val cacheDir =
            File(
                context.cacheDir,
                "compat-splits"
            ).apply {
                mkdirs()
            }

        val masterName =
            "TheNkiri-compatfeature-master.apk"

        val abiName =
            abiAssetName()

        val master =
            download(
                assetUrl(masterName),
                File(cacheDir, masterName),
                0,
                35,
                onProgress
            )

        val abi =
            download(
                assetUrl(abiName),
                File(cacheDir, abiName),
                35,
                90,
                onProgress
            )

        withContext(Dispatchers.IO) {
            installSplits(
                context,
                listOf(master, abi)
            )
        }

        withContext(Dispatchers.Main) {
            onProgress(100)
        }
    }

    private suspend fun download(
        url: String,
        target: File,
        startPercent: Int,
        endPercent: Int,
        onProgress: (Int) -> Unit
    ): File =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url)
                    .get()
                    .build()

            client.newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        error(
                            "Playback support download failed (${response.code})."
                        )
                    }

                    val body =
                        response.body
                            ?: error(
                                "Playback support file was empty."
                            )

                    val total =
                        body.contentLength()
                            .coerceAtLeast(1L)

                    target.outputStream()
                        .buffered()
                        .use { output ->
                            val input =
                                body.byteStream()

                            val buffer =
                                ByteArray(64 * 1024)

                            var readTotal = 0L

                            while (true) {
                                val count =
                                    input.read(buffer)

                                if (count < 0) break

                                output.write(
                                    buffer,
                                    0,
                                    count
                                )

                                readTotal += count

                                val fraction =
                                    (
                                        readTotal.toDouble() /
                                            total.toDouble()
                                        )
                                        .coerceIn(0.0, 1.0)

                                val percent =
                                    startPercent +
                                        (
                                            (endPercent - startPercent) *
                                                fraction
                                            ).toInt()

                                withContext(Dispatchers.Main) {
                                    onProgress(percent)
                                }
                            }
                        }
                }

            target
        }

    private fun installSplits(
        context: Context,
        files: List<File>
    ) {
        val installer =
            context.packageManager.packageInstaller

        val params =
            PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
            ).apply {
                setAppPackageName(context.packageName)
                setSize(files.sumOf { it.length() })

                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(
                        PackageInstaller.SessionParams.USER_ACTION_REQUIRED
                    )
                }
            }

        val sessionId =
            installer.createSession(params)

        installer.openSession(sessionId)
            .use { session ->
                files.forEach { file ->
                    session.openWrite(
                        file.name,
                        0,
                        file.length()
                    ).use { output ->
                        file.inputStream()
                            .use { input ->
                                input.copyTo(output)
                            }

                        session.fsync(output)
                    }
                }

                val callbackIntent =
                    Intent(
                        context,
                        CompatibilityInstallReceiver::class.java
                    ).setAction(ACTION_INSTALL_STATUS)

                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        callbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_MUTABLE
                    )

                session.commit(
                    pendingIntent.intentSender
                )
            }
    }

    fun readPending(
        context: Context
    ): CompatibilityPlaybackRequest? {
        val raw =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
                .getString(KEY_PENDING, null)
                ?: return null

        return runCatching {
            val json = JSONObject(raw)

            CompatibilityPlaybackRequest(
                url = json.getString("url"),
                mediaId = json.optString("mediaId"),
                title = json.optString("title", "TheNkiri"),
                poster =
                    json.optString("poster")
                        .takeIf {
                            it.isNotBlank() &&
                                it != "null"
                        },
                provider =
                    json.optString(
                        "provider",
                        "moviex"
                    ),
                type =
                    json.optString(
                        "type",
                        "movie"
                    ),
                season =
                    json.optInt("season", 0)
                        .takeIf { it > 0 },
                episode =
                    json.optInt("episode", 0)
                        .takeIf { it > 0 },
                episodeLabel =
                    json.optString("episodeLabel")
                        .takeIf {
                            it.isNotBlank() &&
                                it != "null"
                        },
                subtitleUrl =
                    json.optString("subtitleUrl")
                        .takeIf {
                            it.isNotBlank() &&
                                it != "null"
                        }
            )
        }.getOrNull()
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY_PENDING)
            .apply()
    }

    private fun savePending(
        context: Context,
        request: CompatibilityPlaybackRequest
    ) {
        val json =
            JSONObject()
                .put("url", request.url)
                .put("mediaId", request.mediaId)
                .put("title", request.title)
                .put("poster", request.poster)
                .put("provider", request.provider)
                .put("type", request.type)
                .put("season", request.season)
                .put("episode", request.episode)
                .put("episodeLabel", request.episodeLabel)
                .put("subtitleUrl", request.subtitleUrl)

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_PENDING,
                json.toString()
            )
            .apply()
    }

    private fun Intent.putPlaybackRequest(
        request: CompatibilityPlaybackRequest
    ): Intent =
        putExtra(EXTRA_URL, request.url)
            .putExtra(EXTRA_MEDIA_ID, request.mediaId)
            .putExtra(EXTRA_TITLE, request.title)
            .putExtra(EXTRA_POSTER, request.poster)
            .putExtra(EXTRA_PROVIDER, request.provider)
            .putExtra(EXTRA_TYPE, request.type)
            .putExtra(EXTRA_SEASON, request.season ?: 0)
            .putExtra(EXTRA_EPISODE, request.episode ?: 0)
            .putExtra(EXTRA_EPISODE_LABEL, request.episodeLabel)
            .putExtra(EXTRA_SUBTITLE_URL, request.subtitleUrl)
}
