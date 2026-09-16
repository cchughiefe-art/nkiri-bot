package com.nkiridown.app

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

object LocalCrashReporter {
    private const val PREFS = "nkiri_crash"
    private const val KEY = "last_crash"

    fun install(context: Context) {
        val previous =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler {
            thread,
            throwable ->

            runCatching {
                val writer =
                    StringWriter()

                throwable.printStackTrace(
                    PrintWriter(writer)
                )

                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                    .edit()
                    .putString(
                        KEY,
                        writer.toString()
                            .take(12_000)
                    )
                    .apply()
            }

            previous?.uncaughtException(
                thread,
                throwable
            )
        }
    }

    fun get(context: Context): String? =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .getString(KEY, null)
            ?.takeIf {
                it.isNotBlank()
            }

    fun clear(context: Context) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY)
            .apply()
    }
}
