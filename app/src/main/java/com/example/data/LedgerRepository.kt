package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteBlobTooBigException
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class CategorySyncResult(
    val updatedTransactions: Int = 0,
    val updatedRules: Int = 0,
    val aiPromptSynced: Boolean = false
)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTransactions(): Flow<List<LedgerTransaction>> =
        dao.observeTransactionsSignature()
            .mapLatest { loadAllTransactionsChunked() }
            .flowOn(Dispatchers.IO)

    fun observeBooks(): Flow<List<LedgerBook>> =
        dao.observeAllBooks().map { list -> list.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBookLinks(): Flow<List<TransactionBookLink>> =
        dao.observeBookRefsSignature()
            .mapLatest { loadAllBookLinksChunked() }
            .flowOn(Dispatchers.IO)

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

    suspend fun getAppearanceSettingsOnce(): AppearanceSettings = settingsStore.getAppearanceSettingsOnce()

    suspend fun getExchangeRatesOnce(): ExchangeRateState = settingsStore.getExchangeRateStateOnce()

    suspend fun getAiConfigOnce(): com.example.model.AiConfig = settingsStore.getAiConfigOnce()

    suspend fun getRulesOnce(): List<CustomParsingRule> =
        dao.getAllRulesOnce().map { it.toDomain() }

    suspend fun getAllTransactionsOnce(): List<LedgerTransaction> = loadAllTransactionsChunked()

    suspend fun getTransactionsByIdsOnce(txIds: Collection<String>): List<LedgerTransaction> =
        withContext(Dispatchers.IO) {
            val cleanIds = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleanIds.isEmpty()) return@withContext emptyList()
            val result = ArrayList<LedgerTransaction>(cleanIds.size)
            cleanIds.chunked(200).forEach { chunk ->
                result.addAll(dao.getTransactionsByIds(chunk).map { it.toDomain() })
            }
            result
        }

    suspend fun getAllBooksOnce(): List<LedgerBook> =
        dao.getAllBooksOnce().map { it.toDomain() }

    suspend fun getAllBookLinksOnce(): List<TransactionBookLink> = loadAllBookLinksChunked()

    /**
     * Reads transactions in bounded SQLite pages so a single CursorWindow (2 MB fixed native
     * buffer on Android) never overflows even with thousands of records containing long raw text.
     */
    internal suspend fun loadAllTransactionsChunked(
        initialPageSize: Int = DEFAULT_TRANSACTION_PAGE_SIZE
    ): List<LedgerTransaction> = withContext(Dispatchers.IO) {
        val all = ArrayList<LedgerTransaction>()
        var offset = 0
        var pageSize = initialPageSize.coerceIn(10, 400)
        while (true) {
            val page = readTransactionPageSafe(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            for (entity in page) {
                all.add(entity.toDomain())
            }
            offset += page.size
            if (page.size < pageSize) break
            // If we previously shrank pageSize due to an exceptionally large row, gently recover.
            if (pageSize < initialPageSize) {
                pageSize = (pageSize * 2).coerceAtMost(initialPageSize)
            }
        }
        all
    }

    private suspend fun readTransactionPageSafe(limit: Int, offset: Int): List<TransactionEntity> {
        var attemptLimit = limit.coerceAtLeast(1)
        while (true) {
            try {
                return dao.getTransactionsPage(limit = attemptLimit, offset = offset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val isCursorWindowOverflow = e is SQLiteBlobTooBigException ||
                    e is IllegalStateException ||
                    e.message?.contains("CursorWindow", ignoreCase = true) == true
                if (!isCursorWindowOverflow || attemptLimit <= 1) {
                    throw e
                }
                attemptLimit = when {
                    attemptLimit > 40 -> 40
                    attemptLimit > 10 -> 10
                    else -> 1
                }
            }
        }
    }

    private suspend fun loadAllBookLinksChunked(
        pageSize: Int = DEFAULT_BOOK_LINK_PAGE_SIZE
    ): List<TransactionBookLink> = withContext(Dispatchers.IO) {
        val all = ArrayList<TransactionBookLink>()
        var offset = 0
        val safePageSize = pageSize.coerceIn(50, 1000)
        while (true) {
            val page = dao.getBookRefsPage(limit = safePageSize, offset = offset)
            if (page.isEmpty()) break
            for (entity in page) {
                all.add(entity.toDomain())
            }
            offset += page.size
            if (page.size < safePageSize) break
        }
        all
    }

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
                customFields = sanitizeCustomFields(tx.customFields),
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

    private fun sanitizeCustomFields(raw: Map<String, String>): Map<String, String> {
        if (raw.isEmpty()) return emptyMap()
        val clean = LinkedHashMap<String, String>()
        raw.forEach { (k, v) ->
            val ck = k.trim().take(40)
            val cv = v.trim().take(160)
            if (ck.isNotEmpty() && cv.isNotEmpty()) clean[ck] = cv
        }
        return clean
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
        updateExtendedFields: Boolean = false,
        customFields: Map<String, String>? = null
    ): Result<Boolean> {
        return try {
            require(txId.isNotBlank()) { "账单 ID 不能为空" }
            require(amountCents in 1L..1_000_000_000L) { "金额需大于 0 且不超过 1000 万" }
            val existing = dao.getTransactionById(txId)
                ?: throw IllegalArgumentException("未找到对应的账单记录")
            val now = System.currentTimeMillis()
            val cleanCategory = category.trim().ifBlank { "其他" }.take(40)
            val cleanMerchant = merchant.trim().ifBlank { "未知商户" }.take(120)
            val cleanChannel = channel.trim().ifBlank { "未知渠道" }.take(60)
            val nextCustomFieldsJson = when {
                customFields != null -> TransactionEntity.encodeCustomFieldsJson(sanitizeCustomFields(customFields))
                else -> existing.customFieldsJson
            }
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
                customFieldsJson = nextCustomFieldsJson,
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

    data class NonBillMarkUndoSnapshot(
        val transaction: LedgerTransaction,
        val bookLinks: List<TransactionBookLink> = emptyList(),
        val createdOrUpdatedRule: CustomParsingRule? = null,
        val previousRuleBeforeOverwrite: CustomParsingRule? = null
    )

    suspend fun recordIgnoredNotificationRuleWithPrevious(
        packageName: String,
        title: String,
        content: String
    ): Result<Pair<CustomParsingRule, CustomParsingRule?>> {
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
            Result.success(rule to existingIgnore)
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
    ): Result<CustomParsingRule> = recordIgnoredNotificationRuleWithPrevious(
        packageName = packageName,
        title = title,
        content = content
    ).map { it.first }

    suspend fun markTransactionAsNonBillWithUndoSnapshot(tx: LedgerTransaction): Result<NonBillMarkUndoSnapshot> {
        return try {
            val raw = tx.rawNotification?.trim().orEmpty()
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val title = when {
                lines.size >= 2 -> lines.first()
                tx.merchant.isNotBlank() -> tx.merchant
                else -> tx.channel
            }
            val content = when {
                lines.size >= 2 -> lines.drop(1).joinToString(" ")
                raw.isNotEmpty() -> raw
                else -> listOfNotNull(
                    tx.merchant.takeIf { it.isNotBlank() },
                    tx.productName?.takeIf { it.isNotBlank() },
                    tx.note?.takeIf { it.isNotBlank() }
                ).joinToString(" ")
            }
            val packageHint = when (com.example.model.SourceApp.fromRaw(tx.sourceApp)) {
                com.example.model.SourceApp.ALIPAY -> "com.eg.android.AlipayGphone"
                com.example.model.SourceApp.WECHAT -> "com.tencent.mm"
                com.example.model.SourceApp.UNIONPAY -> "com.unionpay"
                else -> "*"
            }
            val bookLinks = getBookLinksForTransactionsOnce(listOf(tx.id))
            val rulePair = if (title.isNotBlank() || content.isNotBlank()) {
                recordIgnoredNotificationRuleWithPrevious(
                    packageName = packageHint,
                    title = title,
                    content = content
                ).getOrNull()
            } else {
                null
            }
            dao.deleteRefsByTransactionId(tx.id)
            dao.deleteTransactionById(tx.id)
            pruneDedupExcludedIds(setOf(tx.id))
            Result.success(
                NonBillMarkUndoSnapshot(
                    transaction = tx,
                    bookLinks = bookLinks,
                    createdOrUpdatedRule = rulePair?.first,
                    previousRuleBeforeOverwrite = rulePair?.second
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markTransactionAsNonBill(tx: LedgerTransaction): Result<CustomParsingRule?> {
        return markTransactionAsNonBillWithUndoSnapshot(tx).map { it.createdOrUpdatedRule }
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
            pruneDedupExcludedIds(setOf(txId))
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
            pruneDedupExcludedIds(cleanIds.toHashSet())
            Result.success(cleanIds.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookLinksForTransactionsOnce(txIds: Collection<String>): List<TransactionBookLink> {
        val cleanIds = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanIds.isEmpty()) return emptyList()
        return buildList {
            cleanIds.chunked(400).forEach { chunk ->
                addAll(dao.getBookRefsByTransactionIds(chunk).map { it.toDomain() })
            }
        }
    }

    suspend fun restoreBookLinks(links: List<TransactionBookLink>) {
        if (links.isEmpty()) return
        val existingTxIds = dao.getAllTransactionIdsOnce().toHashSet()
        val existingBookIds = dao.getAllBooksOnce().mapTo(HashSet()) { it.id }
        val valid = links
            .filter { it.transactionId in existingTxIds && it.bookId in existingBookIds }
            .map { TransactionBookCrossRefEntity.fromDomain(it) }
        valid.chunked(400).forEach { chunk ->
            dao.insertBookRefs(chunk)
        }
    }

    suspend fun restoreInquiry(inquiry: NotificationInquiry) {
        dao.upsertInquiry(NotificationInquiryEntity.fromDomain(inquiry))
    }

    suspend fun excludeTransactionsFromDedup(txIds: Collection<String>): Result<Int> {
        return try {
            val clean = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (clean.isEmpty()) return Result.success(0)
            val settings = settingsStore.getBudgetSettingsOnce()
            val merged = (settings.dedupExcludedTransactionIds + clean).distinct()
            val addedCount = (merged.size - settings.dedupExcludedTransactionIds.distinct().size).coerceAtLeast(0)
            settingsStore.saveBudgetSettings(settings.copy(dedupExcludedTransactionIds = merged))
            Result.success(addedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreExcludedDedupTransactions(txIds: Collection<String>): Result<Int> {
        return try {
            val removeSet = txIds.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
            if (removeSet.isEmpty()) return Result.success(0)
            val settings = settingsStore.getBudgetSettingsOnce()
            val remaining = settings.dedupExcludedTransactionIds.filterNot { it in removeSet }
            val removed = (settings.dedupExcludedTransactionIds.size - remaining.size).coerceAtLeast(0)
            if (removed > 0) {
                settingsStore.saveBudgetSettings(settings.copy(dedupExcludedTransactionIds = remaining))
            }
            Result.success(removed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun pruneDedupExcludedIds(removedIds: Set<String>) {
        if (removedIds.isEmpty()) return
        val settings = settingsStore.getBudgetSettingsOnce()
        if (settings.dedupExcludedTransactionIds.isEmpty()) return
        val filtered = settings.dedupExcludedTransactionIds.filterNot { it in removedIds }
        if (filtered.size != settings.dedupExcludedTransactionIds.size) {
            settingsStore.saveBudgetSettings(settings.copy(dedupExcludedTransactionIds = filtered))
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

            val existing = buildList {
                idSet.chunked(200).forEach { chunk ->
                    addAll(dao.getTransactionsByIds(chunk))
                }
            }
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
            val existingTxIds = dao.getAllTransactionIdsOnce().toHashSet()
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

    data class ChannelSyncResult(
        val updatedTransactions: Int = 0,
        val updatedRules: Int = 0,
        val aiPromptSynced: Boolean = false
    )

    suspend fun addCustomChannel(
        rawName: String,
        syncAiPrompt: Boolean = true
    ): Result<ChannelSyncResult> {
        return try {
            val cleanName = rawName.trim().take(28)
            require(cleanName.isNotEmpty()) { "渠道名称不能为空" }
            require(cleanName != "自动" && cleanName != "未知渠道" && cleanName != "已忽略") { "该名称为系统保留词，请换一个" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val cur = com.example.model.ChannelCatalog.activeChannels(settings)
            require(cur.none { it.equals(cleanName, ignoreCase = true) }) { "该渠道已存在" }
            val updatedSettings = settings.copy(customChannels = cur + cleanName)
            settingsStore.saveBudgetSettings(updatedSettings)
            var aiSynced = false
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForChannelChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = null,
                    newName = cleanName,
                    allActiveChannels = com.example.model.ChannelCatalog.activeChannels(updatedSettings),
                    allActiveCategories = CategoryCatalog.allActiveCategories(updatedSettings)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
                aiSynced = true
            }
            Result.success(ChannelSyncResult(aiPromptSynced = aiSynced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameChannel(
        oldName: String,
        rawNewName: String,
        syncTransactions: Boolean = true,
        syncRules: Boolean = true,
        syncAiPrompt: Boolean = true
    ): Result<ChannelSyncResult> {
        return try {
            val cleanOld = oldName.trim()
            val cleanNew = rawNewName.trim().take(28)
            require(cleanOld.isNotEmpty() && cleanNew.isNotEmpty()) { "渠道名称不能为空" }
            require(!cleanOld.equals(cleanNew, ignoreCase = true)) { "新渠道名称与原名称相同" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val cur = com.example.model.ChannelCatalog.activeChannels(settings)
            val replaced = if (cur.any { it.equals(cleanOld, ignoreCase = true) }) {
                cur.map { if (it.equals(cleanOld, ignoreCase = true)) cleanNew else it }.distinct()
            } else {
                (cur + cleanNew).distinct()
            }
            val updatedChannelParentMap = buildMap<String, String> {
                settings.channelParentMap.forEach { (k, v) ->
                    val nk = if (k.equals(cleanOld, ignoreCase = true)) cleanNew else k
                    val nv = if (v.equals(cleanOld, ignoreCase = true)) cleanNew else v
                    if (!nk.equals(nv, ignoreCase = true)) put(nk, nv)
                }
            }
            val updatedSettings = settings.copy(
                customChannels = replaced,
                channelParentMap = updatedChannelParentMap
            )
            settingsStore.saveBudgetSettings(updatedSettings)

            val now = System.currentTimeMillis()
            var updatedTxCount = 0
            if (syncTransactions) {
                val allTxs = getAllTransactionsOnce()
                val changed = allTxs.mapNotNull { tx ->
                    val nextChannel = com.example.model.ChannelCatalog.replaceChannelToken(tx.channel, cleanOld, cleanNew)
                    if (nextChannel != tx.channel) {
                        TransactionEntity.fromDomain(tx.copy(channel = nextChannel, updatedAtMillis = now))
                    } else {
                        null
                    }
                }
                if (changed.isNotEmpty()) {
                    changed.chunked(300).forEach { dao.upsertTransactions(it) }
                    updatedTxCount = changed.size
                }
            }
            var updatedRulesCount = 0
            if (syncRules) {
                val allRules = dao.getAllRulesOnce().map { it.toDomain() }
                val changedRules = allRules.mapNotNull { rule ->
                    if (rule.isIgnoreRule) return@mapNotNull null
                    val nextChannel = com.example.model.ChannelCatalog.replaceChannelToken(rule.channelName, cleanOld, cleanNew)
                    if (nextChannel != rule.channelName) {
                        ParsingRuleEntity.fromDomain(rule.copy(channelName = nextChannel, updatedAtMillis = now))
                    } else {
                        null
                    }
                }
                if (changedRules.isNotEmpty()) {
                    dao.upsertRules(changedRules)
                    updatedRulesCount = changedRules.size
                }
            }
            var aiSynced = false
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForChannelChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = cleanOld,
                    newName = cleanNew,
                    allActiveChannels = com.example.model.ChannelCatalog.activeChannels(updatedSettings),
                    allActiveCategories = CategoryCatalog.allActiveCategories(updatedSettings)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
                aiSynced = true
            }
            Result.success(
                ChannelSyncResult(
                    updatedTransactions = updatedTxCount,
                    updatedRules = updatedRulesCount,
                    aiPromptSynced = aiSynced
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChannel(
        targetName: String,
        fallbackChannel: String = "其他",
        syncTransactions: Boolean = true,
        syncRules: Boolean = true,
        syncAiPrompt: Boolean = true
    ): Result<ChannelSyncResult> {
        return try {
            val cleanTarget = targetName.trim()
            val cleanFallback = fallbackChannel.trim().ifBlank { "其他" }.take(28)
            require(cleanTarget.isNotEmpty()) { "渠道名称不能为空" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val cur = com.example.model.ChannelCatalog.activeChannels(settings)
            val updatedChannels = if (cur.any { it.equals(cleanTarget, ignoreCase = true) }) {
                require(cur.size > 1) { "至少需保留 1 个渠道" }
                cur.filterNot { it.equals(cleanTarget, ignoreCase = true) }
            } else {
                cur
            }
            val cleanedParentMap = settings.channelParentMap
                .filterNot { (k, v) ->
                    k.equals(cleanTarget, ignoreCase = true) || v.equals(cleanTarget, ignoreCase = true)
                }
            val updatedSettings = settings.copy(
                customChannels = updatedChannels,
                channelParentMap = cleanedParentMap
            )
            settingsStore.saveBudgetSettings(updatedSettings)

            val now = System.currentTimeMillis()
            var updatedTxCount = 0
            if (syncTransactions && !cleanFallback.equals(cleanTarget, ignoreCase = true)) {
                val allTxs = getAllTransactionsOnce()
                val changed = allTxs.mapNotNull { tx ->
                    val nextChannel = com.example.model.ChannelCatalog.replaceChannelToken(tx.channel, cleanTarget, cleanFallback)
                    if (nextChannel != tx.channel) {
                        TransactionEntity.fromDomain(tx.copy(channel = nextChannel, updatedAtMillis = now))
                    } else {
                        null
                    }
                }
                if (changed.isNotEmpty()) {
                    changed.chunked(300).forEach { dao.upsertTransactions(it) }
                    updatedTxCount = changed.size
                }
            }
            var updatedRulesCount = 0
            if (syncRules && !cleanFallback.equals(cleanTarget, ignoreCase = true)) {
                val allRules = dao.getAllRulesOnce().map { it.toDomain() }
                val changedRules = allRules.mapNotNull { rule ->
                    if (rule.isIgnoreRule) return@mapNotNull null
                    val nextChannel = com.example.model.ChannelCatalog.replaceChannelToken(rule.channelName, cleanTarget, cleanFallback)
                    if (nextChannel != rule.channelName) {
                        ParsingRuleEntity.fromDomain(rule.copy(channelName = nextChannel, updatedAtMillis = now))
                    } else {
                        null
                    }
                }
                if (changedRules.isNotEmpty()) {
                    dao.upsertRules(changedRules)
                    updatedRulesCount = changedRules.size
                }
            }
            var aiSynced = false
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForChannelChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = cleanTarget,
                    newName = null,
                    allActiveChannels = com.example.model.ChannelCatalog.activeChannels(updatedSettings),
                    allActiveCategories = CategoryCatalog.allActiveCategories(updatedSettings)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
                aiSynced = true
            }
            Result.success(
                ChannelSyncResult(
                    updatedTransactions = updatedTxCount,
                    updatedRules = updatedRulesCount,
                    aiPromptSynced = aiSynced
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetChannelsToDefault(syncAiPrompt: Boolean = true): Result<Unit> {
        return try {
            val settings = settingsStore.getBudgetSettingsOnce()
            val reset = settings.copy(
                customChannels = com.example.model.ChannelCatalog.defaultChannels,
                channelParentMap = com.example.model.ChannelCatalog.defaultChannelParentMap
            )
            settingsStore.saveBudgetSettings(reset)
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForChannelChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = null,
                    newName = null,
                    allActiveChannels = com.example.model.ChannelCatalog.defaultChannels,
                    allActiveCategories = CategoryCatalog.allActiveCategories(reset)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reorderChannels(orderedNames: List<String>): Result<Unit> {
        return try {
            val clean = orderedNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (clean.isEmpty()) return Result.success(Unit)
            val settings = settingsStore.getBudgetSettingsOnce()
            val existing = com.example.model.ChannelCatalog.activeChannels(settings)
            val merged = (clean + existing.filterNot { name -> clean.any { it.equals(name, ignoreCase = true) } }).distinct()
            settingsStore.saveBudgetSettings(settings.copy(customChannels = merged))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setChannelParent(
        childName: String,
        parentName: String?
    ): Result<Unit> {
        return try {
            val cleanChild = childName.trim()
            val cleanParent = parentName?.trim()?.takeIf { it.isNotEmpty() }
            require(cleanChild.isNotEmpty()) { "渠道名称不能为空" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedMap = settings.channelParentMap.toMutableMap()
            if (cleanParent == null) {
                updatedMap.remove(cleanChild)
            } else {
                require(com.example.model.ChannelCatalog.canAssignParent(cleanChild, cleanParent, updatedMap)) {
                    "不能将渠道归属给自身或形成循环归属"
                }
                updatedMap[cleanChild] = cleanParent
            }
            val currentChannels = com.example.model.ChannelCatalog.activeChannels(settings)
            val nextChannels = buildList {
                addAll(currentChannels)
                if (cleanChild !in this) add(cleanChild)
                if (cleanParent != null && cleanParent !in this) add(cleanParent)
            }
            settingsStore.saveBudgetSettings(
                settings.copy(
                    customChannels = nextChannels,
                    channelParentMap = updatedMap
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchSetChannelParent(
        childNames: Collection<String>,
        parentName: String?
    ): Result<Int> {
        return try {
            val cleanParent = parentName?.trim()?.takeIf { it.isNotEmpty() }
            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedMap = settings.channelParentMap.toMutableMap()
            var count = 0
            val extraChannels = LinkedHashSet<String>()
            if (cleanParent != null) extraChannels.add(cleanParent)
            childNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { child ->
                if (cleanParent == null) {
                    if (updatedMap.remove(child) != null) count++
                } else if (com.example.model.ChannelCatalog.canAssignParent(child, cleanParent, updatedMap)) {
                    updatedMap[child] = cleanParent
                    extraChannels.add(child)
                    count++
                }
            }
            val nextChannels = (com.example.model.ChannelCatalog.activeChannels(settings) + extraChannels).distinct()
            settingsStore.saveBudgetSettings(
                settings.copy(
                    customChannels = nextChannels,
                    channelParentMap = updatedMap
                )
            )
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setChannelShowChildren(showChildren: Boolean): Result<Unit> {
        return try {
            val settings = settingsStore.getBudgetSettingsOnce()
            settingsStore.saveBudgetSettings(settings.copy(channelShowChildren = showChildren))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun consolidateChannelChildrenToParent(
        parentName: String? = null,
        syncTransactions: Boolean = true,
        syncRules: Boolean = true
    ): Result<ChannelSyncResult> {
        return try {
            val settings = settingsStore.getBudgetSettingsOnce()
            val parentMap = settings.channelParentMap
            if (parentMap.isEmpty()) return Result.success(ChannelSyncResult())
            val cleanTargetParent = parentName?.trim()?.takeIf { it.isNotEmpty() }
            val targetPairs = parentMap.entries.mapNotNull { (child, _) ->
                val root = com.example.model.ChannelCatalog.resolveRootParent(child, parentMap)
                if (root == child) return@mapNotNull null
                if (cleanTargetParent != null && !root.equals(cleanTargetParent, ignoreCase = true) &&
                    !parentMap[child].equals(cleanTargetParent, ignoreCase = true)
                ) {
                    return@mapNotNull null
                }
                child to (cleanTargetParent ?: root)
            }
            if (targetPairs.isEmpty()) return Result.success(ChannelSyncResult())
            val now = System.currentTimeMillis()
            var updatedTxCount = 0
            if (syncTransactions) {
                val allTxs = getAllTransactionsOnce()
                val changed = allTxs.mapNotNull { tx ->
                    var curChannel = tx.channel
                    targetPairs.forEach { (child, target) ->
                        curChannel = com.example.model.ChannelCatalog.replaceChannelToken(curChannel, child, target)
                    }
                    if (curChannel != tx.channel) {
                        TransactionEntity.fromDomain(tx.copy(channel = curChannel, updatedAtMillis = now))
                    } else {
                        null
                    }
                }
                if (changed.isNotEmpty()) {
                    changed.chunked(300).forEach { dao.upsertTransactions(it) }
                    updatedTxCount = changed.size
                }
            }
            var updatedRulesCount = 0
            if (syncRules) {
                val allRules = dao.getAllRulesOnce().map { it.toDomain() }
                val changedRules = allRules.mapNotNull { rule ->
                    if (rule.isIgnoreRule) return@mapNotNull null
                    var curChannel = rule.channelName
                    targetPairs.forEach { (child, target) ->
                        curChannel = com.example.model.ChannelCatalog.replaceChannelToken(curChannel, child, target)
                    }
                    if (curChannel != rule.channelName) {
                        ParsingRuleEntity.fromDomain(rule.copy(channelName = curChannel, updatedAtMillis = now))
                    } else {
                        null
                    }
                }
                if (changedRules.isNotEmpty()) {
                    dao.upsertRules(changedRules)
                    updatedRulesCount = changedRules.size
                }
            }
            Result.success(
                ChannelSyncResult(
                    updatedTransactions = updatedTxCount,
                    updatedRules = updatedRulesCount,
                    aiPromptSynced = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCategoryIcon(
        categoryName: String,
        iconKey: String?
    ): Result<Unit> {
        return try {
            val cleanName = categoryName.trim().take(24)
            require(cleanName.isNotEmpty()) { "分类名称不能为空" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedIcons = settings.categoryIcons.toMutableMap()
            val cleanKey = iconKey?.trim()?.take(48).orEmpty()
            if (cleanKey.isEmpty()) {
                updatedIcons.remove(cleanName)
            } else {
                updatedIcons[cleanName] = cleanKey
            }
            settingsStore.saveBudgetSettings(settings.copy(categoryIcons = updatedIcons))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addCustomCategory(
        type: TransactionType,
        rawName: String,
        syncAiPrompt: Boolean = true,
        iconKey: String? = null
    ): Result<CategorySyncResult> {
        return try {
            val cleanName = rawName.trim().take(24)
            require(cleanName.isNotEmpty()) { "分类名称不能为空" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedIcons = settings.categoryIcons.toMutableMap().apply {
                val cleanKey = iconKey?.trim()?.take(48).orEmpty()
                if (cleanKey.isNotEmpty()) put(cleanName, cleanKey)
            }
            val updatedSettings = when (type) {
                TransactionType.EXPENSE -> {
                    val cur = CategoryCatalog.activeExpenseCategories(settings)
                    require(cur.none { it == cleanName }) { "该支出分类已存在" }
                    settings.copy(expenseCategories = cur + cleanName, categoryIcons = updatedIcons)
                }
                TransactionType.INCOME -> {
                    val cur = CategoryCatalog.activeIncomeCategories(settings)
                    require(cur.none { it == cleanName }) { "该收入分类已存在" }
                    settings.copy(incomeCategories = cur + cleanName, categoryIcons = updatedIcons)
                }
                TransactionType.TRANSFER -> {
                    val cur = CategoryCatalog.activeTransferCategories(settings)
                    require(cur.none { it == cleanName }) { "该转账分类已存在" }
                    settings.copy(transferCategories = cur + cleanName, categoryIcons = updatedIcons)
                }
            }
            settingsStore.saveBudgetSettings(updatedSettings)
            var aiSynced = false
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForCategoryChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = null,
                    newName = cleanName,
                    allActiveCategories = CategoryCatalog.allActiveCategories(updatedSettings)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
                aiSynced = true
            }
            Result.success(CategorySyncResult(aiPromptSynced = aiSynced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameCategory(
        type: TransactionType,
        oldName: String,
        rawNewName: String,
        syncTransactions: Boolean = true,
        syncRules: Boolean = true,
        syncAiPrompt: Boolean = true
    ): Result<CategorySyncResult> {
        return try {
            val cleanOld = oldName.trim()
            val cleanNew = rawNewName.trim().take(24)
            require(cleanOld.isNotEmpty() && cleanNew.isNotEmpty()) { "分类名称不能为空" }
            require(cleanOld != cleanNew) { "新分类名称与原名称相同" }

            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedIcons = settings.categoryIcons.toMutableMap().apply {
                val prevIcon = remove(cleanOld)
                if (!prevIcon.isNullOrBlank() && !containsKey(cleanNew)) {
                    put(cleanNew, prevIcon)
                }
            }
            val updatedParentMap = buildMap<String, String> {
                settings.categoryParentMap.forEach { (k, v) ->
                    val nk = if (k == cleanOld) cleanNew else k
                    val nv = if (v == cleanOld) cleanNew else v
                    if (nk != nv) put(nk, nv)
                }
            }
            val updatedSettings = when (type) {
                TransactionType.EXPENSE -> {
                    val cur = CategoryCatalog.activeExpenseCategories(settings)
                    val replaced = if (cleanOld in cur) {
                        cur.map { if (it == cleanOld) cleanNew else it }.distinct()
                    } else {
                        (cur + cleanNew).distinct()
                    }
                    settings.copy(expenseCategories = replaced, categoryIcons = updatedIcons, categoryParentMap = updatedParentMap)
                }
                TransactionType.INCOME -> {
                    val cur = CategoryCatalog.activeIncomeCategories(settings)
                    val replaced = if (cleanOld in cur) {
                        cur.map { if (it == cleanOld) cleanNew else it }.distinct()
                    } else {
                        (cur + cleanNew).distinct()
                    }
                    settings.copy(incomeCategories = replaced, categoryIcons = updatedIcons, categoryParentMap = updatedParentMap)
                }
                TransactionType.TRANSFER -> {
                    val cur = CategoryCatalog.activeTransferCategories(settings)
                    val replaced = if (cleanOld in cur) {
                        cur.map { if (it == cleanOld) cleanNew else it }.distinct()
                    } else {
                        (cur + cleanNew).distinct()
                    }
                    settings.copy(transferCategories = replaced, categoryIcons = updatedIcons, categoryParentMap = updatedParentMap)
                }
            }
            settingsStore.saveBudgetSettings(updatedSettings)

            val now = System.currentTimeMillis()
            val updatedTx = if (syncTransactions) {
                dao.renameTransactionCategory(cleanOld, cleanNew, now)
            } else {
                0
            }
            val updatedRules = if (syncRules) {
                dao.renameRuleCategory(cleanOld, cleanNew, now)
            } else {
                0
            }
            var aiSynced = false
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForCategoryChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = cleanOld,
                    newName = cleanNew,
                    allActiveCategories = CategoryCatalog.allActiveCategories(updatedSettings)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
                aiSynced = true
            }
            Result.success(
                CategorySyncResult(
                    updatedTransactions = updatedTx,
                    updatedRules = updatedRules,
                    aiPromptSynced = aiSynced
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(
        type: TransactionType,
        targetName: String,
        fallbackCategory: String = "其他",
        syncTransactions: Boolean = true,
        syncRules: Boolean = true,
        syncAiPrompt: Boolean = true
    ): Result<CategorySyncResult> {
        return try {
            val cleanTarget = targetName.trim()
            val cleanFallback = fallbackCategory.trim().ifBlank { "其他" }.take(24)
            require(cleanTarget.isNotEmpty()) { "分类名称不能为空" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedSettings = when (type) {
                TransactionType.EXPENSE -> {
                    val cur = CategoryCatalog.activeExpenseCategories(settings)
                    if (cleanTarget in cur) {
                        require(cur.size > 1) { "至少需保留 1 个支出分类" }
                        settings.copy(expenseCategories = cur.filterNot { it == cleanTarget })
                    } else {
                        settings
                    }
                }
                TransactionType.INCOME -> {
                    val cur = CategoryCatalog.activeIncomeCategories(settings)
                    if (cleanTarget in cur) {
                        require(cur.size > 1) { "至少需保留 1 个收入分类" }
                        settings.copy(incomeCategories = cur.filterNot { it == cleanTarget })
                    } else {
                        settings
                    }
                }
                TransactionType.TRANSFER -> {
                    val cur = CategoryCatalog.activeTransferCategories(settings)
                    if (cleanTarget in cur) {
                        require(cur.size > 1) { "至少需保留 1 个转账分类" }
                        settings.copy(transferCategories = cur.filterNot { it == cleanTarget })
                    } else {
                        settings
                    }
                }
            }
            val remainingCategories = CategoryCatalog.allActiveCategories(updatedSettings)
            val cleanedIcons = if (cleanTarget !in remainingCategories) {
                updatedSettings.categoryIcons - cleanTarget
            } else {
                updatedSettings.categoryIcons
            }
            val cleanedParentMap = if (cleanTarget !in remainingCategories) {
                updatedSettings.categoryParentMap.filterNot { (k, v) -> k == cleanTarget || v == cleanTarget }
            } else {
                updatedSettings.categoryParentMap
            }
            settingsStore.saveBudgetSettings(
                updatedSettings.copy(
                    categoryIcons = cleanedIcons,
                    categoryParentMap = cleanedParentMap
                )
            )
            val now = System.currentTimeMillis()
            val updatedTx = if (syncTransactions && cleanFallback != cleanTarget) {
                dao.renameTransactionCategory(cleanTarget, cleanFallback, now)
            } else {
                0
            }
            val updatedRules = if (syncRules && cleanFallback != cleanTarget) {
                dao.renameRuleCategory(cleanTarget, cleanFallback, now)
            } else {
                0
            }
            var aiSynced = false
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForCategoryChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = cleanTarget,
                    newName = null,
                    allActiveCategories = CategoryCatalog.allActiveCategories(updatedSettings)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
                aiSynced = true
            }
            Result.success(
                CategorySyncResult(
                    updatedTransactions = updatedTx,
                    updatedRules = updatedRules,
                    aiPromptSynced = aiSynced
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetCategoriesToDefault(
        syncAiPrompt: Boolean = true,
        resetIcons: Boolean = false
    ): Result<Unit> {
        return try {
            val settings = settingsStore.getBudgetSettingsOnce()
            val reset = settings.copy(
                expenseCategories = CategoryCatalog.expenseCategories,
                incomeCategories = CategoryCatalog.incomeCategories,
                transferCategories = CategoryCatalog.transferCategories,
                categoryIcons = if (resetIcons) emptyMap() else settings.categoryIcons,
                categoryParentMap = emptyMap()
            )
            settingsStore.saveBudgetSettings(reset)
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForCategoryChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    oldName = null,
                    newName = null,
                    allActiveCategories = CategoryCatalog.allActiveCategories(reset)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reorderCategories(
        type: TransactionType,
        orderedNames: List<String>
    ): Result<Unit> {
        return try {
            val clean = orderedNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (clean.isEmpty()) return Result.success(Unit)
            val settings = settingsStore.getBudgetSettingsOnce()
            val updated = when (type) {
                TransactionType.EXPENSE -> {
                    val cur = CategoryCatalog.activeExpenseCategories(settings)
                    settings.copy(expenseCategories = (clean + cur.filterNot { it in clean }).distinct())
                }
                TransactionType.INCOME -> {
                    val cur = CategoryCatalog.activeIncomeCategories(settings)
                    settings.copy(incomeCategories = (clean + cur.filterNot { it in clean }).distinct())
                }
                TransactionType.TRANSFER -> {
                    val cur = CategoryCatalog.activeTransferCategories(settings)
                    settings.copy(transferCategories = (clean + cur.filterNot { it in clean }).distinct())
                }
            }
            settingsStore.saveBudgetSettings(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCategoryParent(
        childName: String,
        parentName: String?
    ): Result<Unit> {
        return try {
            val cleanChild = childName.trim()
            val cleanParent = parentName?.trim()?.takeIf { it.isNotEmpty() }
            require(cleanChild.isNotEmpty()) { "分类名称不能为空" }
            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedMap = settings.categoryParentMap.toMutableMap()
            if (cleanParent == null) {
                updatedMap.remove(cleanChild)
            } else {
                require(CategoryCatalog.canAssignParent(cleanChild, cleanParent, updatedMap)) {
                    "不能将分类归属给自身或形成循环归属"
                }
                updatedMap[cleanChild] = cleanParent
            }
            settingsStore.saveBudgetSettings(settings.copy(categoryParentMap = updatedMap))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchSetCategoryParent(
        childNames: Collection<String>,
        parentName: String?
    ): Result<Int> {
        return try {
            val cleanParent = parentName?.trim()?.takeIf { it.isNotEmpty() }
            val settings = settingsStore.getBudgetSettingsOnce()
            val updatedMap = settings.categoryParentMap.toMutableMap()
            var count = 0
            childNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { child ->
                if (cleanParent == null) {
                    if (updatedMap.remove(child) != null) count++
                } else if (CategoryCatalog.canAssignParent(child, cleanParent, updatedMap)) {
                    updatedMap[child] = cleanParent
                    count++
                }
            }
            settingsStore.saveBudgetSettings(settings.copy(categoryParentMap = updatedMap))
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCategoryShowChildren(showChildren: Boolean): Result<Unit> {
        return try {
            val settings = settingsStore.getBudgetSettingsOnce()
            settingsStore.saveBudgetSettings(settings.copy(categoryShowChildren = showChildren))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun consolidateCategoryChildrenToParent(
        parentName: String? = null,
        syncTransactions: Boolean = true,
        syncRules: Boolean = true
    ): Result<CategorySyncResult> {
        return try {
            val settings = settingsStore.getBudgetSettingsOnce()
            val parentMap = settings.categoryParentMap
            if (parentMap.isEmpty()) return Result.success(CategorySyncResult())
            val cleanTargetParent = parentName?.trim()?.takeIf { it.isNotEmpty() }
            val targetPairs = parentMap.entries.mapNotNull { (child, directParent) ->
                val root = CategoryCatalog.resolveRootParent(child, parentMap)
                if (root == child) return@mapNotNull null
                if (cleanTargetParent != null && root != cleanTargetParent && directParent != cleanTargetParent) {
                    return@mapNotNull null
                }
                child to (cleanTargetParent ?: root)
            }
            if (targetPairs.isEmpty()) return Result.success(CategorySyncResult())
            val now = System.currentTimeMillis()
            var updatedTx = 0
            var updatedRules = 0
            targetPairs.forEach { (child, target) ->
                if (syncTransactions) {
                    updatedTx += dao.renameTransactionCategory(child, target, now)
                }
                if (syncRules) {
                    updatedRules += dao.renameRuleCategory(child, target, now)
                }
            }
            Result.success(
                CategorySyncResult(
                    updatedTransactions = updatedTx,
                    updatedRules = updatedRules,
                    aiPromptSynced = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveBillFieldsConfig(
        fields: List<com.example.model.BillFieldConfig>,
        syncAiPrompt: Boolean = true
    ): Result<Unit> {
        return try {
            val normalized = com.example.model.BillFieldCatalog.normalizeFields(fields)
            val settings = settingsStore.getBudgetSettingsOnce()
            val updated = settings.copy(billFieldsConfig = normalized)
            settingsStore.saveBudgetSettings(updated)
            if (syncAiPrompt) {
                val aiCfg = settingsStore.getAiConfigOnce()
                val newPrompt = AiUnderstandingManager.syncPromptForBillFieldsChange(
                    currentCustomPrompt = aiCfg.customSystemPrompt,
                    billFieldsConfig = normalized,
                    allActiveCategories = CategoryCatalog.allActiveCategories(updated),
                    allActiveChannels = com.example.model.ChannelCatalog.activeChannels(updated)
                )
                if (newPrompt != aiCfg.customSystemPrompt) {
                    settingsStore.saveAiConfig(aiCfg.copy(customSystemPrompt = newPrompt))
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordNotificationLog(
        packageName: String,
        appLabel: String,
        title: String,
        content: String,
        status: String,
        summary: String,
        capturedAtMillis: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = NotificationLogEntity(
                id = java.util.UUID.randomUUID().toString(),
                packageName = packageName.take(120),
                appLabel = appLabel.take(60),
                title = title.take(160),
                content = content.take(400),
                status = status.take(60),
                summary = summary.take(240),
                capturedAtMillis = capturedAtMillis
            )
            dao.insertNotificationLog(entity)
            val settings = settingsStore.getBudgetSettingsOnce()
            applyNotificationLogRetentionLocked(
                maxCount = settings.notificationLogMaxCount,
                maxDays = settings.notificationLogMaxDays,
                nowMillis = capturedAtMillis
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun applyNotificationLogRetentionLocked(
        maxCount: Int,
        maxDays: Int,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (maxDays > 0) {
            val cutoff = nowMillis - maxDays.toLong() * 86_400_000L
            dao.deleteNotificationLogsOlderThan(cutoff)
        }
        if (maxCount > 0) {
            dao.deleteNotificationLogsExcess(maxCount)
        }
    }

    suspend fun loadNotificationLogsPage(
        query: String = "",
        limit: Int = 50,
        offset: Int = 0
    ): List<com.example.model.CapturedNotificationLog> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val cleanQuery = query.trim()
        val entities = if (cleanQuery.isEmpty()) {
            dao.getNotificationLogsPage(limit = safeLimit, offset = safeOffset)
        } else {
            val escaped = cleanQuery
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            dao.searchNotificationLogsPage(pattern = "%$escaped%", limit = safeLimit, offset = safeOffset)
        }
        entities.map { it.toDomain() }
    }

    fun observeNotificationLogsCount(): Flow<Int> = dao.observeNotificationLogsCount()

    suspend fun updateNotificationLogStatus(
        id: String,
        status: String,
        summary: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.updateNotificationLogStatus(
                id = id,
                status = status.take(48),
                summary = summary.take(180)
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAllNotificationLogs(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.clearAllNotificationLogs()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateNotificationLogRetention(
        maxCount: Int,
        maxDays: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val safeCount = maxCount.coerceAtLeast(0)
            val safeDays = maxDays.coerceAtLeast(0)
            val settings = settingsStore.getBudgetSettingsOnce()
            settingsStore.saveBudgetSettings(
                settings.copy(
                    notificationLogMaxCount = safeCount,
                    notificationLogMaxDays = safeDays
                )
            )
            applyNotificationLogRetentionLocked(maxCount = safeCount, maxDays = safeDays)
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
        rateDateText: String,
        sourceId: String = "",
        sourceAvailableCodes: List<String> = emptyList()
    ) {
        settingsStore.saveExchangeRates(
            ratesToCny = ratesToCny,
            updatedAtMillis = updatedAtMillis,
            status = status,
            dataSource = dataSource,
            rateDateText = rateDateText,
            sourceId = sourceId,
            sourceAvailableCodes = sourceAvailableCodes
        )
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
        const val DEFAULT_TRANSACTION_PAGE_SIZE = 200
        const val DEFAULT_BOOK_LINK_PAGE_SIZE = 500
        const val MAX_RAW_NOTIFICATION_LENGTH = 220
        const val MAX_NOTE_LENGTH = 120
        fun defaultSeededRules(): List<CustomParsingRule> {
            val now = 1700000000000L
            return listOf(
                CustomParsingRule(
                    id = "builtin_self_transfer_withdraw",
                    name = "零钱提现与本人转账（不计收支）",
                    packageFilter = "*",
                    keywordPattern = "零钱提现|余额提现|提现到银行卡|提现至银行卡|提现到账|零钱充值|零钱通转入|零钱通转出|余额宝转入|余额宝转出|本人转账|本人账户|同名转账|同名账户|账户互转|内部转账|信用卡还款",
                    channelName = "",
                    defaultCategory = "不计收支",
                    defaultType = "TRANSFER",
                    priority = 5,
                    enabled = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                ),
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
