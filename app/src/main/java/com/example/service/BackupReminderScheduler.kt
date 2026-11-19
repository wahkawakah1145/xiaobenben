package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BackupReminderScheduler {

    private const val REQUEST_CODE_BACKUP_REMINDER = 4107

    fun isBackupDue(
        enabled: Boolean,
        intervalHours: Int,
        lastLocalBackupMillis: Long,
        lastBackupReminderMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!enabled) return false
        val intervalMillis = intervalHours.coerceIn(1, 8760) * 3_600_000L
        val lastAnchor = maxOf(lastLocalBackupMillis, lastBackupReminderMillis)
        if (lastAnchor <= 0L) return false
        return (nowMillis - lastAnchor) >= intervalMillis
    }

    fun computeNextTriggerAtMillis(
        intervalHours: Int,
        lastLocalBackupMillis: Long,
        lastBackupReminderMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val intervalMillis = intervalHours.coerceIn(1, 8760) * 3_600_000L
        val lastAnchor = maxOf(lastLocalBackupMillis, lastBackupReminderMillis)
        return if (lastAnchor <= 0L) {
            nowMillis + intervalMillis
        } else {
            maxOf(nowMillis + 60_000L, lastAnchor + intervalMillis)
        }
    }

    fun schedule(
        context: Context,
        enabled: Boolean,
        intervalHours: Int,
        lastLocalBackupMillis: Long,
        lastBackupReminderMillis: Long
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(appContext)
        alarmManager.cancel(pendingIntent)

        if (!enabled) return

        val triggerAt = computeNextTriggerAtMillis(
            intervalHours = intervalHours,
            lastLocalBackupMillis = lastLocalBackupMillis,
            lastBackupReminderMillis = lastBackupReminderMillis
        )
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    suspend fun checkDueAndNotifyIfNeeded(context: Context) {
        val repository = LedgerRepository(context.applicationContext)
        val settings = repository.getSettingsOnce()
        if (!settings.backupReminderEnabled) return

        val now = System.currentTimeMillis()
        if (isBackupDue(
                enabled = settings.backupReminderEnabled,
                intervalHours = settings.backupReminderIntervalHours,
                lastLocalBackupMillis = settings.lastLocalBackupMillis,
                lastBackupReminderMillis = settings.lastBackupReminderMillis,
                nowMillis = now
            )
        ) {
            val intervalText = formatIntervalLabel(settings.backupReminderIntervalHours)
            val posted = NotificationBridge.postUrgentBackupReminder(
                context = context.applicationContext,
                title = "【通知】请及时备份小本本账单",
                body = "已超过设定的备份周期（$intervalText），点击立即导出本地 JSON 备份，防止意外丢失记录。"
            )
            if (posted) {
                repository.markBackupReminderSent(now)
            }
            schedule(
                context = context,
                enabled = true,
                intervalHours = settings.backupReminderIntervalHours,
                lastLocalBackupMillis = settings.lastLocalBackupMillis,
                lastBackupReminderMillis = now
            )
        }
    }

    fun formatIntervalLabel(hours: Int): String {
        val safe = hours.coerceIn(1, 8760)
        return if (safe >= 24 && safe % 24 == 0) {
            "${safe / 24} 天"
        } else {
            "$safe 小时"
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BackupReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BACKUP_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class BackupReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = LedgerRepository(context.applicationContext)
                val settings = repository.getSettingsOnce()
                if (settings.backupReminderEnabled) {
                    val now = System.currentTimeMillis()
                    val intervalText = BackupReminderScheduler.formatIntervalLabel(settings.backupReminderIntervalHours)
                    NotificationBridge.postUrgentBackupReminder(
                        context = context.applicationContext,
                        title = "【通知】小本本数据备份时间到",
                        body = "已到达你设置的 $intervalText 备份周期，点击前往导出本地备份。"
                    )
                    repository.markBackupReminderSent(now)
                    BackupReminderScheduler.schedule(
                        context = context,
                        enabled = true,
                        intervalHours = settings.backupReminderIntervalHours,
                        lastLocalBackupMillis = settings.lastLocalBackupMillis,
                        lastBackupReminderMillis = now
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
