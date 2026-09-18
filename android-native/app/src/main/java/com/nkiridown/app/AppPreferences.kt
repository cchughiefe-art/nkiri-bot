package com.nkiridown.app

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "nkiri_ui_preferences",
            Context.MODE_PRIVATE
        )

    fun dataSaverEnabled(): Boolean =
        prefs.getBoolean(KEY_DATA_SAVER, false)

    fun setDataSaverEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DATA_SAVER, enabled)
            .apply()
    }

    fun preferredQuality(): Int =
        prefs.getInt(KEY_PREFERRED_QUALITY, 720)

    fun setPreferredQuality(quality: Int) {
        prefs.edit()
            .putInt(KEY_PREFERRED_QUALITY, quality.coerceAtLeast(0))
            .apply()
    }

    fun wifiOnlyDownloads(): Boolean =
        prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, false)

    fun setWifiOnlyDownloads(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled)
            .apply()
    }

    fun autoPlayNext(): Boolean =
        prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)

    fun setAutoPlayNext(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_PLAY_NEXT, enabled)
            .apply()
    }

    fun onboardingSeen(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_SEEN, false)

    fun setOnboardingSeen(seen: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_SEEN, seen)
            .apply()
    }

    companion object {
        private const val KEY_DATA_SAVER = "data_saver"
        private const val KEY_PREFERRED_QUALITY = "preferred_quality"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        private const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    }
}
