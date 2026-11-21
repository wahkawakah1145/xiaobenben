package com.example.data

import android.content.Context
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CurrencyCatalog
import com.example.model.CustomParsingRule
import com.example.model.ExchangeRateState
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import com.example.model.NotificationInquiry
import com.example.model.ParseMethod
import com.example.model.StorageFootprintInfo
import com.example.model.StorageOptimizeResult
import com.example.model.TransactionType
import com.example.model.WebDavConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LedgerRepository(
    private val dao: LedgerDao,
    private val settingsStore: SettingsDataStore,
    private val database: LedgerDatabase? = null
) {
    constructor(context: Context) : this(
        dao = LedgerDatabase.getInstance(context).ledgerDao(),
        settingsStore = SettingsDataStore(context.applicationContext),
        database = LedgerDatabase.getInstance(context)
    )

    fun observeTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeAllTransactions().map { list -> list.map { it.toDomain() } }

    fun observeBooks(): Flow<List<LedgerBook>> =
        dao.observeAllBooks().map { list -> list.map { it.toDomain() } }

    fun observeBookLinks(): Flow<List<TransactionBookLink>> =
        dao.observeAllBookRefs().map { list -> list.map { it.toDomain() } }

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

    suspend fun getAllBooksOnce(): List<LedgerBook> =
        dao.getAllBooksOnce().map { it.toDomain() }

    suspend fun getAllBookLinksOnce(): List<TransactionBookLink> =
        dao.getAllBookRefsOnce().map { it.toDomain() }

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
        ruleDefaultType: String = "AUTO",
        occurredAtMillis: Long = inquiry.occurredAtMillis
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
                rawNotification = compactText("${inquiry.title}: ${inquiry.content}", MAX_RAW_NOTIFICATION_LENGTH),
                note = "经询问确认并学习",
                occurredAtMillis = occurredAtMillis.coerceAtLeast(1L),
                confirmed = true,
                recordedBaseCurrencyCode = baseCode,
                recordedRateToBase = snapshotRate,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertTransaction(TransactionEntity.fromDomain(tx))

            if (learnRule && (ruleKeyword.trim().isNotBlank() || inquiry.content.isNotBlank())) {
                val rawSample = "${inquiry.title}\n${inquiry.content}".trim()
                val templateSig = com.example.parser.ChinaNotificationParser
                    .buildNotificationTemplateSignature(rawSample, keepMerchantSlotExact = false)
                val cleanKeyword = ruleKeyword.trim().ifBlank { cleanChannel }.take(200)
                val cleanName = ruleName.trim().ifBlank { "$cleanMerchant · ${cleanCategoryOrType(category)}" }.take(80)
                val existingRules = dao.getAllRulesOnce().map { it.toDomain() }
                val existingRule = existingRules.firstOrNull {
                    (templateSig.isNotBlank() && it.templateSignature.equals(templateSig, ignoreCase = true) && !it.isIgnoreRule) ||
                        (it.keywordPattern.equals(cleanKeyword, ignoreCase = true) && !it.isIgnoreRule) ||
                        (it.priority > 0 && it.name.equals(cleanName, ignoreCase = true) && !it.isIgnoreRule)
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
                    templateSignature = templateSig.ifBlank { existingRule?.templateSignature ?: "" },
                    channelName = cleanChannel.take(60),
                    defaultCategory = category.trim().ifBlank { "其他" }.take(40),
                    defaultType = normalizedRuleType,
                    merchantOverride = cleanMerchant.take(60),
                    numberFormatPattern = com.example.model.NumberFormatStyle.fromRaw(numberFormatPattern).name,
                    priority = maxOf(existingRule?.priority ?: 25, 25),
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
                rawNotification = compactText(tx.rawNotification, MAX_RAW_NOTIFICATION_LENGTH),
                note = compactText(tx.note, MAX_NOTE_LENGTH),
                tradeType = compactText(tx.tradeType, 60),
                productName = compactText(tx.productName, 120),
                tradeStatus = compactText(tx.tradeStatus, 40),
                transactionNo = compactText(tx.transactionNo, 80),
                merchantOrderNo = compactText(tx.merchantOrderNo, 80),
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
        confirmed: Boolean = true,
        learnFormatTemplate: Boolean = true,
        tradeType: String? = null,
        productName: String? = null,
        tradeStatus: String? = null,
        transactionNo: String? = null,
        merchantOrderNo: String? = null,
        updateExtendedFields: Boolean = false
    ): Result<Boolean> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            require(amountCents in 1L..1_000_000_000L) { "金额需大于 0 且不超过 1000 万" }
            val existing = dao.getAllTransactionsOnce().firstOrNull { it.id == txId }
                ?: throw IllegalArgumentException("未找到对应的账单记录")
            val now = System.currentTimeMillis()
            val cleanCategory = category.trim().ifBlank { "其他" }.take(40)
            val cleanMerchant = merchant.trim().ifBlank { "未知商户" }.take(120)
            val cleanChannel = channel.trim().ifBlank { "未知渠道" }.take(60)
            val updated = existing.copy(
                amountCents = amountCents,
                currencyCode = CurrencyCatalog.resolve(currencyCode).code,
                type = type.name,
                category = cleanCategory,
                merchant = cleanMerchant,
                channel = cleanChannel,
                cardLastFour = cardLastFour?.trim()?.takeIf { it.isNotEmpty() }?.take(8),
                note = compactText(note, MAX_NOTE_LENGTH),
                tradeType = if (updateExtendedFields) compactText(tradeType, 60) else existing.tradeType,
                productName = if (updateExtendedFields) compactText(productName, 120) else existing.productName,
                tradeStatus = if (updateExtendedFields) compactText(tradeStatus, 40) else existing.tradeStatus,
                transactionNo = if (updateExtendedFields) compactText(transactionNo, 80) else existing.transactionNo,
                merchantOrderNo = if (updateExtendedFields) compactText(merchantOrderNo, 80) else existing.merchantOrderNo,
                occurredAtMillis = occurredAtMillis.coerceAtLeast(1L),
                confirmed = confirmed,
                updatedAtMillis = now
            )
            dao.upsertTransaction(updated)

            var templateLearned = false
            val rawText = existing.rawNotification?.trim().orEmpty()
            if (learnFormatTemplate && rawText.isNotBlank()) {
                templateLearned = upsertTemplateFromEditedTransaction(
                    rawNotification = rawText,
                    sourceAppRaw = existing.sourceApp,
                    editedType = type,
                    editedCategory = cleanCategory,
                    editedMerchant = cleanMerchant,
                    editedChannel = cleanChannel,
                    previousMerchant = existing.merchant,
                    timestampMillis = now
                )
            }
            Result.success(templateLearned)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordIgnoredNotificationRule(
        packageName: String,
        title: String,
        content: String
    ): Result<CustomParsingRule> {
        return try {
            val rawCombined = "$title\n$content".trim()
            val sig = com.example.parser.ChinaNotificationParser
                .buildNotificationTemplateSignature(rawCombined, keepMerchantSlotExact = true)
            val keyword = com.example.parser.ChinaNotificationParser.suggestRuleKeyword(
                title = title,
                content = content,
                merchant = "",
                channel = ""
            ).ifBlank { title.ifBlank { content }.take(20) }.take(200)
            val now = System.currentTimeMillis()
            val existingRules = dao.getAllRulesOnce().map { it.toDomain() }
            val existingIgnore = existingRules.firstOrNull { rule ->
                rule.isIgnoreRule && (
                    (sig.isNotBlank() && rule.templateSignature.equals(sig, ignoreCase = true)) ||
                        rule.keywordPattern.equals(keyword, ignoreCase = true)
                    )
            }
            val cleanTitle = title.trim().ifBlank { keyword }.take(22)
            val stableId = existingIgnore?.id ?: ("ign_" + UUID.nameUUIDFromBytes((sig.ifBlank { keyword }).toByteArray()).toString())
            val rule = (existingIgnore ?: CustomParsingRule(id = stableId)).copy(
                name = "忽略非账单 · $cleanTitle".take(80),
                packageFilter = packageName.takeIf { it.isNotBlank() && it != "manual.bench" } ?: "*",
                keywordPattern = keyword,
                templateSignature = sig,
                channelName = "已忽略",
                defaultCategory = "非账单",
                defaultType = "IGNORE",
                merchantOverride = "",
                numberFormatPattern = com.example.model.NumberFormatStyle.AUTO.name,
                priority = 100,
                enabled = true,
                createdAtMillis = existingIgnore?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            dao.upsertRule(ParsingRuleEntity.fromDomain(rule))
            Result.success(rule)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun upsertTemplateFromEditedTransaction(
        rawNotification: String,
        sourceAppRaw: String,
        editedType: TransactionType,
        editedCategory: String,
        editedMerchant: String,
        editedChannel: String,
        previousMerchant: String,
        timestampMillis: Long
    ): Boolean {
        val signature = com.example.parser.ChinaNotificationParser
            .buildNotificationTemplateSignature(rawNotification, keepMerchantSlotExact = false)
        if (signature.isBlank()) return false

        val keyword = com.example.parser.ChinaNotificationParser.suggestRuleKeyword(
            title = "",
            content = rawNotification,
            merchant = editedMerchant,
            channel = editedChannel
        ).ifBlank { editedChannel }.take(200)

        val numberStyle = com.example.parser.ChinaNotificationParser.detectNumberFormatStyle(rawNotification)
        val hasDynamicMerchantSlot = signature.contains("<MERCHANT>")
        val merchantChanged = !editedMerchant.equals(previousMerchant, ignoreCase = true)
        val isPlaceholderMerchant = editedMerchant == "${editedChannel}消费" ||
            editedMerchant == "${editedChannel}收款" ||
            editedMerchant == "${editedChannel}转账" ||
            editedMerchant == "未知商户"
        val merchantOverride = when {
            isPlaceholderMerchant -> ""
            !hasDynamicMerchantSlot || merchantChanged -> editedMerchant.take(60)
            else -> ""
        }

        val packageHint = when (com.example.model.SourceApp.fromRaw(sourceAppRaw)) {
            com.example.model.SourceApp.ALIPAY -> "com.eg.android.AlipayGphone"
            com.example.model.SourceApp.WECHAT -> "com.tencent.mm"
            com.example.model.SourceApp.UNIONPAY -> "com.unionpay"
            else -> "*"
        }

        val existingRules = dao.getAllRulesOnce().map { it.toDomain() }
        val existingTemplate = existingRules.firstOrNull {
            !it.isIgnoreRule && it.templateSignature.equals(signature, ignoreCase = true)
        }
        val stableId = existingTemplate?.id
            ?: ("tpl_" + UUID.nameUUIDFromBytes(signature.toByteArray()).toString())

        val rule = (existingTemplate ?: CustomParsingRule(id = stableId)).copy(
            name = "格式模版 · $editedChannel ($editedCategory)".take(80),
            packageFilter = existingTemplate?.packageFilter ?: packageHint,
            keywordPattern = keyword,
            templateSignature = signature,
            channelName = editedChannel.take(60),
            defaultCategory = editedCategory.take(40),
            defaultType = editedType.name,
            merchantOverride = merchantOverride,
            numberFormatPattern = numberStyle.name,
            priority = maxOf(existingTemplate?.priority ?: 40, 40),
            enabled = true,
            createdAtMillis = existingTemplate?.createdAtMillis ?: timestampMillis,
            updatedAtMillis = timestampMillis
        )
        dao.upsertRule(ParsingRuleEntity.fromDomain(rule))
        return true
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
            dao.deleteRefsByTransactionId(txId)
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
                dao.deleteRefsByTransactionIds(chunk)
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
            val isIgnore = rule.defaultType.equals("IGNORE", ignoreCase = true)
            val channel = if (isIgnore && rule.channelName.trim().isEmpty()) "已忽略" else rule.channelName.trim()
            require(name.isNotEmpty() && (keyword.isNotEmpty() || rule.templateSignature.isNotBlank()) && channel.isNotEmpty()) {
                "规则名称、匹配关键词和渠道不能为空"
            }
            val now = System.currentTimeMillis()
            val normalizedType = when {
                isIgnore -> "IGNORE"
                rule.defaultType.equals("AUTO", ignoreCase = true) -> "AUTO"
                else -> TransactionType.fromRaw(rule.defaultType).name
            }
            val normalized = rule.copy(
                id = rule.id.ifBlank { UUID.randomUUID().toString() },
                name = name.take(80),
                packageFilter = rule.packageFilter.trim().ifBlank { "*" }.take(120),
                keywordPattern = keyword.ifBlank { name }.take(200),
                templateSignature = rule.templateSignature.trim().take(180),
                channelName = channel.take(60),
                defaultCategory = if (isIgnore) "非账单" else rule.defaultCategory.trim().ifBlank { "自动" }.take(40),
                defaultType = normalizedType,
                merchantOverride = if (isIgnore) "" else rule.merchantOverride.trim().take(60),
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


    suspend fun saveBook(book: LedgerBook): Result<LedgerBook> {
        return try {
            val cleanName = book.name.trim()
            require(cleanName.isNotEmpty()) { "账本名称不能为空" }
            val now = System.currentTimeMillis()
            val normalized = book.copy(
                id = book.id.ifBlank { UUID.randomUUID().toString() },
                name = cleanName.take(40),
                description = book.description.trim().take(120),
                createdAtMillis = book.createdAtMillis.takeIf { it > 0L } ?: now,
                updatedAtMillis = now
            )
            dao.upsertBook(LedgerBookEntity.fromDomain(normalized))
            Result.success(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBook(bookId: String): Result<Unit> {
        return try {
            require(bookId.isNotBlank()) { "账本 ID 不能为空" }
            dao.deleteRefsByBookId(bookId)
            dao.deleteBookById(bookId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addTransactionsToBooks(
        txIds: Collection<String>,
        bookIds: Collection<String>
    ): Result<Int> {
        return try {
            val cleanTx = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val cleanBooks = bookIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleanTx.isEmpty() || cleanBooks.isEmpty()) return Result.success(0)
            val now = System.currentTimeMillis()
            val refs = buildList(cleanTx.size * cleanBooks.size) {
                cleanTx.forEach { txId ->
                    cleanBooks.forEach { bookId ->
                        add(TransactionBookCrossRefEntity(txId, bookId, now))
                    }
                }
            }
            refs.chunked(400).forEach { chunk ->
                dao.insertBookRefs(chunk)
            }
            Result.success(refs.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeTransactionsFromBook(
        txIds: Collection<String>,
        bookId: String
    ): Result<Int> {
        return try {
            val cleanTx = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleanTx.isEmpty() || bookId.isBlank()) return Result.success(0)
            cleanTx.chunked(400).forEach { chunk ->
                dao.deleteRefsByBookAndTransactionIds(bookId, chunk)
            }
            Result.success(cleanTx.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setBooksForTransaction(
        txId: String,
        bookIds: Collection<String>
    ): Result<Unit> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            dao.deleteRefsByTransactionId(txId)
            val cleanBooks = bookIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleanBooks.isNotEmpty()) {
                val now = System.currentTimeMillis()
                dao.insertBookRefs(cleanBooks.map { TransactionBookCrossRefEntity(txId, it, now) })
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun mergeBooksFromBackup(
        books: List<LedgerBook>,
        links: List<TransactionBookLink>
    ) {
        if (books.isNotEmpty()) {
            dao.upsertBooks(books.map { LedgerBookEntity.fromDomain(it) })
        }
        if (links.isNotEmpty()) {
            val existingTxIds = dao.getAllTransactionsOnce().mapTo(HashSet()) { it.id }
            val existingBookIds = dao.getAllBooksOnce().mapTo(HashSet()) { it.id }
            val validRefs = links
                .filter { it.transactionId in existingTxIds && it.bookId in existingBookIds }
                .map { TransactionBookCrossRefEntity.fromDomain(it) }
            validRefs.chunked(400).forEach { chunk ->
                dao.insertBookRefs(chunk)
            }
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

    suspend fun inspectStorageFootprint(context: Context): StorageFootprintInfo = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val dbInstance = database ?: LedgerDatabase.getInstance(appCtx)
        val actualPath = runCatching { dbInstance.openHelper.writableDatabase.path }.getOrNull()
        val dbFile = if (!actualPath.isNullOrBlank()) {
            File(actualPath)
        } else {
            appCtx.getDatabasePath("smart_ledger.db")
        }
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        val prefsFile = File(appCtx.filesDir, "datastore/smart_ledger_settings.preferences_pb")
        val modelsDir = File(appCtx.filesDir, "local_ai_models")

        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        val walBytes = (if (walFile.exists()) walFile.length() else 0L) +
            (if (shmFile.exists()) shmFile.length() else 0L)
        val prefsBytes = if (prefsFile.exists()) prefsFile.length() else 0L
        val aiModelBytes = directorySizeBytes(modelsDir)
        val cacheBytes = directorySizeBytes(appCtx.cacheDir)

        StorageFootprintInfo(
            databaseBytes = dbBytes,
            walBytes = walBytes,
            preferencesBytes = prefsBytes,
            aiModelBytes = aiModelBytes,
            cacheBytes = cacheBytes,
            transactionCount = dao.countTransactions(),
            rawNotificationCount = dao.countRawNotifications(),
            inquiryCount = dao.countInquiries()
        )
    }

    suspend fun optimizeStorage(
        context: Context,
        pruneRawLogsOlderThanDays: Int? = null
    ): Result<StorageOptimizeResult> {
        return try {
            val appCtx = context.applicationContext
            val before = inspectStorageFootprint(appCtx)
            val now = System.currentTimeMillis()
            val prunedInquiries = dao.pruneStaleInquiries(now - 30L * 86_400_000L)
            val prunedRawLogs = if (pruneRawLogsOlderThanDays != null && pruneRawLogsOlderThanDays >= 0) {
                val cutoff = if (pruneRawLogsOlderThanDays == 0) {
                    Long.MAX_VALUE
                } else {
                    now - pruneRawLogsOlderThanDays.toLong() * 86_400_000L
                }
                dao.pruneOldRawNotifications(cutoff)
            } else {
                0
            }

            val clearedCacheBytes = withContext(Dispatchers.IO) {
                var freed = 0L
                appCtx.cacheDir?.listFiles()?.forEach { child ->
                    freed += directorySizeBytes(child)
                    child.deleteRecursively()
                }
                val modelsDir = File(appCtx.filesDir, "local_ai_models")
                modelsDir.walkBottomUp().forEach { f ->
                    if (f.isFile && f.name.endsWith(".tmp")) {
                        freed += f.length()
                        f.delete()
                    }
                }
                freed
            }

            (database ?: LedgerDatabase.getInstance(appCtx)).vacuumAndCheckpoint()
            val after = inspectStorageFootprint(appCtx)

            Result.success(
                StorageOptimizeResult(
                    beforeBytes = before.totalUserDataBytes,
                    afterBytes = after.totalUserDataBytes,
                    prunedRawLogs = prunedRawLogs,
                    prunedInquiries = prunedInquiries,
                    clearedCacheBytes = clearedCacheBytes
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compactText(raw: String?, maxLength: Int): String? {
        val cleaned = raw?.replace(Regex("\\s+"), " ")?.trim() ?: return null
        if (cleaned.isEmpty()) return null
        return cleaned.take(maxLength)
    }

    private fun directorySizeBytes(target: File?): Long {
        if (target == null || !target.exists()) return 0L
        if (target.isFile) return target.length()
        return target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        const val MAX_RAW_NOTIFICATION_LENGTH = 220
        const val MAX_NOTE_LENGTH = 120
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
