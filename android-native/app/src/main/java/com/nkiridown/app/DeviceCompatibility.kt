package com.nkiridown.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object DeviceCompatibility {
    private const val PACK_PACKAGE = "com.nkiridown.compat"
    private const val PACK_ACTION = "com.nkiridown.compat.PLAY"

    fun deviceLabel(): String =
        when {
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "ARM64"
            Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "ARM32"
            Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "x86_64"
            Build.SUPPORTED_ABIS.any { it == "x86" } -> "x86"
            else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown"
        }

    fun abiToken(): String =
        when {
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "arm64-v8a"
            Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "armeabi-v7a"
            Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "x86_64"
            Build.SUPPORTED_ABIS.any { it == "x86" } -> "x86"
            else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        }

    fun openExternalPlayer(
        context: Context,
        mediaUrl: String
    ): Boolean =
        runCatching {
            val uri = Uri.parse(mediaUrl)

            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "video/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(
                Intent.createChooser(
                    intent,
                    "Open with player"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
}
