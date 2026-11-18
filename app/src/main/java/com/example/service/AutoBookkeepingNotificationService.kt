package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.LedgerRepository
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.model.ParseMethod
import com.example.parser.ChinaNotificationParser
import com.example.parser.ChinaPaymentCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AutoBookkeepingNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentFingerprints = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.setServiceConnected(true)
        Log.d(TAG, "AutoBookkeepingNotificationService connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBridge.setServiceConnected(false)
        Log.d(TAG, "AutoBookkeepingNotificationService disconnected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification?.extras ?: return
        val isSelfTestNotification = extras.getBoolean(
            NotificationBridge.EXTRA_IS_TEST_PAYMENT_NOTIFICATION,
            false
        )
        if (sbn.packageName == packageName && !isSelfTestNotification) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val content = bigText?.takeIf { it.isNotBlank() } ?: text

        if (title.isBlank() && content.isBlank()) return

        val isWatched = ChinaPaymentCatalog.isWatchedPackage(sbn.packageName) || isSelfTestNotification
        if (!isWatched && !ChinaNotificationParser.isLikelyFinancialNotification(sbn.packageName, title, content)) {
            return
        }

        val fingerprint = "${sbn.packageName}|$title|$content"
        val now = System.currentTimeMillis()
        val lastSeen = recentFingerprints[fingerprint]
        if (lastSeen != null && now - lastSeen < DEDUP_WINDOW_MS) {
            return
        }
        recentFingerprints[fingerprint] = now
        if (recentFingerprints.size > 100) {
            recentFingerprints.entries.removeIf { now - it.value > DEDUP_WINDOW_MS * 4 }
        }

        val appLabel = ChinaPaymentCatalog.watchedPackageMap[sbn.packageName]?.name
            ?: if (isSelfTestNotification) "系统通知测试" else sbn.packageName

        serviceScope.launch {
            processIncomingNotification(
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = title,
                content = content,
                occurredAt = sbn.postTime.takeIf { it > 0L } ?: now
            )
        }
    }

    private suspend fun processIncomingNotification(
        packageName: String,
        appLabel: String,
        title: String,
        content: String,
        occurredAt: Long
    ) {
        val repository = LedgerRepository(applicationContext)
        val settings = repository.getSettingsOnce()
        if (!settings.autoRecordEnabled) {
            NotificationBridge.appendLog(
                packageName = packageName,
                appLabel = appLabel,
                title = title,
                content = content,
                status = "已暂停自动记账",
                summary = "自动记账开关已关闭"
            )
            return
        }

        val customRules = repository.getRulesOnce()
        val parsed = ChinaNotificationParser.parse(
            packageName = packageName,
            title = title,
            content = content,
            customRules = customRules,
            enableLocalSemanticPass = settings.smartSemanticEnabled
        )

        if (parsed == null || !parsed.isFinancialTransaction || parsed.amountCents <= 0L) {
            NotificationBridge.appendLog(
                packageName = packageName,
                appLabel = appLabel,
                title = title,
                content = content,
                status = "未能识别金额",
                summary = "非动账通知或格式未命中规则"
            )
            return
        }

        val needsManualConfirm = settings.requireManualConfirm
        val tx = LedgerTransaction(
            amountCents = parsed.amountCents,
            currencyCode = parsed.currencyCode,
            type = parsed.type.name,
            category = parsed.category,
            merchant = parsed.merchant,
            channel = parsed.channel,
            cardLastFour = parsed.cardLastFour,
            sourceApp = parsed.sourceApp.name,
            parseMethod = parsed.parseMethod.name,
            rawNotification = "$title: $content".take(600),
            note = parsed.reasoning.take(200),
            occurredAtMillis = occurredAt,
            confirmed = !needsManualConfirm
        )

        val saveResult = repository.addTransaction(tx)
        saveResult.onSuccess {
            val currencySymbol = CurrencyCatalog.resolve(parsed.currencyCode).symbol
            val formattedAmount = "$currencySymbol%.2f".format(parsed.amountCents / 100.0)
            val methodTag = if (parsed.parseMethod == ParseMethod.LOCAL_NLP) "语义识别" else "规则匹配"
            val stateLabel = if (needsManualConfirm) "待进软件确认" else "已自动入账"
            NotificationBridge.appendLog(
                packageName = packageName,
                appLabel = parsed.channel,
                title = title,
                content = content,
                status = "$stateLabel ($methodTag)",
                summary = "${parsed.type.label} $formattedAmount · ${parsed.merchant} · ${parsed.category}"
            )
            if (settings.notifyOnRecorded) {
                val notifTitle = if (needsManualConfirm) {
                    "小本本已记录，待进软件确认 (${parsed.channel})"
                } else {
                    "小本本已记账 (${parsed.channel})"
                }
                NotificationBridge.postRecordedConfirmation(
                    context = applicationContext,
                    title = notifTitle,
                    body = "${parsed.merchant} ${parsed.type.label} $formattedAmount · 分类：${parsed.category}"
                )
            }
        }.onFailure { error ->
            NotificationBridge.appendLog(
                packageName = packageName,
                appLabel = parsed.channel,
                title = title,
                content = content,
                status = "入账失败",
                summary = error.localizedMessage ?: "保存失败"
            )
        }
    }

    private companion object {
        const val TAG = "AutoBookkeepingSvc"
        const val DEDUP_WINDOW_MS = 15_000L
    }
}
