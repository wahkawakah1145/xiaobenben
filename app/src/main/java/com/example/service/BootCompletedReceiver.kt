package com.example.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import com.example.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                NotificationBridge.isNotificationListenerEnabled(context)
            ) {
                runCatching {
                    NotificationListenerService.requestRebind(
                        ComponentName(context, AutoBookkeepingNotificationService::class.java)
                    )
                }
            }
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = LedgerRepository(context).getSettingsOnce()
                    if (settings.rootKeepAliveEnabled) {
                        RootKeepAliveManager.applyRootKeepAlive(context)
                    }
                    BackupReminderScheduler.schedule(
                        context = context,
                        enabled = settings.backupReminderEnabled,
                        intervalHours = settings.backupReminderIntervalHours,
                        lastLocalBackupMillis = settings.lastLocalBackupMillis,
                        lastBackupReminderMillis = settings.lastBackupReminderMillis
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
