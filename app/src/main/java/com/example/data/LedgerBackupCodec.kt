package com.example.data

import com.example.model.BudgetSettings
import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LedgerBackupPayload(
    val transactions: List<LedgerTransaction>,
    val parsingRules: List<CustomParsingRule>,
    val settings: BudgetSettings?,
    val exportedAtMillis: Long
)

object LedgerBackupCodec {

    fun encodeBackupJson(
        transactions: List<LedgerTransaction>,
        rules: List<CustomParsingRule>,
        settings: BudgetSettings
    ): String {
        val rootJson = JSONObject().apply {
            put("version", 3)
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
            })
            put("transactions", JSONArray().apply {
                transactions.forEach { tx ->
                    put(JSONObject().apply {
                        put("id", tx.id)
                        put("amountCents", tx.amountCents)
                        put("currencyCode", tx.currencyCode)
                        put("type", tx.type)
                        put("category", tx.category)
                        put("merchant", tx.merchant)
                        put("channel", tx.channel)
                        put("cardLastFour", tx.cardLastFour ?: JSONObject.NULL)
                        put("sourceApp", tx.sourceApp)
                        put("parseMethod", tx.parseMethod)
                        put("rawNotification", tx.rawNotification ?: JSONObject.NULL)
                        put("note", tx.note ?: JSONObject.NULL)
                        put("occurredAtMillis", tx.occurredAtMillis)
                        put("confirmed", tx.confirmed)
                        put("recordedBaseCurrencyCode", tx.recordedBaseCurrencyCode)
                        put("recordedRateToBase", tx.recordedRateToBase)
                        put("createdAtMillis", tx.createdAtMillis)
                        put("updatedAtMillis", tx.updatedAtMillis)
                    })
                }
            })
            put("parsingRules", JSONArray().apply {
                rules.forEach { rule ->
                    put(JSONObject().apply {
                        put("id", rule.id)
                        put("name", rule.name)
                        put("packageFilter", rule.packageFilter)
                        put("keywordPattern", rule.keywordPattern)
                        put("channelName", rule.channelName)
                        put("defaultCategory", rule.defaultCategory)
                        put("defaultType", rule.defaultType)
                        put("merchantOverride", rule.merchantOverride)
                        put("numberFormatPattern", rule.numberFormatPattern)
                        put("priority", rule.priority)
                        put("enabled", rule.enabled)
                        put("createdAtMillis", rule.createdAtMillis)
                        put("updatedAtMillis", rule.updatedAtMillis)
                    })
                }
            })
        }
        return rootJson.toString(2)
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
                ).coerceIn(1, 8760)
            )
        }

        return LedgerBackupPayload(
            transactions = txList,
            parsingRules = ruleList,
            settings = parsedSettings,
            exportedAtMillis = root.optLong("exportedAtMillis", 0L)
        )
    }
}
