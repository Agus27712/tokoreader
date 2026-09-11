package com.tkc.screener

import android.app.Activity
import android.app.Application
import android.os.Bundle
import timber.log.Timber

class TKCScreenerApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var activeActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        AppContextProvider.init(this)
        registerActivityLifecycleCallbacks(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Prevent Chromium WebView Code Cache / simple_file_enumerator errors on startup
        try {
            val jsCodeCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            val wasmCodeCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!jsCodeCache.exists()) jsCodeCache.mkdirs()
            if (!wasmCodeCache.exists()) wasmCodeCache.mkdirs()
        } catch (_: Exception) {}

        com.tkc.screener.service.CandidateScanWorker.schedule(this)
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivityCount++
        AppContextProvider.isAppInForeground = activeActivityCount > 0
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivityCount = maxOf(0, activeActivityCount - 1)
        AppContextProvider.isAppInForeground = activeActivityCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {
        AppContextProvider.isAppInForeground = true
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
