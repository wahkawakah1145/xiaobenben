package com.example.data

import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
import com.example.model.ChannelCatalog
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
            put("version", 5)
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
                put("enabledCurrencyCodes", JSONArray().apply {
                    settings.enabledCurrencyCodes.forEach { put(it) }
                })
                put("expenseCategories", JSONArray().apply {
                    CategoryCatalog.activeExpenseCategories(settings).forEach { put(it) }
                })
                put("incomeCategories", JSONArray().apply {
                    CategoryCatalog.activeIncomeCategories(settings).forEach { put(it) }
                })
                put("transferCategories", JSONArray().apply {
                    CategoryCatalog.activeTransferCategories(settings).forEach { put(it) }
                })
                if (settings.categoryIcons.isNotEmpty()) {
                    put("categoryIcons", JSONObject().apply {
                        settings.categoryIcons.forEach { (k, v) ->
                            if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                        }
                    })
                }
                if (settings.categoryParentMap.isNotEmpty()) {
                    put("categoryParentMap", JSONObject().apply {
                        settings.categoryParentMap.forEach { (k, v) ->
                            if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                        }
                    })
                }
                put("categoryShowChildren", settings.categoryShowChildren)
                put("customChannels", JSONArray().apply {
                    ChannelCatalog.activeChannels(settings).forEach { put(it) }
                })
                if (settings.channelParentMap.isNotEmpty()) {
                    put("channelParentMap", JSONObject().apply {
                        settings.channelParentMap.forEach { (k, v) ->
                            if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                        }
                    })
                }
                put("channelShowChildren", settings.channelShowChildren)
                put("billFieldsConfig", JSONArray().apply {
                    com.example.model.BillFieldCatalog.normalizeFields(settings.billFieldsConfig).forEach { field ->
                        put(JSONObject().apply {
                            put("id", field.id)
                            put("label", field.label)
                            put("isBuiltIn", field.isBuiltIn)
                            put("isMajorItem", field.isMajorItem)
                            put("foldedInMore", field.foldedInMore)
                            put("enabled", field.enabled)
                        })
                    }
                })
                put("notificationLogMaxCount", settings.notificationLogMaxCount)
                put("notificationLogMaxDays", settings.notificationLogMaxDays)
                if (settings.dedupExcludedTransactionIds.isNotEmpty()) {
                    put("dedupExcludedTransactionIds", JSONArray().apply {
                        settings.dedupExcludedTransactionIds.distinct().forEach { put(it) }
                    })
                }
                put("monitoredAppsCustomized", settings.monitoredAppsCustomized)
                if (settings.monitoredAppPackages.isNotEmpty()) {
                    put("monitoredAppPackages", JSONArray().apply {
                        settings.monitoredAppPackages.distinct().forEach { put(it) }
                    })
                }
                put("allowOtherFinancialAppsFallback", settings.allowOtherFinancialAppsFallback)
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
                        if (tx.customFields.isNotEmpty()) {
                            put("customFields", JSONObject().apply {
                                tx.customFields.forEach { (k, v) ->
                                    if (k.isNotBlank() && v.isNotBlank()) put(k.take(40), v.take(160))
                                }
                            })
                        }
                        tx.balanceAfterCents?.let { put("balanceAfterCents", it) }
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
                        customFields = o.optJSONObject("customFields")?.let { cfObj ->
                            buildMap {
                                val keys = cfObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next().trim().take(40)
                                    val v = cfObj.optString(k).trim().take(160)
                                    if (k.isNotEmpty() && v.isNotEmpty() && v != "null") put(k, v)
                                }
                            }
                        } ?: emptyMap(),
                        balanceAfterCents = if (o.has("balanceAfterCents") && !o.isNull("balanceAfterCents")) {
                            o.optLong("balanceAfterCents")
                        } else {
                            null
                        },
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
                    .ifBlank { currentSettings.githubUpdateMirror },
                enabledCurrencyCodes = s.optJSONArray("enabledCurrencyCodes")?.let { arr ->
                    buildList(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val c = arr.optString(i).trim()
                            if (c.isNotEmpty()) add(c)
                        }
                    }
                } ?: currentSettings.enabledCurrencyCodes,
                expenseCategories = s.optJSONArray("expenseCategories")?.let { arr ->
                    CategoryCatalog.normalizeCategoryList(
                        buildList(arr.length()) {
                            for (i in 0 until arr.length()) add(arr.optString(i))
                        },
                        currentSettings.expenseCategories
                    )
                } ?: currentSettings.expenseCategories,
                incomeCategories = s.optJSONArray("incomeCategories")?.let { arr ->
                    CategoryCatalog.normalizeCategoryList(
                        buildList(arr.length()) {
                            for (i in 0 until arr.length()) add(arr.optString(i))
                        },
                        currentSettings.incomeCategories
                    )
                } ?: currentSettings.incomeCategories,
                transferCategories = s.optJSONArray("transferCategories")?.let { arr ->
                    CategoryCatalog.normalizeCategoryList(
                        buildList(arr.length()) {
                            for (i in 0 until arr.length()) add(arr.optString(i))
                        },
                        currentSettings.transferCategories
                    )
                } ?: currentSettings.transferCategories,
                categoryIcons = s.optJSONObject("categoryIcons")?.let { obj ->
                    val map = LinkedHashMap<String, String>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next().trim().take(24)
                        val v = obj.optString(k).trim().take(48)
                        if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
                    }
                    map
                } ?: currentSettings.categoryIcons,
                categoryParentMap = s.optJSONObject("categoryParentMap")?.let { obj ->
                    val map = LinkedHashMap<String, String>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next().trim().take(24)
                        val v = obj.optString(k).trim().take(24)
                        if (k.isNotEmpty() && v.isNotEmpty() && k != v) map[k] = v
                    }
                    map
                } ?: currentSettings.categoryParentMap,
                categoryShowChildren = s.optBoolean(
                    "categoryShowChildren",
                    currentSettings.categoryShowChildren
                ),
                customChannels = s.optJSONArray("customChannels")?.let { arr ->
                    ChannelCatalog.normalizeChannelList(
                        buildList(arr.length()) {
                            for (i in 0 until arr.length()) add(arr.optString(i))
                        },
                        currentSettings.customChannels
                    )
                } ?: currentSettings.customChannels,
                channelParentMap = s.optJSONObject("channelParentMap")?.let { obj ->
                    val map = LinkedHashMap<String, String>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next().trim().take(28)
                        val v = obj.optString(k).trim().take(28)
                        if (k.isNotEmpty() && v.isNotEmpty() && !k.equals(v, ignoreCase = true)) map[k] = v
                    }
                    map
                } ?: currentSettings.channelParentMap,
                channelShowChildren = s.optBoolean(
                    "channelShowChildren",
                    currentSettings.channelShowChildren
                ),
                billFieldsConfig = s.optJSONArray("billFieldsConfig")?.let { arr ->
                    val parsed = buildList(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val id = item.optString("id").ifBlank { item.optString("key") }.trim()
                            val label = item.optString("label").trim()
                            if (id.isNotEmpty() && label.isNotEmpty()) {
                                add(
                                    com.example.model.BillFieldConfig(
                                        id = id,
                                        label = label,
                                        isBuiltIn = item.optBoolean("isBuiltIn", false),
                                        isMajorItem = item.optBoolean("isMajorItem", !item.optBoolean("isOptional", true)),
                                        foldedInMore = item.optBoolean("foldedInMore", true),
                                        enabled = item.optBoolean("enabled", true)
                                    )
                                )
                            }
                        }
                    }
                    com.example.model.BillFieldCatalog.normalizeFields(parsed)
                } ?: currentSettings.billFieldsConfig,
                notificationLogMaxCount = s.optInt(
                    "notificationLogMaxCount",
                    currentSettings.notificationLogMaxCount
                ).coerceAtLeast(0),
                notificationLogMaxDays = s.optInt(
                    "notificationLogMaxDays",
                    currentSettings.notificationLogMaxDays
                ).coerceAtLeast(0),
                dedupExcludedTransactionIds = s.optJSONArray("dedupExcludedTransactionIds")?.let { arr ->
                    buildList(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val id = arr.optString(i).trim()
                            if (id.isNotEmpty()) add(id)
                        }
                    }.distinct()
                } ?: currentSettings.dedupExcludedTransactionIds,
                monitoredAppsCustomized = s.optBoolean(
                    "monitoredAppsCustomized",
                    currentSettings.monitoredAppsCustomized
                ),
                monitoredAppPackages = s.optJSONArray("monitoredAppPackages")?.let { arr ->
                    buildList(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val pkg = arr.optString(i).trim()
                            if (pkg.isNotEmpty()) add(pkg)
                        }
                    }.distinct()
                } ?: currentSettings.monitoredAppPackages,
                allowOtherFinancialAppsFallback = s.optBoolean(
                    "allowOtherFinancialAppsFallback",
                    currentSettings.allowOtherFinancialAppsFallback
                )
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
