package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.AiUnderstandingManager
import com.example.data.AppHttpClient
import com.example.data.LedgerDatabase
import com.example.service.NotificationBridge
import java.util.concurrent.Executors

class LedgerApplication : Application() {

    private val trimExecutor by lazy(LazyThreadSafetyMode.NONE) {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "xbb-mem-trim").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            // Release idle UI/network/SQLite page caches without affecting NotificationListener / Accessibility keep-alive
            val keepLogs = if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) 2 else 6
            NotificationBridge.trimMemoryCaches(keepRecentLogs = keepLogs)
            AiUnderstandingManager.trimMemory(evictConnections = true)
            AppHttpClient.trimIdleResources()
            trimExecutor.execute {
                LedgerDatabase.trimMemoryIfOpen()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        NotificationBridge.trimMemoryCaches(keepRecentLogs = 0)
        AiUnderstandingManager.trimMemory(evictConnections = true)
        AppHttpClient.trimIdleResources()
        trimExecutor.execute {
            LedgerDatabase.trimMemoryIfOpen()
        }
    }
}
