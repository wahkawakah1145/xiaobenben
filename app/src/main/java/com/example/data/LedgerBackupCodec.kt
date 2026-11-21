package com.example.data

import com.example.model.BudgetSettings
import com.example.model.CustomParsingRule
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LedgerBackupPayload(
    val transactions: List<LedgerTransaction>,
    val parsingRules: List<CustomParsingRule>,
    val settings: BudgetSettings?,
    val exportedAtMillis: Long,
    val books: List<LedgerBook> = emptyList(),
    val bookLinks: List<TransactionBookLink> = emptyList()
)

object LedgerBackupCodec {

    fun encodeBackupJson(
        transactions: List<LedgerTransaction>,
        rules: List<CustomParsingRule>,
        settings: BudgetSettings,
        books: List<LedgerBook> = emptyList(),
        bookLinks: List<TransactionBookLink> = emptyList()
    ): String {
        val rootJson = JSONObject().apply {
            put("version", 4)
            put("appId", "com.live150yo.lvshuiqingshan")
            put("exportedAtMillis", System.currentTimeMillis())
            put("settings", JSONObject().apply {
                put("monthlyBudgetCents", settings.monthlyBudgetCents)
                put("autoRecordEnabled", settings.autoRecordEnabled)
                put("requireManualConfirm", settings.requireManualConfirm)
                put("smartSemanticEnabled", settings.smartSemanticEnabled)
                put("notifyOnRecorded", settings.notifyOnRecorded)
                put("baseCurrencyCode", settings.baseCurrencyCode)
                put("backupReminderEnabled", settings.backupReminderEnabled)
                put("backupReminderIntervalHours", settings.backupReminderIntervalHours)
                put("exchangeRateSource", settings.exchangeRateSource)
                if (settings.customExchangeRateUrl.isNotBlank()) {
                    put("customExchangeRateUrl", settings.customExchangeRateUrl)
                }
                put("githubUpdateMirror", settings.githubUpdateMirror)
            })
            put("transactions", JSONArray().apply {
                transactions.forEach { tx ->
                    put(JSONObject().apply {
                        put("id", tx.id)
                        put("amountCents", tx.amountCents)
                        if (!tx.currencyCode.equals("CNY", ignoreCase = true)) {
                            put("currencyCode", tx.currencyCode)
                        }
                        put("type", tx.type)
                        put("category", tx.category)
                        put("merchant", tx.merchant)
                        put("channel", tx.channel)
                        tx.cardLastFour?.takeIf { it.isNotBlank() }?.let { put("cardLastFour", it) }
                        if (tx.sourceApp != "MANUAL") put("sourceApp", tx.sourceApp)
                        if (tx.parseMethod != "MANUAL") put("parseMethod", tx.parseMethod)
                        tx.rawNotification?.takeIf { it.isNotBlank() }?.let {
                            put("rawNotification", it.take(220))
                        }
                        tx.note?.takeIf { it.isNotBlank() }?.let { put("note", it.take(160)) }
                        tx.tradeType?.takeIf { it.isNotBlank() }?.let { put("tradeType", it.take(60)) }
                        tx.productName?.takeIf { it.isNotBlank() }?.let { put("productName", it.take(120)) }
                        tx.tradeStatus?.takeIf { it.isNotBlank() }?.let { put("tradeStatus", it.take(40)) }
                        tx.transactionNo?.takeIf { it.isNotBlank() }?.let { put("transactionNo", it.take(80)) }
                        tx.merchantOrderNo?.takeIf { it.isNotBlank() }?.let { put("merchantOrderNo", it.take(80)) }
                        put("occurredAtMillis", tx.occurredAtMillis)
                        if (!tx.confirmed) put("confirmed", false)
                        if (!tx.recordedBaseCurrencyCode.equals("CNY", ignoreCase = true)) {
                            put("recordedBaseCurrencyCode", tx.recordedBaseCurrencyCode)
                        }
                        if (tx.recordedRateToBase > 0.0) {
                            put("recordedRateToBase", tx.recordedRateToBase)
                        }
                        if (tx.createdAtMillis != tx.occurredAtMillis) {
                            put("createdAtMillis", tx.createdAtMillis)
                        }
                        if (tx.updatedAtMillis != tx.occurredAtMillis) {
                            put("updatedAtMillis", tx.updatedAtMillis)
                        }
                    })
                }
            })
            put("parsingRules", JSONArray().apply {
                rules.forEach { rule ->
                    put(JSONObject().apply {
                        put("id", rule.id)
                        put("name", rule.name)
                        if (rule.packageFilter != "*") put("packageFilter", rule.packageFilter)
                        put("keywordPattern", rule.keywordPattern)
                        if (rule.templateSignature.isNotBlank()) put("templateSignature", rule.templateSignature)
                        put("channelName", rule.channelName)
                        put("defaultCategory", rule.defaultCategory)
                        put("defaultType", rule.defaultType)
                        if (rule.merchantOverride.isNotBlank()) put("merchantOverride", rule.merchantOverride)
                        if (rule.numberFormatPattern != "AUTO") put("numberFormatPattern", rule.numberFormatPattern)
                        if (rule.priority != 10) put("priority", rule.priority)
                        if (!rule.enabled) put("enabled", false)
                        put("createdAtMillis", rule.createdAtMillis)
                        put("updatedAtMillis", rule.updatedAtMillis)
                    })
                }
            })
            if (books.isNotEmpty()) {
                put("books", JSONArray().apply {
                    books.forEach { book ->
                        put(JSONObject().apply {
                            put("id", book.id)
                            put("name", book.name)
                            if (book.description.isNotBlank()) put("description", book.description)
                            put("colorArgb", book.colorArgb)
                            put("createdAtMillis", book.createdAtMillis)
                            put("updatedAtMillis", book.updatedAtMillis)
                        })
                    }
                })
            }
            if (bookLinks.isNotEmpty()) {
                put("bookLinks", JSONArray().apply {
                    bookLinks.forEach { link ->
                        put(JSONObject().apply {
                            put("transactionId", link.transactionId)
                            put("bookId", link.bookId)
                            put("addedAtMillis", link.addedAtMillis)
                        })
                    }
                })
            }
        }
        return rootJson.toString()
    }

