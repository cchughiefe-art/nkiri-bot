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

    companion object {
        private const val KEY_DATA_SAVER = "data_saver"
    }
}
