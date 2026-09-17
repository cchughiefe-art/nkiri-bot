package com.nkiridown.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ManagedDownloads.initialize(this)

        startForeground(
            NOTIFICATION_ID,
            buildNotification(0, 0, true)
        )

        scope.launch {
            ManagedDownloads.state(this@DownloadForegroundService)
                .collectLatest { tasks ->
                    val active = tasks.filter {
                        it.status == ManagedDownloadStatus.RUNNING ||
                        it.status == ManagedDownloadStatus.QUEUED
                    }

                    if (active.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collectLatest
                    }

                    val known = active.filter { it.totalBytes > 0L }
                    val progress =
                        if (known.isEmpty()) 0
                        else {
                            val downloaded = known.sumOf { it.downloadedBytes }
                            val total = known.sumOf { it.totalBytes }.coerceAtLeast(1L)
                            ((downloaded.toDouble() / total.toDouble()) * 100.0)
                                .toInt()
                                .coerceIn(0, 100)
                        }

                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            active.size,
                            progress,
                            known.isEmpty()
                        )
                    )
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE ->
                intent.getStringExtra(EXTRA_ID)?.let {
                    ManagedDownloads.pause(this, it)
                }

            ACTION_CANCEL ->
                intent.getStringExtra(EXTRA_ID)?.let {
                    ManagedDownloads.cancel(this, it)
                }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(
        active: Int,
        progress: Int,
        indeterminate: Boolean
    ): android.app.Notification {
        val first = ManagedDownloads.tasks.value.firstOrNull {
            it.status == ManagedDownloadStatus.RUNNING ||
            it.status == ManagedDownloadStatus.QUEUED
        }

        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                if (active <= 1) first?.title ?: "TheNkiri download"
                else "$active downloads running"
            )
            .setContentText(
                if (indeterminate) "Downloading…"
                else "$progress% complete"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setProgress(100, progress, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        first?.let { task ->
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionIntent(ACTION_PAUSE, task.id, 20)
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                actionIntent(ACTION_CANCEL, task.id, 30)
            )
        }

        return builder.build()
    }

    private fun actionIntent(
        action: String,
        id: String,
        requestCode: Int
    ): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, DownloadForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TheNkiri downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress and controls"
                setShowBadge(false)
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "nkiri_downloads"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_ID = "download_id"
        private const val ACTION_PAUSE = "com.nkiridown.app.PAUSE_DOWNLOAD"
        private const val ACTION_CANCEL = "com.nkiridown.app.CANCEL_DOWNLOAD"
    }
}