    fun decodeBackupJson(
        rawBody: String,
        currentSettings: BudgetSettings = BudgetSettings()
    ): LedgerBackupPayload {
        val root = JSONObject(rawBody)
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        val txList = buildList {
            for (i in 0 until txArray.length()) {
                val o = txArray.getJSONObject(i)
                val amount = o.optLong("amountCents", 0L)
                if (amount <= 0L) continue
                add(
                    LedgerTransaction(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        amountCents = amount,
                        currencyCode = o.optString("currencyCode", "CNY").ifBlank { "CNY" },
                        type = o.optString("type", "EXPENSE"),
                        category = o.optString("category", "其他"),
                        merchant = o.optString("merchant", "未知商户"),
                        channel = o.optString("channel", "未知渠道"),
                        cardLastFour = o.optString("cardLastFour").takeIf { it.isNotBlank() && it != "null" },
                        sourceApp = o.optString("sourceApp", "MANUAL"),
                        parseMethod = o.optString("parseMethod", "MANUAL"),
                        rawNotification = o.optString("rawNotification").takeIf { it.isNotBlank() && it != "null" },
                        note = o.optString("note").takeIf { it.isNotBlank() && it != "null" },
                        tradeType = o.optString("tradeType").takeIf { it.isNotBlank() && it != "null" },
                        productName = o.optString("productName").takeIf { it.isNotBlank() && it != "null" },
                        tradeStatus = o.optString("tradeStatus").takeIf { it.isNotBlank() && it != "null" },
                        transactionNo = o.optString("transactionNo").takeIf { it.isNotBlank() && it != "null" },
                        merchantOrderNo = o.optString("merchantOrderNo").takeIf { it.isNotBlank() && it != "null" },
                        occurredAtMillis = o.optLong("occurredAtMillis", System.currentTimeMillis()),
                        confirmed = o.optBoolean("confirmed", true),
                        recordedBaseCurrencyCode = o.optString("recordedBaseCurrencyCode", "CNY").ifBlank { "CNY" },
                        recordedRateToBase = o.optDouble("recordedRateToBase", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }

        val ruleArray = root.optJSONArray("parsingRules") ?: JSONArray()
        val ruleList = buildList {
            for (i in 0 until ruleArray.length()) {
                val o = ruleArray.getJSONObject(i)
                add(
                    CustomParsingRule(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = o.optString("name", "导入规则"),
                        packageFilter = o.optString("packageFilter", "*"),
                        keywordPattern = o.optString("keywordPattern", ""),
                        templateSignature = o.optString("templateSignature", ""),
                        channelName = o.optString("channelName", "其他"),
                        defaultCategory = o.optString("defaultCategory", "其他"),
                        defaultType = o.optString("defaultType", "EXPENSE"),
                        merchantOverride = o.optString("merchantOverride", ""),
                        numberFormatPattern = o.optString("numberFormatPattern", "AUTO").ifBlank { "AUTO" },
                        priority = o.optInt("priority", 10),
                        enabled = o.optBoolean("enabled", true),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }

        val booksArray = root.optJSONArray("books") ?: JSONArray()
        val bookList = buildList {
            for (i in 0 until booksArray.length()) {
                val o = booksArray.getJSONObject(i)
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                add(
                    LedgerBook(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name.take(40),
                        description = o.optString("description", "").take(120),
                        colorArgb = o.optLong("colorArgb", 0xFF0F6B54L),
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }

        val linksArray = root.optJSONArray("bookLinks") ?: JSONArray()
        val linkList = buildList {
            for (i in 0 until linksArray.length()) {
                val o = linksArray.getJSONObject(i)
                val txId = o.optString("transactionId").trim()
                val bookId = o.optString("bookId").trim()
                if (txId.isEmpty() || bookId.isEmpty()) continue
                add(
                    TransactionBookLink(
                        transactionId = txId,
                        bookId = bookId,
                        addedAtMillis = o.optLong("addedAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }

        val parsedSettings = root.optJSONObject("settings")?.let { s ->
            currentSettings.copy(
                monthlyBudgetCents = s.optLong("monthlyBudgetCents", currentSettings.monthlyBudgetCents),
                autoRecordEnabled = s.optBoolean("autoRecordEnabled", currentSettings.autoRecordEnabled),
                requireManualConfirm = s.optBoolean("requireManualConfirm", currentSettings.requireManualConfirm),
                smartSemanticEnabled = s.optBoolean("smartSemanticEnabled", currentSettings.smartSemanticEnabled),
                notifyOnRecorded = s.optBoolean("notifyOnRecorded", currentSettings.notifyOnRecorded),
                baseCurrencyCode = s.optString("baseCurrencyCode", currentSettings.baseCurrencyCode)
                    .ifBlank { currentSettings.baseCurrencyCode },
                backupReminderEnabled = s.optBoolean("backupReminderEnabled", currentSettings.backupReminderEnabled),
                backupReminderIntervalHours = s.optInt(
                    "backupReminderIntervalHours",
                    currentSettings.backupReminderIntervalHours
                ).coerceIn(1, 8760),
                exchangeRateSource = s.optString("exchangeRateSource", currentSettings.exchangeRateSource)
                    .ifBlank { currentSettings.exchangeRateSource },
                customExchangeRateUrl = s.optString("customExchangeRateUrl", currentSettings.customExchangeRateUrl),
                githubUpdateMirror = s.optString("githubUpdateMirror", currentSettings.githubUpdateMirror)
                    .ifBlank { currentSettings.githubUpdateMirror }
            )
        }

        return LedgerBackupPayload(
            transactions = txList,
            parsingRules = ruleList,
            settings = parsedSettings,
            exportedAtMillis = root.optLong("exportedAtMillis", 0L),
            books = bookList,
            bookLinks = linkList
        )
    }
}
