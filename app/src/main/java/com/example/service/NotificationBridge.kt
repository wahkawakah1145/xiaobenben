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
    const val EXTRA_IS_TEST_PAYMENT_NOTIFICATION = "extra_is_test_payment_notification"

    private val _capturedLogs = MutableStateFlow<List<CapturedNotificationLog>>(emptyList())
    val capturedLogs: StateFlow<List<CapturedNotificationLog>> = _capturedLogs.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
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
            manager.createNotificationChannel(recordedChannel)
            manager.createNotificationChannel(testChannel)
        }
    }

    fun postRecordedConfirmation(context: Context, title: String, body: String) {
        if (!hasPostNotificationPermission(context)) return
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

        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 10000).toInt() + 100, notification)
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

        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 10000).toInt() + 2000, notification)
        return true
    }
}
