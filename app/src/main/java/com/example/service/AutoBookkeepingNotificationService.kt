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
    private val repository by lazy(LazyThreadSafetyMode.NONE) { LedgerRepository(applicationContext) }
    private val aiManager by lazy(LazyThreadSafetyMode.NONE) { com.example.data.AiUnderstandingManager() }

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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_BACKGROUND) {
            val now = System.currentTimeMillis()
            recentFingerprints.entries.removeIf { now - it.value > DEDUP_WINDOW_MS * 2 }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recentFingerprints.clear()
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
        if (recentFingerprints.size > 16) {
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
        repository.ensureDefaultRulesSeeded()
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
        val ignoreRule = ChinaNotificationParser.matchesIgnoreRule(
            packageName = packageName,
            title = title,
            content = content,
            customRules = customRules
        )
        if (ignoreRule != null) {
            NotificationBridge.appendLog(
                packageName = packageName,
                appLabel = appLabel,
                title = title,
                content = content,
                status = "已按忽略规则跳过",
                summary = "命中忽略规则「${ignoreRule.name}」"
            )
            return
        }

        val ruleParsed = ChinaNotificationParser.parse(
            packageName = packageName,
            title = title,
            content = content,
            customRules = customRules,
            enableLocalSemanticPass = false,
            referenceTimeMillis = occurredAt
        )
        val aiConfig = repository.getAiConfigOnce()
        val shouldInvokeAi = aiConfig.providerMode != com.example.model.AiProviderMode.DISABLED && (
            !aiConfig.useAiAsFallbackOnly ||
                ruleParsed == null ||
                !ruleParsed.isFinancialTransaction ||
                ruleParsed.needsInquiry
            )
        val parsed = if (shouldInvokeAi) {
            val aiResult = aiManager.enhanceOrParseNotification(
                context = applicationContext,
                config = aiConfig,
                packageName = packageName,
                title = title,
                content = content,
                fallbackParsed = ruleParsed,
                availableRules = customRules
            ).getOrNull()
            aiResult ?: ruleParsed
        } else {
            ruleParsed
        }

        val resolvedInquiryTime = parsed?.occurredAtMillis
            ?: ChinaNotificationParser.extractOccurredAtMillis("$title\n$content", occurredAt)
            ?: occurredAt

        if (parsed == null || !parsed.isFinancialTransaction || parsed.amountCents <= 0L) {
            if (ChinaNotificationParser.hasUncertainFinancialCue(packageName, title, content)) {
                val inquiry = com.example.model.NotificationInquiry(
                    packageName = packageName,
                    appLabel = appLabel,
                    title = title,
                    content = content,
                    suggestedAmountCents = 0L,
                    suggestedCurrencyCode = ChinaNotificationParser.detectCurrencyCode("$title $content"),
                    suggestedType = com.example.model.TransactionType.EXPENSE.name,
                    suggestedCategory = "其他",
                    suggestedMerchant = title.ifBlank { appLabel }.take(40),
                    suggestedChannel = appLabel,
                    reason = "未能自动提取金额或模板，等待确认并生成规则",
                    occurredAtMillis = resolvedInquiryTime
                )
                repository.addInquiry(inquiry)
                NotificationBridge.appendLog(
                    packageName = packageName,
                    appLabel = appLabel,
                    title = title,
                    content = content,
                    status = "待询问确认",
                    summary = inquiry.reason
                )
                if (settings.notifyOnRecorded) {
                    NotificationBridge.postRecordedConfirmation(
                        context = applicationContext,
                        title = "小本本需要你确认一条通知 ($appLabel)",
                        body = "${title.ifBlank { content }} — 点击进入软件补充并学习规则"
                    )
                }
            } else {
                NotificationBridge.appendLog(
                    packageName = packageName,
                    appLabel = appLabel,
                    title = title,
                    content = content,
                    status = "未能识别金额",
                    summary = "非动账通知"
                )
            }
            return
        }

        if (parsed.needsInquiry) {
            val inquiry = com.example.model.NotificationInquiry(
                packageName = packageName,
                appLabel = appLabel,
                title = title,
                content = content,
                suggestedAmountCents = parsed.amountCents,
                suggestedCurrencyCode = parsed.currencyCode,
                suggestedType = parsed.type.name,
                suggestedCategory = parsed.category,
                suggestedMerchant = parsed.merchant,
                suggestedChannel = parsed.channel,
                cardLastFour = parsed.cardLastFour,
                reason = parsed.inquiryReason.ifBlank { "信息不够明确，请确认后学习为规则" },
                occurredAtMillis = resolvedInquiryTime
            )
            repository.addInquiry(inquiry)
            val currencySymbol = CurrencyCatalog.resolve(parsed.currencyCode).symbol
            val formattedAmount = "$currencySymbol%.2f".format(parsed.amountCents / 100.0)
            NotificationBridge.appendLog(
                packageName = packageName,
                appLabel = parsed.channel,
                title = title,
                content = content,
                status = "待询问确认",
                summary = "$formattedAmount · ${inquiry.reason}"
            )
            if (settings.notifyOnRecorded) {
                NotificationBridge.postRecordedConfirmation(
                    context = applicationContext,
                    title = "小本本不确定这笔账单 ($formattedAmount)",
                    body = "${inquiry.reason} — 点击进入确认并自动更新规则"
                )
            }
            return
        }

        val needsManualConfirm = settings.requireManualConfirm
        val resolvedTxOccurredAt = parsed.occurredAtMillis ?: occurredAt
        val compactRaw = "$title: $content"
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(220)
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
            rawNotification = compactRaw,
            note = parsed.reasoning.take(120),
            occurredAtMillis = resolvedTxOccurredAt,
            confirmed = !needsManualConfirm
        )

        val saveResult = repository.addTransaction(tx)
        saveResult.onSuccess {
            val currencySymbol = CurrencyCatalog.resolve(parsed.currencyCode).symbol
            val formattedAmount = "$currencySymbol%.2f".format(parsed.amountCents / 100.0)
            val methodTag = if (parsed.parseMethod == ParseMethod.LOCAL_NLP) "AI理解" else "规则匹配"
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
