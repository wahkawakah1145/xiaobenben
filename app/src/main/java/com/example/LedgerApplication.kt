package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.AiUnderstandingManager
import com.example.service.NotificationBridge

class LedgerApplication : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            // Release idle UI/network caches without affecting NotificationListener / Accessibility keep-alive
            NotificationBridge.trimMemoryCaches(keepRecentLogs = 6)
            AiUnderstandingManager.trimMemory(evictConnections = true)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        NotificationBridge.trimMemoryCaches(keepRecentLogs = 2)
        AiUnderstandingManager.trimMemory(evictConnections = true)
    }
}
