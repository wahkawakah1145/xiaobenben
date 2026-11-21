package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.AiUnderstandingManager
import com.example.data.AppHttpClient
import com.example.data.LedgerDatabase
import com.example.service.NotificationBridge
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LedgerApplication : Application() {

    /**
     * Zero-core-thread executor with short 15s keep-alive so no permanent idle thread stack
     * (~1MB virtual/stack footprint) lingers when only NotificationListener / Accessibility runs.
     */
    private val trimExecutor by lazy(LazyThreadSafetyMode.NONE) {
        ThreadPoolExecutor(
            0,
            1,
            15L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(4)
        ) { runnable ->
            Thread(runnable, "xbb-mem-trim").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }.apply {
            allowCoreThreadTimeOut(true)
        }
    }

    fun requestLightBackgroundTrim(aggressive: Boolean = false) {
        val keepLogs = if (aggressive) 0 else 4
        NotificationBridge.trimMemoryCaches(keepRecentLogs = keepLogs)
        AiUnderstandingManager.trimMemory(evictConnections = true)
        AppHttpClient.trimIdleResources()
        runCatching {
            trimExecutor.execute {
                LedgerDatabase.trimMemoryIfOpen()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                requestLightBackgroundTrim(aggressive = true)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                // Release idle UI/network/SQLite page caches without affecting NotificationListener / Accessibility keep-alive
                requestLightBackgroundTrim(aggressive = false)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        requestLightBackgroundTrim(aggressive = true)
    }
}
