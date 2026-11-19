package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.CapturedNotificationLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object NotificationBridge {

    const val CHANNEL_RECORDED_ID = "smart_ledger_recorded"
    const val CHANNEL_SIMULATED_PAYMENT_ID = "smart_ledger_payment_test"
    const val CHANNEL_BACKUP_URGENT_ID = "smart_ledger_backup_urgent"
    const val EXTRA_IS_TEST_PAYMENT_NOTIFICATION = "extra_is_test_payment_notification"
    const val EXTRA_OPEN_LOCAL_BACKUP = "extra_open_local_backup"

    private val _capturedLogs = MutableStateFlow<List<CapturedNotificationLog>>(emptyList())
    val capturedLogs: StateFlow<List<CapturedNotificationLog>> = _capturedLogs.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _accessibilityConnected.value = connected
    }

    fun appendLog(
        packageName: String,
        appLabel: String,
        title: String,
        content: String,
        status: String,
        summary: String
    ) {
        val log = CapturedNotificationLog(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            appLabel = appLabel,
            title = title,
            content = content,
            capturedAtMillis = System.currentTimeMillis(),
            status = status,
            summary = summary
        )
        _capturedLogs.update { current -> (listOf(log) + current).take(30) }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expectedShort = "${context.packageName}/${AutoBookkeepingAccessibilityService::class.java.name}"
        val expectedCompact = "${context.packageName}/.service.AutoBookkeepingAccessibilityService"
        return enabled.contains(expectedShort) || enabled.contains(expectedCompact) || _accessibilityConnected.value
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(fallback) }
            }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun requestListenerRebind(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isNotificationListenerEnabled(context)) {
            runCatching {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(context, AutoBookkeepingNotificationService::class.java)
                )
            }
        }
    }

    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val recordedChannel = NotificationChannel(
                CHANNEL_RECORDED_ID,
                "自动记账入账提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "当通知理解并成功入账后发送确认提醒"
            }
            val testChannel = NotificationChannel(
                CHANNEL_SIMULATED_PAYMENT_ID,
                "支付通知测试通道",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用于在无真机银行/支付App时触发真实系统通知监听测试"
            }
            val urgentBackupChannel = NotificationChannel(
                CHANNEL_BACKUP_URGENT_ID,
                "数据备份通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定期提醒导出本地备份的通知"
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0L, 350L, 180L, 350L)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(recordedChannel)
            manager.createNotificationChannel(testChannel)
            manager.createNotificationChannel(urgentBackupChannel)
        }
    }

    fun postRecordedConfirmation(context: Context, title: String, body: String) {
        ensureNotificationChannels(context)
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RECORDED_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        postNotificationSafely(
            context = context,
            notificationId = (System.currentTimeMillis() % 10000).toInt() + 100,
            notification = notification
        )
    }

    fun dispatchTestPaymentNotification(context: Context, title: String, body: String): Boolean {
        if (!hasPostNotificationPermission(context)) return false
        ensureNotificationChannels(context)
        val extras = android.os.Bundle().apply {
            putBoolean(EXTRA_IS_TEST_PAYMENT_NOTIFICATION, true)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_SIMULATED_PAYMENT_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addExtras(extras)
            .setAutoCancel(true)
            .build()

        return postNotificationSafely(
            context = context,
            notificationId = (System.currentTimeMillis() % 10000).toInt() + 2000,
            notification = notification
        )
    }

    fun postUrgentBackupReminder(
        context: Context,
        title: String,
        body: String
    ): Boolean {
        if (!hasPostNotificationPermission(context)) return false
        ensureNotificationChannels(context)
        val openIntent = PendingIntent.getActivity(
            context,
            3101,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_LOCAL_BACKUP, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP_URGENT_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        return postNotificationSafely(
            context = context,
            notificationId = 3101,
            notification = notification
        )
    }

    private fun postNotificationSafely(
        context: Context,
        notificationId: Int,
        notification: android.app.Notification
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
