package com.example.data

import android.content.Context
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CurrencyCatalog
import com.example.model.CustomParsingRule
import com.example.model.ExchangeRateState
import com.example.model.LedgerTransaction
import com.example.model.NotificationInquiry
import com.example.model.ParseMethod
import com.example.model.TransactionType
import com.example.model.WebDavConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LedgerRepository(
    private val dao: LedgerDao,
    private val settingsStore: SettingsDataStore
) {
    constructor(context: Context) : this(
        dao = LedgerDatabase.getInstance(context).ledgerDao(),
        settingsStore = SettingsDataStore(context.applicationContext)
    )

    fun observeTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeAllTransactions().map { list -> list.map { it.toDomain() } }

    fun observeInquiries(): Flow<List<com.example.model.NotificationInquiry>> =
        dao.observeInquiries().map { list -> list.map { it.toDomain() } }

    fun observeParsingRules(): Flow<List<CustomParsingRule>> =
        dao.observeAllRules().map { list -> list.map { it.toDomain() } }

    fun observeSettings(): Flow<BudgetSettings> = settingsStore.budgetSettingsFlow

    fun observeWebDavConfig(): Flow<WebDavConfig> = settingsStore.webDavConfigFlow

    fun observeAppearanceSettings(): Flow<AppearanceSettings> = settingsStore.appearanceSettingsFlow

    fun observeExchangeRates(): Flow<ExchangeRateState> = settingsStore.exchangeRateStateFlow

    fun observeAiConfig(): Flow<com.example.model.AiConfig> = settingsStore.aiConfigFlow

    suspend fun getSettingsOnce(): BudgetSettings = settingsStore.getBudgetSettingsOnce()

    suspend fun getExchangeRatesOnce(): ExchangeRateState = settingsStore.getExchangeRateStateOnce()

    suspend fun getAiConfigOnce(): com.example.model.AiConfig = settingsStore.getAiConfigOnce()

    suspend fun getRulesOnce(): List<CustomParsingRule> =
        dao.getAllRulesOnce().map { it.toDomain() }

    suspend fun getAllTransactionsOnce(): List<LedgerTransaction> =
        dao.getAllTransactionsOnce().map { it.toDomain() }

    suspend fun ensureDefaultRulesSeeded() {
        val count = dao.getRuleCount()
        if (count == 0 && !settingsStore.isRulesSeeded()) {
            val defaults = defaultSeededRules()
            dao.upsertRules(defaults.map { ParsingRuleEntity.fromDomain(it) })
            settingsStore.markRulesSeeded()
        }
    }

    suspend fun resetDefaultRules(): Result<Unit> {
        return try {
            val existing = dao.getAllRulesOnce().map { it.toDomain() }
            val existingIds = existing.map { it.id }.toSet()
            val toInsert = defaultSeededRules().filter { it.id !in existingIds }
            if (toInsert.isNotEmpty()) {
                dao.upsertRules(toInsert.map { ParsingRuleEntity.fromDomain(it) })
            }
            settingsStore.markRulesSeeded()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addInquiry(inquiry: com.example.model.NotificationInquiry): Result<Unit> {
        return try {
            dao.upsertInquiry(NotificationInquiryEntity.fromDomain(inquiry))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dismissInquiry(inquiryId: String): Result<Unit> {
        return try {
            dao.deleteInquiryById(inquiryId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveInquiryAndLearnRule(
        inquiry: com.example.model.NotificationInquiry,
        amountCents: Long,
        currencyCode: String,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        learnRule: Boolean,
        ruleKeyword: String,
        ruleName: String,
        numberFormatPattern: String = com.example.model.NumberFormatStyle.AUTO.name,
        ruleDefaultType: String = "AUTO"
    ): Result<Unit> {
        return try {
            require(amountCents in 1L..1_000_000_000L) { "金额需大于 0 且不超过 1000 万" }
            val now = System.currentTimeMillis()
            val cleanCurrency = CurrencyCatalog.resolve(currencyCode).code
            val cleanMerchant = merchant.trim().ifBlank { category }
            val cleanChannel = channel.trim().ifBlank { "未知渠道" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val rates = settingsStore.getExchangeRateStateOnce().ratesToCny
            val baseCode = CurrencyCatalog.resolve(settings.baseCurrencyCode).code
            val snapshotRate = CurrencyExchangeManager.rateBetween(cleanCurrency, baseCode, rates)

            val tx = LedgerTransaction(
                amountCents = amountCents,
                currencyCode = cleanCurrency,
                type = type.name,
                category = category.trim().ifBlank { "其他" },
                merchant = cleanMerchant,
                channel = cleanChannel,
                cardLastFour = inquiry.cardLastFour,
                sourceApp = com.example.parser.ChinaPaymentCatalog
                    .resolveSourceApp(inquiry.packageName, "${inquiry.title} ${inquiry.content}").name,
                parseMethod = ParseMethod.RULE_ENGINE.name,
                rawNotification = "${inquiry.title}: ${inquiry.content}".take(600),
                note = "经询问确认并学习",
                occurredAtMillis = inquiry.occurredAtMillis,
                confirmed = true,
                recordedBaseCurrencyCode = baseCode,
                recordedRateToBase = snapshotRate,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertTransaction(TransactionEntity.fromDomain(tx))

            if (learnRule && ruleKeyword.trim().isNotBlank()) {
                val cleanKeyword = ruleKeyword.trim().take(200)
                val cleanName = ruleName.trim().ifBlank { "$cleanMerchant · ${cleanCategoryOrType(category)}" }.take(80)
                val existingRule = dao.getAllRulesOnce()
                    .map { it.toDomain() }
                    .firstOrNull {
                        it.keywordPattern.equals(cleanKeyword, ignoreCase = true) ||
                            (it.priority > 0 && it.name.equals(cleanName, ignoreCase = true))
                    }
                val normalizedRuleType = if (ruleDefaultType.equals("AUTO", ignoreCase = true)) {
                    "AUTO"
                } else {
                    TransactionType.fromRaw(ruleDefaultType).name
                }
                val ruleToSave = (existingRule ?: CustomParsingRule()).copy(
                    name = cleanName,
                    packageFilter = existingRule?.packageFilter
                        ?: inquiry.packageName.takeIf { it.isNotBlank() && it != "manual.bench" } ?: "*",
                    keywordPattern = cleanKeyword,
                    channelName = cleanChannel.take(60),
                    defaultCategory = category.trim().ifBlank { "其他" }.take(40),
                    defaultType = normalizedRuleType,
                    merchantOverride = cleanMerchant.take(60),
                    numberFormatPattern = com.example.model.NumberFormatStyle.fromRaw(numberFormatPattern).name,
                    priority = 20,
                    enabled = true,
                    updatedAtMillis = now
                )
                dao.upsertRule(ParsingRuleEntity.fromDomain(ruleToSave))
            }

            dao.deleteInquiryById(inquiry.id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanCategoryOrType(category: String): String = category.ifBlank { "规则" }

    suspend fun addTransaction(tx: LedgerTransaction): Result<String> {
        return try {
            require(tx.amountCents in 1L..1_000_000_000L) { "金额需大于 0 且不超过 1000 万" }
            val now = System.currentTimeMillis()
            val id = tx.id.ifBlank { UUID.randomUUID().toString() }
            val resolvedCurrency = CurrencyCatalog.resolve(tx.currencyCode).code
            val (baseCode, snapshotRate) = if (tx.recordedRateToBase > 0.0) {
                CurrencyCatalog.resolve(tx.recordedBaseCurrencyCode).code to tx.recordedRateToBase
            } else {
                val settings = settingsStore.getBudgetSettingsOnce()
                val rates = settingsStore.getExchangeRateStateOnce().ratesToCny
                val base = CurrencyCatalog.resolve(settings.baseCurrencyCode).code
                base to CurrencyExchangeManager.rateBetween(resolvedCurrency, base, rates)
            }
            val normalized = tx.copy(
                id = id,
                currencyCode = resolvedCurrency,
                merchant = tx.merchant.trim().ifBlank { "未知商户" }.take(120),
                channel = tx.channel.trim().ifBlank { "未知渠道" }.take(60),
                category = tx.category.trim().ifBlank { "其他" }.take(40),
                cardLastFour = tx.cardLastFour?.trim()?.takeIf { it.isNotEmpty() }?.take(8),
                rawNotification = tx.rawNotification?.trim()?.takeIf { it.isNotEmpty() }?.take(600),
                note = tx.note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                occurredAtMillis = tx.occurredAtMillis.coerceAtLeast(1L),
                recordedBaseCurrencyCode = baseCode,
                recordedRateToBase = snapshotRate,
                createdAtMillis = tx.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = now
            )
            dao.upsertTransaction(TransactionEntity.fromDomain(normalized))
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        txId: String,
        amountCents: Long,
        currencyCode: String,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        cardLastFour: String?,
        note: String?,
        occurredAtMillis: Long,
        confirmed: Boolean = true
    ): Result<Unit> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            require(amountCents in 1L..1_000_000_000L) { "金额需大于 0 且不超过 1000 万" }
            val existing = dao.getAllTransactionsOnce().firstOrNull { it.id == txId }
                ?: throw IllegalArgumentException("未找到对应的账单记录")
            val updated = existing.copy(
                amountCents = amountCents,
                currencyCode = CurrencyCatalog.resolve(currencyCode).code,
                type = type.name,
                category = category.trim().ifBlank { "其他" }.take(40),
                merchant = merchant.trim().ifBlank { "未知商户" }.take(120),
                channel = channel.trim().ifBlank { "未知渠道" }.take(60),
                cardLastFour = cardLastFour?.trim()?.takeIf { it.isNotEmpty() }?.take(8),
                note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                occurredAtMillis = occurredAtMillis.coerceAtLeast(1L),
                confirmed = confirmed,
                updatedAtMillis = System.currentTimeMillis()
            )
            dao.upsertTransaction(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmTransaction(txId: String): Result<Unit> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            dao.confirmTransaction(txId, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmAllPendingTransactions(): Result<Unit> {
        return try {
            dao.confirmAllPendingTransactions(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(txId: String): Result<Unit> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            dao.deleteTransactionById(txId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTransactions(txIds: Collection<String>): Result<Int> {
        return try {
            val cleanIds = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleanIds.isEmpty()) return Result.success(0)
            cleanIds.chunked(400).forEach { chunk ->
                dao.deleteTransactionsByIds(chunk)
            }
            Result.success(cleanIds.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchUpdateTransactions(
        txIds: Collection<String>,
        type: TransactionType? = null,
        category: String? = null,
        channel: String? = null,
        merchant: String? = null,
        currencyCode: String? = null,
        note: String? = null
    ): Result<Int> {
        return try {
            val idSet = txIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (idSet.isEmpty()) return Result.success(0)
            val now = System.currentTimeMillis()
            val settings = if (currencyCode != null) settingsStore.getBudgetSettingsOnce() else null
            val rates = if (currencyCode != null) settingsStore.getExchangeRateStateOnce().ratesToCny else null
            val baseCode = settings?.let { CurrencyCatalog.resolve(it.baseCurrencyCode).code }
            val resolvedNewCurrency = currencyCode?.let { CurrencyCatalog.resolve(it).code }

            val existing = dao.getAllTransactionsOnce().filter { it.id in idSet }
            val updated = existing.map { entity ->
                val newCurrency = resolvedNewCurrency ?: entity.currencyCode
                val currencyChanged = resolvedNewCurrency != null && !resolvedNewCurrency.equals(entity.currencyCode, ignoreCase = true)
                val newRecordedBase = if (currencyChanged && baseCode != null) baseCode else entity.recordedBaseCurrencyCode
                val newRecordedRate = if (currencyChanged && baseCode != null && rates != null) {
                    CurrencyExchangeManager.rateBetween(newCurrency, baseCode, rates)
                } else {
                    entity.recordedRateToBase
                }
                entity.copy(
                    type = type?.name ?: entity.type,
                    category = category?.trim()?.takeIf { it.isNotEmpty() }?.take(40) ?: entity.category,
                    channel = channel?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: entity.channel,
                    merchant = merchant?.trim()?.takeIf { it.isNotEmpty() }?.take(120) ?: entity.merchant,
                    currencyCode = newCurrency,
                    note = if (note != null) note.trim().takeIf { it.isNotEmpty() }?.take(200) else entity.note,
                    recordedBaseCurrencyCode = newRecordedBase,
                    recordedRateToBase = newRecordedRate,
                    confirmed = true,
                    updatedAtMillis = now
                )
            }
            if (updated.isNotEmpty()) {
                dao.upsertTransactions(updated)
            }
            Result.success(updated.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importTransactions(items: List<LedgerTransaction>): Result<Int> {
        return try {
            val settings = settingsStore.getBudgetSettingsOnce()
            val rates = settingsStore.getExchangeRateStateOnce().ratesToCny
            val base = CurrencyCatalog.resolve(settings.baseCurrencyCode).code
            val valid = items.filter { it.amountCents in 1L..1_000_000_000L }.map { tx ->
                if (tx.recordedRateToBase > 0.0) {
                    tx
                } else {
                    val code = CurrencyCatalog.resolve(tx.currencyCode).code
                    tx.copy(
                        currencyCode = code,
                        recordedBaseCurrencyCode = base,
                        recordedRateToBase = CurrencyExchangeManager.rateBetween(code, base, rates)
                    )
                }
            }
            if (valid.isNotEmpty()) {
                dao.upsertTransactions(valid.map { TransactionEntity.fromDomain(it) })
            }
            Result.success(valid.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeTransactionsFromBackup(items: List<LedgerTransaction>) {
        if (items.isEmpty()) return
        dao.upsertTransactions(items.map { TransactionEntity.fromDomain(it) })
    }

    suspend fun saveParsingRule(rule: CustomParsingRule): Result<Unit> {
        return try {
            val name = rule.name.trim()
            val keyword = rule.keywordPattern.trim()
            val channel = rule.channelName.trim()
            require(name.isNotEmpty() && keyword.isNotEmpty() && channel.isNotEmpty()) {
                "规则名称、匹配关键词和渠道不能为空"
            }
            val now = System.currentTimeMillis()
            val normalizedType = if (rule.defaultType.equals("AUTO", ignoreCase = true)) {
                "AUTO"
            } else {
                TransactionType.fromRaw(rule.defaultType).name
            }
            val normalized = rule.copy(
                id = rule.id.ifBlank { UUID.randomUUID().toString() },
                name = name.take(80),
                packageFilter = rule.packageFilter.trim().ifBlank { "*" }.take(120),
                keywordPattern = keyword.take(200),
                channelName = channel.take(60),
                defaultCategory = rule.defaultCategory.trim().ifBlank { "自动" }.take(40),
                defaultType = normalizedType,
                merchantOverride = rule.merchantOverride.trim().take(60),
                numberFormatPattern = com.example.model.NumberFormatStyle.fromRaw(rule.numberFormatPattern).name,
                priority = rule.priority,
                createdAtMillis = rule.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = now
            )
            dao.upsertRule(ParsingRuleEntity.fromDomain(normalized))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeRulesFromBackup(rules: List<CustomParsingRule>) {
        if (rules.isEmpty()) return
        dao.upsertRules(rules.map { ParsingRuleEntity.fromDomain(it) })
    }

    suspend fun toggleParsingRule(ruleId: String, enabled: Boolean): Result<Unit> {
        return try {
            dao.setRuleEnabled(ruleId, enabled, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteParsingRule(ruleId: String): Result<Unit> {
        return try {
            dao.deleteRuleById(ruleId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSettings(settings: BudgetSettings): Result<Unit> {
        return try {
            settingsStore.saveBudgetSettings(settings)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveAppearanceSettings(settings: AppearanceSettings): Result<Unit> {
        return try {
            settingsStore.saveAppearanceSettings(settings)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveAiConfig(config: com.example.model.AiConfig): Result<Unit> {
        return try {
            settingsStore.saveAiConfig(config)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveExchangeRates(
        ratesToCny: Map<String, Double>,
        updatedAtMillis: Long,
        status: String,
        dataSource: String,
        rateDateText: String
    ) {
        settingsStore.saveExchangeRates(ratesToCny, updatedAtMillis, status, dataSource, rateDateText)
    }

    suspend fun saveWebDavConfig(config: WebDavConfig): Result<Unit> {
        return try {
            settingsStore.saveWebDavConfig(config)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWebDavSyncStatus(millis: Long, status: String) {
        settingsStore.updateWebDavSyncStatus(millis, status)
    }

    suspend fun markLocalBackupCompleted(millis: Long) {
        settingsStore.markLocalBackupCompleted(millis)
    }

    suspend fun markBackupReminderSent(millis: Long) {
        settingsStore.markBackupReminderSent(millis)
    }

    companion object {
        fun defaultSeededRules(): List<CustomParsingRule> {
            val now = 1700000000000L
            return listOf(
                CustomParsingRule(
                    id = "builtin_alipay",
                    name = "支付宝通用规则",
                    packageFilter = "com.eg.android.AlipayGphone",
                    keywordPattern = "支付宝|花呗|余额宝|付款|收款",
                    channelName = "支付宝",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                CustomParsingRule(
                    id = "builtin_wechat",
                    name = "微信支付通用规则",
                    packageFilter = "com.tencent.mm",
                    keywordPattern = "微信支付|零钱|微信转账|付款|收款",
                    channelName = "微信支付",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                CustomParsingRule(
                    id = "builtin_unionpay",
                    name = "云闪付通用规则",
                    packageFilter = "com.unionpay",
                    keywordPattern = "云闪付|银联|95516",
                    channelName = "云闪付",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                CustomParsingRule(
                    id = "builtin_cmb",
                    name = "招商银行短信/App",
                    packageFilter = "*",
                    keywordPattern = "招商银行|95555",
                    channelName = "招商银行",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                CustomParsingRule(
                    id = "builtin_icbc",
                    name = "工商银行短信/App",
                    packageFilter = "*",
                    keywordPattern = "工商银行|95588",
                    channelName = "工商银行",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                CustomParsingRule(
                    id = "builtin_ccb",
                    name = "建设银行短信/App",
                    packageFilter = "*",
                    keywordPattern = "建设银行|95533",
                    channelName = "建设银行",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                CustomParsingRule(
                    id = "builtin_abc",
                    name = "农业银行短信/App",
                    packageFilter = "*",
                    keywordPattern = "农业银行|95599",
                    channelName = "农业银行",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
                CustomParsingRule(
                    id = "builtin_boc",
                    name = "中国银行短信/App",
                    packageFilter = "*",
                    keywordPattern = "中国银行|95566",
                    channelName = "中国银行",
                    defaultCategory = "自动",
                    defaultType = "AUTO",
                    priority = 0,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }
    }
}
