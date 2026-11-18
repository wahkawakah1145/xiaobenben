package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoBookkeepingAccessibilityService : AccessibilityService() {

    private var lastRebindCheckMillis: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        NotificationBridge.setAccessibilityConnected(true)
        ensureNotificationListenerBound()
        Log.d(TAG, "Accessibility keep-alive service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastRebindCheckMillis >= REBIND_THROTTLE_MS) {
            lastRebindCheckMillis = now
            ensureNotificationListenerBound()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility keep-alive service interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        NotificationBridge.setAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    private fun ensureNotificationListenerBound() {
        if (!NotificationBridge.serviceConnected.value &&
            NotificationBridge.isNotificationListenerEnabled(this)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    NotificationListenerService.requestRebind(
                        ComponentName(this, AutoBookkeepingNotificationService::class.java)
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "XiaoBenBenKeepAlive"
        const val REBIND_THROTTLE_MS = 30_000L
    }
}
