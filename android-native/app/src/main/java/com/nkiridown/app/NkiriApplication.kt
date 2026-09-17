package com.nkiridown.app

import android.app.Application

class NkiriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalCrashReporter.install(this)
        ManagedDownloads.initialize(this)
    }
}
