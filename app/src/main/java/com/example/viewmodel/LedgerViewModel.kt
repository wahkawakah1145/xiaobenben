package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiUnderstandingManager
import com.example.data.CurrencyExchangeManager
import com.example.data.ImportPreviewResult
import com.example.data.LedgerRepository
import com.example.data.LocalBackupManager
import com.example.data.LocalModelCatalog
import com.example.data.ManualImportManager
import com.example.data.WebDavSyncManager
import com.example.model.AiConfig
import com.example.model.AiProviderMode
import com.example.model.AnalyticsGranularity
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CurrencyCatalog
import com.example.model.CustomLocalModelInfo
import com.example.model.CustomParsingRule
import com.example.model.ExchangeRateAutoUpdateFrequency
import com.example.model.ExchangeRateState
import com.example.model.HomeStatsDimension
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import com.example.model.NotificationInquiry
import com.example.model.ParseMethod
import com.example.model.ParsedNotificationResult
import com.example.model.SourceApp
import com.example.model.StorageFootprintInfo
import com.example.model.TransactionType
import com.example.model.WebDavConfig
import com.example.parser.ChinaNotificationParser
import com.example.parser.LocalSpendingAnalyzer
import com.example.service.BackupReminderScheduler
import com.example.service.NotificationBridge
import com.example.service.RootKeepAliveManager
import com.example.service.RootKeepAliveStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface ParseBenchState {
    data object Idle : ParseBenchState
    data class Parsed(
        val result: ParsedNotificationResult,
        val rawTitle: String,
        val rawContent: String
    ) : ParseBenchState
    data class Error(val message: String) : ParseBenchState
}

sealed interface WebDavActionState {
    data object Idle : WebDavActionState
    data class Working(val label: String) : WebDavActionState
    data class Success(val message: String) : WebDavActionState
    data class Error(val message: String) : WebDavActionState
}

sealed interface RateUpdateState {
    data object Idle : RateUpdateState
    data object Loading : RateUpdateState
    data class Success(val message: String) : RateUpdateState
    data class Error(val message: String) : RateUpdateState
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Parsing(val message: String) : ImportUiState
    data class Ready(val preview: ImportPreviewResult) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

sealed interface LocalBackupActionState {
    data object Idle : LocalBackupActionState
    data class Working(val label: String) : LocalBackupActionState
    data class Success(val message: String) : LocalBackupActionState
    data class Error(val message: String) : LocalBackupActionState
}

sealed interface AiModelDownloadState {
    data object Idle : AiModelDownloadState
    data class Downloading(
        val modelId: String,
        val progress: Float,
        val statusText: String
    ) : AiModelDownloadState
    data class Success(val modelId: String, val message: String) : AiModelDownloadState
    data class Error(val message: String) : AiModelDownloadState
}

sealed interface AiTestState {
    data object Idle : AiTestState
    data object Loading : AiTestState
    data class Success(val result: ParsedNotificationResult) : AiTestState
    data class Error(val message: String) : AiTestState
}

data class DeleteUndoBanner(
    val id: Long = System.nanoTime(),
    val message: String,
    val deletedItems: List<LedgerTransaction> = emptyList(),
    val deletedRules: List<CustomParsingRule> = emptyList(),
    val deletedBookLinks: List<TransactionBookLink> = emptyList(),
    val dismissedInquiries: List<NotificationInquiry> = emptyList(),
    val createdNonBillRuleId: String? = null,
    val previousNonBillRuleSnapshot: CustomParsingRule? = null,
    val isNonBillMark: Boolean = false
)

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val webDavSyncManager: WebDavSyncManager = WebDavSyncManager(),
    private val exchangeManager: CurrencyExchangeManager = CurrencyExchangeManager(),
    private val aiManager: AiUnderstandingManager = AiUnderstandingManager()
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.ensureDefaultRulesSeeded()
        }
    }

    val transactionsState: StateFlow<UiState<List<LedgerTransaction>>> =
        repository.observeTransactions()
            .map<List<LedgerTransaction>, UiState<List<LedgerTransaction>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(UiState.Error(error.localizedMessage ?: "无法读取账单数据"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val booksState: StateFlow<List<LedgerBook>> =
        repository.observeBooks()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val bookLinksState: StateFlow<List<TransactionBookLink>> =
        repository.observeBookLinks()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    private val _selectedAnalyticsBookId = MutableStateFlow<String?>(null)
    val selectedAnalyticsBookId: StateFlow<String?> = _selectedAnalyticsBookId.asStateFlow()

    fun selectAnalyticsBook(bookId: String?) {
        _selectedAnalyticsBookId.value = bookId?.takeIf { it.isNotBlank() }
    }

    private val _openBookDetailId = MutableStateFlow<String?>(null)
    val openBookDetailId: StateFlow<String?> = _openBookDetailId.asStateFlow()

    fun openBookDetail(bookId: String) {
        val clean = bookId.trim()
        if (clean.isEmpty()) return
        _openBookDetailId.value = clean
        _selectedAnalyticsBookId.value = clean
    }

    fun closeBookDetail() {
        _openBookDetailId.value = null
    }

    val inquiriesState: StateFlow<List<NotificationInquiry>> =
        repository.observeInquiries()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val rulesState: StateFlow<UiState<List<CustomParsingRule>>> =
        repository.observeParsingRules()
            .map<List<CustomParsingRule>, UiState<List<CustomParsingRule>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(UiState.Error(error.localizedMessage ?: "无法读取识别规则"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val settingsState: StateFlow<BudgetSettings> =
        repository.observeSettings()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(BudgetSettings())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = BudgetSettings()
            )

    val appearanceState: StateFlow<AppearanceSettings> =
        repository.observeAppearanceSettings()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(AppearanceSettings())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AppearanceSettings()
            )

    val exchangeRateState: StateFlow<ExchangeRateState> =
        repository.observeExchangeRates()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(ExchangeRateState())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ExchangeRateState()
            )

    val webDavConfigState: StateFlow<WebDavConfig> =
        repository.observeWebDavConfig()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(WebDavConfig())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = WebDavConfig()
            )

    val aiConfigState: StateFlow<AiConfig> =
        repository.observeAiConfig()
            .catch { error ->
                if (error is CancellationException) throw error
                emit(AiConfig())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AiConfig()
            )

    private val _parseBenchState = MutableStateFlow<ParseBenchState>(ParseBenchState.Idle)
    val parseBenchState: StateFlow<ParseBenchState> = _parseBenchState.asStateFlow()

    private val _webDavActionState = MutableStateFlow<WebDavActionState>(WebDavActionState.Idle)
    val webDavActionState: StateFlow<WebDavActionState> = _webDavActionState.asStateFlow()

    private val _rateUpdateState = MutableStateFlow<RateUpdateState>(RateUpdateState.Idle)
    val rateUpdateState: StateFlow<RateUpdateState> = _rateUpdateState.asStateFlow()

    private val _importUiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    private val _localBackupActionState = MutableStateFlow<LocalBackupActionState>(LocalBackupActionState.Idle)
    val localBackupActionState: StateFlow<LocalBackupActionState> = _localBackupActionState.asStateFlow()

    private val _aiModelDownloadState = MutableStateFlow<AiModelDownloadState>(AiModelDownloadState.Idle)
    val aiModelDownloadState: StateFlow<AiModelDownloadState> = _aiModelDownloadState.asStateFlow()

    private val _aiTestState = MutableStateFlow<AiTestState>(AiTestState.Idle)
    val aiTestState: StateFlow<AiTestState> = _aiTestState.asStateFlow()

    private val _rootStatusState = MutableStateFlow(RootKeepAliveStatus())
    val rootStatusState: StateFlow<RootKeepAliveStatus> = _rootStatusState.asStateFlow()

    private val _storageFootprintState = MutableStateFlow(StorageFootprintInfo())
    val storageFootprintState: StateFlow<StorageFootprintInfo> = _storageFootprintState.asStateFlow()

    private val _storageOptimizing = MutableStateFlow(false)
    val storageOptimizing: StateFlow<Boolean> = _storageOptimizing.asStateFlow()

    private val _customModelsState = MutableStateFlow<List<CustomLocalModelInfo>>(emptyList())
    val customModelsState: StateFlow<List<CustomLocalModelInfo>> = _customModelsState.asStateFlow()

    private val _deleteUndoBanner = MutableStateFlow<DeleteUndoBanner?>(null)
    val deleteUndoBanner: StateFlow<DeleteUndoBanner?> = _deleteUndoBanner.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun dismissDeleteUndoBanner() {
        _deleteUndoBanner.value = null
    }

    fun undoLastDelete() {
        val banner = _deleteUndoBanner.value ?: return
        _deleteUndoBanner.value = null
        viewModelScope.launch {
            runCatching {
                if (banner.deletedRules.isNotEmpty()) {
                    repository.mergeRulesFromBackup(banner.deletedRules)
                }
                if (banner.deletedItems.isNotEmpty()) {
                    repository.mergeTransactionsFromBackup(banner.deletedItems)
                }
                if (banner.deletedBookLinks.isNotEmpty()) {
                    repository.restoreBookLinks(banner.deletedBookLinks)
                }
                if (banner.dismissedInquiries.isNotEmpty()) {
                    banner.dismissedInquiries.forEach { repository.restoreInquiry(it) }
                }
                val createdRuleId = banner.createdNonBillRuleId
                if (!createdRuleId.isNullOrBlank()) {
                    val prevRule = banner.previousNonBillRuleSnapshot
                    if (prevRule != null) {
                        repository.saveParsingRule(prevRule)
                    } else {
                        repository.deleteParsingRule(createdRuleId)
                    }
                }
            }.onSuccess {
                _actionMessage.value = when {
                    banner.isNonBillMark && banner.deletedItems.isNotEmpty() -> {
                        "已撤销非账单标记，恢复「${banner.deletedItems.first().merchant}」并取消忽略规则"
                    }
                    banner.isNonBillMark && banner.dismissedInquiries.isNotEmpty() -> {
                        "已撤销非账单标记，恢复待确认提醒并取消忽略规则"
                    }
                    banner.deletedRules.isNotEmpty() && banner.deletedItems.isEmpty() -> {
                        if (banner.deletedRules.size == 1) {
                            "已撤销删除，恢复规则「${banner.deletedRules.first().name}」"
                        } else {
                            "已撤销删除，恢复 ${banner.deletedRules.size} 条规则"
                        }
                    }
                    banner.deletedItems.isNotEmpty() -> {
                        "已撤销删除，恢复 ${banner.deletedItems.size} 笔账单"
                    }
                    else -> "已撤销操作"
                }
            }.onFailure { err ->
                _actionMessage.value = "撤销失败：${err.localizedMessage ?: "请重试"}"
            }
        }
    }

    fun refreshStorageFootprint(context: android.content.Context) {
        viewModelScope.launch {
            runCatching { repository.inspectStorageFootprint(context) }
                .onSuccess { _storageFootprintState.value = it }
        }
    }

    fun optimizeStorage(
        context: android.content.Context,
        pruneRawLogsOlderThanDays: Int? = null
    ) {
        viewModelScope.launch {
            _storageOptimizing.value = true
            repository.optimizeStorage(context, pruneRawLogsOlderThanDays)
                .onSuccess { result ->
                    _storageFootprintState.value = repository.inspectStorageFootprint(context)
                    val savedKb = ((result.beforeBytes - result.afterBytes).coerceAtLeast(0L)) / 1024.0
                    val parts = buildList {
                        add("数据库已压缩 (VACUUM)")
                        if (result.prunedRawLogs > 0) add("精简 ${result.prunedRawLogs} 条历史原文")
                        if (result.prunedInquiries > 0) add("清理 ${result.prunedInquiries} 条过期询问")
                        if (result.clearedCacheBytes > 0) add("清除缓存 ${result.clearedCacheBytes / 1024} KB")
                        add("共释放约 %.1f KB".format(savedKb))
                    }
                    _actionMessage.value = parts.joinToString("，")
                }
                .onFailure { err ->
                    _actionMessage.value = "存储优化失败：${err.localizedMessage ?: "请重试"}"
                }
            _storageOptimizing.value = false
        }
    }

    fun resetParseBench() {
        _parseBenchState.value = ParseBenchState.Idle
    }

    fun clearImportPreview() {
        _importUiState.value = ImportUiState.Idle
    }

    fun addManualTransaction(
        amountCents: Long,
        currencyCode: String,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        cardLastFour: String?,
        note: String?,
        occurredAtMillis: Long = System.currentTimeMillis(),
        tradeType: String? = null,
        productName: String? = null,
        tradeStatus: String? = null,
        transactionNo: String? = null,
        merchantOrderNo: String? = null
    ) {
        viewModelScope.launch {
            val resolvedCurrency = CurrencyCatalog.resolve(currencyCode)
            val tx = LedgerTransaction(
                amountCents = amountCents,
                currencyCode = resolvedCurrency.code,
                type = type.name,
                category = category,
                merchant = merchant,
                channel = channel,
                cardLastFour = cardLastFour?.takeIf { it.isNotBlank() },
                sourceApp = SourceApp.MANUAL.name,
                parseMethod = ParseMethod.MANUAL.name,
                rawNotification = null,
                note = note?.takeIf { it.isNotBlank() },
                tradeType = tradeType?.takeIf { it.isNotBlank() },
                productName = productName?.takeIf { it.isNotBlank() },
                tradeStatus = tradeStatus?.takeIf { it.isNotBlank() },
                transactionNo = transactionNo?.takeIf { it.isNotBlank() },
                merchantOrderNo = merchantOrderNo?.takeIf { it.isNotBlank() },
                occurredAtMillis = occurredAtMillis,
                confirmed = true
            )
            repository.addTransaction(tx)
                .onFailure {
                    _actionMessage.value = "记录失败：${it.localizedMessage ?: "请检查输入"}"
                }
        }
    }

    fun updateTransaction(
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
    ) {
        viewModelScope.launch {
            repository.updateTransaction(
                txId = txId,
                amountCents = amountCents,
                currencyCode = currencyCode,
                type = type,
                category = category,
                merchant = merchant,
                channel = channel,
                cardLastFour = cardLastFour,
                note = note,
                occurredAtMillis = occurredAtMillis,
                confirmed = confirmed,
                learnFormatTemplate = learnFormatTemplate,
                tradeType = tradeType,
                productName = productName,
                tradeStatus = tradeStatus,
                transactionNo = transactionNo,
                merchantOrderNo = merchantOrderNo,
                updateExtendedFields = updateExtendedFields
            )
                .onSuccess { templateUpdated ->
                    if (templateUpdated) {
                        _actionMessage.value = "已保存修改，并更新该通知格式模版"
                    }
                }
                .onFailure { _actionMessage.value = "更新失败：${it.localizedMessage ?: "请重试"}" }
        }
    }

    fun confirmTransaction(txId: String) {
        viewModelScope.launch {
            repository.confirmTransaction(txId)
                .onFailure { _actionMessage.value = "确认失败" }
        }
    }

    fun confirmAllPendingTransactions() {
        viewModelScope.launch {
            repository.confirmAllPendingTransactions()
                .onFailure { _actionMessage.value = "确认失败" }
        }
    }

    fun markTransactionAsNonBill(tx: LedgerTransaction) {
        viewModelScope.launch {
            repository.markTransactionAsNonBillWithUndoSnapshot(tx)
                .onSuccess { snapshot ->
                    val label = if (snapshot.createdOrUpdatedRule != null) {
                        "已标记「${tx.merchant}」为非账单并记住忽略规则"
                    } else {
                        "已标记「${tx.merchant}」为非账单并移除"
                    }
                    _deleteUndoBanner.value = DeleteUndoBanner(
                        id = System.nanoTime(),
                        message = label,
                        deletedItems = listOf(snapshot.transaction),
                        deletedBookLinks = snapshot.bookLinks,
                        createdNonBillRuleId = snapshot.createdOrUpdatedRule?.id,
                        previousNonBillRuleSnapshot = snapshot.previousRuleBeforeOverwrite,
                        isNonBillMark = true
                    )
                }
                .onFailure { _actionMessage.value = "标记失败：${it.localizedMessage ?: "请重试"}" }
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            val snapshot = repository.getTransactionsByIdsOnce(listOf(txId))
            val bookLinks = repository.getBookLinksForTransactionsOnce(listOf(txId))
            repository.deleteTransaction(txId)
                .onSuccess {
                    if (snapshot.isNotEmpty()) {
                        _deleteUndoBanner.value = DeleteUndoBanner(
                            id = System.nanoTime(),
                            message = "已删除「${snapshot.first().merchant}」",
                            deletedItems = snapshot,
                            deletedBookLinks = bookLinks
                        )
                    }
                }
                .onFailure { _actionMessage.value = "删除失败：${it.localizedMessage ?: "请重试"}" }
        }
    }

    fun deleteTransactions(txIds: Collection<String>) {
        viewModelScope.launch {
            val idSet = txIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            if (idSet.isEmpty()) return@launch
            val snapshot = repository.getTransactionsByIdsOnce(idSet)
            val bookLinks = repository.getBookLinksForTransactionsOnce(idSet)
            repository.deleteTransactions(idSet)
                .onSuccess { count ->
                    if (count > 0 && snapshot.isNotEmpty()) {
                        val label = if (snapshot.size == 1) {
                            "已删除「${snapshot.first().merchant}」"
                        } else {
                            "已删除 ${snapshot.size} 笔账单"
                        }
                        _deleteUndoBanner.value = DeleteUndoBanner(
                            id = System.nanoTime(),
                            message = label,
                            deletedItems = snapshot,
                            deletedBookLinks = bookLinks
                        )
                    }
                }
                .onFailure { _actionMessage.value = "批量删除失败：${it.localizedMessage ?: "请重试"}" }
        }
    }

    fun batchUpdateTransactions(
        txIds: Collection<String>,
        type: TransactionType? = null,
        category: String? = null,
        channel: String? = null,
        merchant: String? = null,
        currencyCode: String? = null,
        note: String? = null
    ) {
        viewModelScope.launch {
            repository.batchUpdateTransactions(
                txIds = txIds,
                type = type,
                category = category,
                channel = channel,
                merchant = merchant,
                currencyCode = currencyCode,
                note = note
            )
                .onSuccess { count ->
                    if (count > 0) _actionMessage.value = "已批量修改 $count 笔账单"
                }
                .onFailure { _actionMessage.value = "批量修改失败：${it.localizedMessage ?: "请重试"}" }
        }
    }

    fun resolveInquiry(
        inquiry: NotificationInquiry,
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
    ) {
        viewModelScope.launch {
            repository.resolveInquiryAndLearnRule(
                inquiry = inquiry,
                amountCents = amountCents,
                currencyCode = currencyCode,
                type = type,
                category = category,
                merchant = merchant,
                channel = channel,
                learnRule = learnRule,
                ruleKeyword = ruleKeyword,
                ruleName = ruleName,
                numberFormatPattern = numberFormatPattern,
                ruleDefaultType = ruleDefaultType,
                occurredAtMillis = occurredAtMillis
            )
                .onFailure { _actionMessage.value = "处理失败：${it.localizedMessage ?: "请检查输入"}" }
        }
    }

    fun dismissInquiry(inquiryId: String) {
        viewModelScope.launch {
            repository.dismissInquiry(inquiryId)
        }
    }

    fun dismissInquiryAsNonBill(inquiry: NotificationInquiry) {
        viewModelScope.launch {
            val rulePair = repository.recordIgnoredNotificationRuleWithPrevious(
                packageName = inquiry.packageName,
                title = inquiry.title,
                content = inquiry.content
            ).getOrNull()
            repository.dismissInquiry(inquiry.id)
            _deleteUndoBanner.value = DeleteUndoBanner(
                id = System.nanoTime(),
                message = "已标记「${inquiry.title.ifBlank { inquiry.suggestedMerchant }}」为非账单并记住忽略规则",
                dismissedInquiries = listOf(inquiry),
                createdNonBillRuleId = rulePair?.first?.id,
                previousNonBillRuleSnapshot = rulePair?.second,
                isNonBillMark = true
            )
        }
    }

    fun enqueueInquiryFromTestBench(
        packageName: String,
        title: String,
        content: String,
        parsed: ParsedNotificationResult?
    ) {
        viewModelScope.launch {
            val inquiry = NotificationInquiry(
                packageName = packageName.ifBlank { "manual.bench" },
                appLabel = parsed?.channel ?: "通知测试",
                title = title,
                content = content,
                suggestedAmountCents = parsed?.amountCents ?: 0L,
                suggestedCurrencyCode = parsed?.currencyCode ?: ChinaNotificationParser.detectCurrencyCode("$title $content"),
                suggestedType = parsed?.type?.name ?: TransactionType.EXPENSE.name,
                suggestedCategory = parsed?.category ?: "其他",
                suggestedMerchant = parsed?.merchant ?: title.take(30),
                suggestedChannel = parsed?.channel ?: "未知渠道",
                cardLastFour = parsed?.cardLastFour,
                reason = parsed?.inquiryReason?.ifBlank { "等待补充信息并学习规则" } ?: "未能自动识别，等待补充",
                occurredAtMillis = parsed?.occurredAtMillis ?: System.currentTimeMillis()
            )
            repository.addInquiry(inquiry)
            _parseBenchState.value = ParseBenchState.Idle
        }
    }

    fun analyzeNotificationText(
        packageName: String,
        title: String,
        content: String,
        enableSemanticPass: Boolean
    ) {
        if (title.isBlank() && content.isBlank()) {
            _parseBenchState.value = ParseBenchState.Error("请输入通知标题或正文内容")
            return
        }
        val rules = (rulesState.value as? UiState.Success)?.data ?: emptyList()
        val parsed = ChinaNotificationParser.parse(
            packageName = packageName,
            title = title,
            content = content,
            customRules = rules,
            enableLocalSemanticPass = enableSemanticPass
        )
        if (parsed != null && parsed.isFinancialTransaction) {
            _parseBenchState.value = ParseBenchState.Parsed(parsed, title, content)
        } else {
            _parseBenchState.value = ParseBenchState.Error("未能准确识别金额或格式（可点击下方转为待询问学习规则）")
        }
    }

    fun analyzeWithConfiguredAi(
        context: android.content.Context,
        packageName: String,
        title: String,
        content: String
    ) {
        if (title.isBlank() && content.isBlank()) {
            _parseBenchState.value = ParseBenchState.Error("请输入通知标题或正文内容")
            return
        }
        viewModelScope.launch {
            val config = repository.getAiConfigOnce()
            if (config.providerMode == AiProviderMode.DISABLED) {
                _parseBenchState.value = ParseBenchState.Error("尚未启用 AI理解，请先前往「设置 -> AI理解」选择本地小模型或填写在线 API")
                return@launch
            }
            val rules = (rulesState.value as? UiState.Success)?.data ?: repository.getRulesOnce()
            val activeCategories = com.example.model.CategoryCatalog.allActiveCategories(settingsState.value)
            val result = aiManager.enhanceOrParseNotification(
                context = context,
                config = config,
                packageName = packageName,
                title = title,
                content = content,
                fallbackParsed = null,
                availableRules = rules,
                activeCategories = activeCategories
            )
            result
                .onSuccess { parsed ->
                    _parseBenchState.value = ParseBenchState.Parsed(parsed, title, content)
                }
                .onFailure { err ->
                    _parseBenchState.value = ParseBenchState.Error(err.message ?: "AI理解失败")
                }
        }
    }

    fun saveParsedResult(
        parsed: ParsedNotificationResult,
        rawTitle: String,
        rawContent: String
    ) {
        viewModelScope.launch {
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
                rawNotification = "$rawTitle: $rawContent".trim().take(220),
                note = parsed.reasoning.take(120),
                occurredAtMillis = parsed.occurredAtMillis ?: System.currentTimeMillis(),
                confirmed = true
            )
            repository.addTransaction(tx)
                .onSuccess {
                    _parseBenchState.value = ParseBenchState.Idle
                }
                .onFailure { error ->
                    _actionMessage.value = "入账失败：${error.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun saveOrUpdateCustomRule(rule: CustomParsingRule) {
        viewModelScope.launch {
            repository.saveParsingRule(rule)
                .onFailure { _actionMessage.value = "保存规则失败：${it.localizedMessage ?: "请检查输入"}" }
        }
    }

    fun resetDefaultRules() {
        viewModelScope.launch {
            repository.resetDefaultRules()
                .onFailure { _actionMessage.value = "恢复默认规则失败" }
        }
    }

    fun toggleCustomRule(ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleParsingRule(ruleId, enabled)
                .onFailure { _actionMessage.value = "更新规则状态失败" }
        }
    }

    fun deleteCustomRule(ruleId: String) {
        viewModelScope.launch {
            val targetRule = (rulesState.value as? UiState.Success)?.data
                ?.firstOrNull { it.id == ruleId }
                ?: repository.getRulesOnce().firstOrNull { it.id == ruleId }
            repository.deleteParsingRule(ruleId)
                .onSuccess {
                    if (targetRule != null) {
                        _deleteUndoBanner.value = DeleteUndoBanner(
                            message = "已删除规则「${targetRule.name}」",
                            deletedRules = listOf(targetRule)
                        )
                    } else {
                        _actionMessage.value = "已删除识别规则"
                    }
                }
                .onFailure { _actionMessage.value = "删除规则失败" }
        }
    }

    fun saveSettings(settings: BudgetSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
                .onFailure { _actionMessage.value = "保存设置失败" }
        }
    }

    fun addCustomCategory(
        type: TransactionType,
        name: String,
        syncAiPrompt: Boolean = true,
        iconKey: String? = null
    ) {
        viewModelScope.launch {
            repository.addCustomCategory(
                type = type,
                rawName = name,
                syncAiPrompt = syncAiPrompt,
                iconKey = iconKey
            )
                .onSuccess { res ->
                    val aiSuffix = if (res.aiPromptSynced) "，已同步 AI 理解提示词" else ""
                    _actionMessage.value = "已添加${type.label}分类「${name.trim()}」$aiSuffix"
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "添加分类失败"
                }
        }
    }

    fun renameCategory(
        type: TransactionType,
        oldName: String,
        newName: String,
        syncTransactions: Boolean = true,
        syncRules: Boolean = true,
        syncAiPrompt: Boolean = true
    ) {
        viewModelScope.launch {
            repository.renameCategory(
                type = type,
                oldName = oldName,
                rawNewName = newName,
                syncTransactions = syncTransactions,
                syncRules = syncRules,
                syncAiPrompt = syncAiPrompt
            )
                .onSuccess { res ->
                    val details = buildList {
                        if (syncTransactions) add("${res.updatedTransactions} 笔账单")
                        if (syncRules) add("${res.updatedRules} 条规则")
                        if (res.aiPromptSynced) add("AI 提示词")
                    }.joinToString("、")
                    val suffix = if (details.isNotEmpty()) "（已同步 $details）" else ""
                    _actionMessage.value = "已将「$oldName」改为「${newName.trim()}」$suffix"
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "修改分类失败"
                }
        }
    }

    fun deleteCategory(
        type: TransactionType,
        targetName: String,
        fallbackCategory: String = "其他",
        syncTransactions: Boolean = true,
        syncRules: Boolean = true,
        syncAiPrompt: Boolean = true
    ) {
        viewModelScope.launch {
            repository.deleteCategory(
                type = type,
                targetName = targetName,
                fallbackCategory = fallbackCategory,
                syncTransactions = syncTransactions,
                syncRules = syncRules,
                syncAiPrompt = syncAiPrompt
            )
                .onSuccess { res ->
                    val details = buildList {
                        if (syncTransactions && res.updatedTransactions > 0) add("${res.updatedTransactions} 笔账单归入「$fallbackCategory」")
                        if (syncRules && res.updatedRules > 0) add("${res.updatedRules} 条规则归入「$fallbackCategory」")
                        if (res.aiPromptSynced) add("已同步 AI 提示词")
                    }.joinToString("，")
                    val suffix = if (details.isNotEmpty()) "（$details）" else ""
                    _actionMessage.value = "已删除分类「$targetName」$suffix"
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "删除分类失败"
                }
        }
    }

    fun setCategoryIcon(categoryName: String, iconKey: String?) {
        viewModelScope.launch {
            repository.setCategoryIcon(categoryName, iconKey)
                .onSuccess {
                    _actionMessage.value = if (iconKey.isNullOrBlank()) {
                        "已恢复「$categoryName」默认图标"
                    } else {
                        "已更新「$categoryName」图标"
                    }
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "更新图标失败"
                }
        }
    }

    fun resetCategoriesToDefault(syncAiPrompt: Boolean = true, resetIcons: Boolean = false) {
        viewModelScope.launch {
            repository.resetCategoriesToDefault(syncAiPrompt = syncAiPrompt, resetIcons = resetIcons)
                .onSuccess {
                    _actionMessage.value = if (syncAiPrompt) {
                        "已恢复默认分类列表并同步 AI 提示词"
                    } else {
                        "已恢复默认分类列表"
                    }
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "恢复默认分类失败"
                }
        }
    }

    suspend fun saveAppearanceSettingsSuspend(settings: AppearanceSettings): Result<Unit> {
        return repository.saveAppearanceSettings(settings)
    }

    fun saveBook(
        name: String,
        description: String = "",
        colorArgb: Long = 0xFF0F6B54L,
        existingBookId: String? = null,
        onSaved: ((LedgerBook) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val existing = existingBookId?.let { id -> booksState.value.firstOrNull { it.id == id } }
            val book = (existing ?: LedgerBook(name = name)).copy(
                name = name.trim(),
                description = description.trim(),
                colorArgb = colorArgb
            )
            repository.saveBook(book)
                .onSuccess { saved ->
                    _actionMessage.value = if (existing == null) "已创建账本「${saved.name}」" else "已更新账本「${saved.name}」"
                    onSaved?.invoke(saved)
                }
                .onFailure { err ->
                    _actionMessage.value = "保存账本失败：${err.localizedMessage ?: "请检查名称"}"
                }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            val targetName = booksState.value.firstOrNull { it.id == bookId }?.name
            repository.deleteBook(bookId)
                .onSuccess {
                    if (_selectedAnalyticsBookId.value == bookId) {
                        _selectedAnalyticsBookId.value = null
                    }
                    if (_openBookDetailId.value == bookId) {
                        _openBookDetailId.value = null
                    }
                    _actionMessage.value = targetName?.let { "已删除账本「$it」（账单本身已保留）" } ?: "已删除账本"
                }
                .onFailure { err ->
                    _actionMessage.value = "删除账本失败：${err.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun addTransactionsToBooks(txIds: Collection<String>, bookIds: Collection<String>) {
        viewModelScope.launch {
            repository.addTransactionsToBooks(txIds, bookIds)
                .onSuccess {
                    _actionMessage.value = "已将 ${txIds.size} 笔账单加入 ${bookIds.size} 个账本"
                }
                .onFailure { err ->
                    _actionMessage.value = "加入账本失败：${err.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun setBooksForTransaction(txId: String, bookIds: Collection<String>) {
        viewModelScope.launch {
            val distinctCount = bookIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().size
            repository.setBooksForTransaction(txId, bookIds)
                .onSuccess {
                    _actionMessage.value = if (distinctCount > 0) {
                        "已更新该账单所属账本（共 $distinctCount 个）"
                    } else {
                        "已将该账单从所有账本移出"
                    }
                }
                .onFailure { err ->
                    _actionMessage.value = "更新所属账本失败：${err.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun removeTransactionsFromBook(txIds: Collection<String>, bookId: String) {
        viewModelScope.launch {
            repository.removeTransactionsFromBook(txIds, bookId)
                .onSuccess { count ->
                    if (count > 0) _actionMessage.value = "已从账本移出 $count 笔账单"
                }
                .onFailure { err ->
                    _actionMessage.value = "移出失败：${err.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun excludeTransactionsFromDedup(txIds: Collection<String>) {
        viewModelScope.launch {
            val clean = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (clean.isEmpty()) return@launch
            repository.excludeTransactionsFromDedup(clean)
                .onSuccess { added ->
                    _actionMessage.value = if (added > 0) {
                        "已将 $added 笔账单标记为排除智能去重"
                    } else {
                        "所选账单已在排除智能去重列表中"
                    }
                }
                .onFailure { err ->
                    _actionMessage.value = "标记排除失败：${err.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun restoreExcludedDedupTransactions(txIds: Collection<String>) {
        viewModelScope.launch {
            val clean = txIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (clean.isEmpty()) return@launch
            repository.restoreExcludedDedupTransactions(clean)
                .onSuccess { restored ->
                    if (restored > 0) {
                        _actionMessage.value = "已恢复 $restored 笔账单参与智能去重"
                    }
                }
                .onFailure { err ->
                    _actionMessage.value = "恢复失败：${err.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun buildQuickSelectionReport(
        transactions: List<LedgerTransaction>,
        scopeTitle: String = "选中账单快速分析"
    ): com.example.parser.QuickAnalysisReport {
        val settings = settingsState.value
        val rates = exchangeRateState.value.ratesToCny
        return LocalSpendingAnalyzer.generateQuickSelectionReport(
            transactions = transactions,
            baseCurrencyCode = settings.baseCurrencyCode,
            ratesToCny = rates,
            scopeTitle = scopeTitle
        )
    }

    fun buildQuickSelectionSummary(
        transactions: List<LedgerTransaction>,
        scopeTitle: String = "选中账单快速分析"
    ): String = buildQuickSelectionReport(transactions, scopeTitle).plainText

    fun saveAppearanceSettings(settings: AppearanceSettings) {
        viewModelScope.launch {
            repository.saveAppearanceSettings(settings)
                .onFailure { _actionMessage.value = "保存外观设置失败" }
        }
    }

    fun checkRootStatus() {
        viewModelScope.launch {
            _rootStatusState.value = RootKeepAliveManager.checkRootAvailable()
        }
    }

    fun applyRootKeepAlive(context: android.content.Context) {
        viewModelScope.launch {
            val result = RootKeepAliveManager.applyRootKeepAlive(context)
            _rootStatusState.value = result
            val current = settingsState.value
            if (current.rootKeepAliveEnabled != result.isRootAvailable && result.isRootAvailable) {
                repository.saveSettings(current.copy(rootKeepAliveEnabled = true))
            }
        }
    }

    fun updateExchangeRateDatabase(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _rateUpdateState.value = RateUpdateState.Loading
            exchangeManager.refreshRatesDatabase(repository)
                .onSuccess { updated ->
                    if (!silent) {
                        _rateUpdateState.value = RateUpdateState.Success(updated.lastStatus)
                    }
                }
                .onFailure { err ->
                    if (!silent) {
                        val msg = err.message ?: "汇率更新失败"
                        _rateUpdateState.value = RateUpdateState.Error(msg)
                    }
                }
        }
    }

    fun checkExchangeRateAutoUpdateIfDue() {
        viewModelScope.launch {
            val settings = repository.getSettingsOnce()
            val freq = ExchangeRateAutoUpdateFrequency.fromRaw(settings.exchangeRateUpdateFrequency)
            if (freq == ExchangeRateAutoUpdateFrequency.MANUAL || freq.intervalHours <= 0) return@launch
            val rates = repository.getExchangeRatesOnce()
            val now = System.currentTimeMillis()
            val intervalMillis = freq.intervalHours * 3_600_000L
            if (rates.updatedAtMillis <= 0L || (now - rates.updatedAtMillis) >= intervalMillis) {
                if (_rateUpdateState.value !is RateUpdateState.Loading) {
                    exchangeManager.refreshRatesDatabase(repository)
                }
            }
        }
    }

    fun saveAiConfig(config: AiConfig) {
        viewModelScope.launch {
            repository.saveAiConfig(config)
                .onFailure { _actionMessage.value = "保存 AI 配置失败" }
        }
    }

    fun downloadLocalAiModel(
        context: android.content.Context,
        modelId: String,
        mirrorBaseUrl: String
    ) {
        viewModelScope.launch {
            val preset = LocalModelCatalog.resolve(modelId)
            _aiModelDownloadState.value = AiModelDownloadState.Downloading(
                modelId = preset.id,
                progress = 0f,
                statusText = "正在连接镜像下载 ${preset.name}..."
            )
            aiManager.downloadLocalModel(
                context = context,
                modelId = preset.id,
                mirrorBaseUrl = mirrorBaseUrl
            ) { progress, statusText ->
                _aiModelDownloadState.value = AiModelDownloadState.Downloading(
                    modelId = preset.id,
                    progress = progress,
                    statusText = statusText
                )
            }
                .onSuccess { installStatus ->
                    _aiModelDownloadState.value = AiModelDownloadState.Success(
                        modelId = preset.id,
                        message = "下载完成（已占用 ${installStatus.totalBytes / 1024} KB）"
                    )
                    val cur = repository.getAiConfigOnce()
                    repository.saveAiConfig(
                        cur.copy(
                            providerMode = AiProviderMode.LOCAL_SMALL_MODEL,
                            localModelId = preset.id,
                            localModelMirror = mirrorBaseUrl
                        )
                    )
                }
                .onFailure { err ->
                    _aiModelDownloadState.value = AiModelDownloadState.Error(err.message ?: "模型下载失败")
                }
        }
    }

    fun deleteLocalAiModel(context: android.content.Context, modelId: String) {
        aiManager.deleteLocalModel(context, modelId)
        _customModelsState.value = aiManager.listCustomImportedModels(context)
        _aiModelDownloadState.value = AiModelDownloadState.Idle
        _actionMessage.value = "已清理本地模型缓存"
    }

    fun refreshCustomModels(context: android.content.Context) {
        _customModelsState.value = aiManager.listCustomImportedModels(context)
    }

    fun importLocalAiWeight(
        context: android.content.Context,
        uri: android.net.Uri,
        explicitFileName: String? = null
    ) {
        viewModelScope.launch {
            _aiModelDownloadState.value = AiModelDownloadState.Downloading(
                modelId = "custom_import",
                progress = 0.45f,
                statusText = "正在校验并导入本地模型权重..."
            )
            aiManager.importCustomModelWeight(context, uri, explicitFileName)
                .onSuccess { info ->
                    _customModelsState.value = aiManager.listCustomImportedModels(context)
                    _aiModelDownloadState.value = AiModelDownloadState.Success(
                        modelId = info.id,
                        message = "已导入「${info.displayName}」(${info.formatLabel} · ${info.sizeBytes / 1024} KB)"
                    )
                    val cur = repository.getAiConfigOnce()
                    repository.saveAiConfig(
                        cur.copy(
                            providerMode = AiProviderMode.LOCAL_SMALL_MODEL,
                            localModelId = info.id
                        )
                    )
                }
                .onFailure { err ->
                    _aiModelDownloadState.value = AiModelDownloadState.Error(err.message ?: "导入模型权重失败")
                }
        }
    }

    fun deleteCustomImportedModel(context: android.content.Context, customId: String) {
        aiManager.deleteLocalModel(context, customId)
        val remaining = aiManager.listCustomImportedModels(context)
        _customModelsState.value = remaining
        viewModelScope.launch {
            val cur = repository.getAiConfigOnce()
            if (cur.localModelId == customId) {
                repository.saveAiConfig(
                    cur.copy(localModelId = remaining.firstOrNull()?.id ?: LocalModelCatalog.presets.first().id)
                )
            }
            _actionMessage.value = "已移除自导入模型权重"
        }
    }

    fun testAiUnderstanding(
        context: android.content.Context,
        config: AiConfig,
        sampleTitle: String,
        sampleContent: String
    ) {
        viewModelScope.launch {
            repository.saveAiConfig(config)
            _aiTestState.value = AiTestState.Loading
            val rules = (rulesState.value as? UiState.Success)?.data ?: repository.getRulesOnce()
            val activeCategories = com.example.model.CategoryCatalog.allActiveCategories(settingsState.value)
            aiManager.enhanceOrParseNotification(
                context = context,
                config = config,
                packageName = "manual.ai.test",
                title = sampleTitle,
                content = sampleContent,
                fallbackParsed = null,
                availableRules = rules,
                activeCategories = activeCategories
            )
                .onSuccess { parsed -> _aiTestState.value = AiTestState.Success(parsed) }
                .onFailure { err -> _aiTestState.value = AiTestState.Error(err.message ?: "AI理解测试失败") }
        }
    }

    fun previewManualImport(
        rawContent: String,
        fileNameHint: String? = null,
        context: android.content.Context? = null,
        useRuleMatching: Boolean = true,
        useAiUnderstanding: Boolean = false
    ) {
        viewModelScope.launch {
            val rules = (rulesState.value as? UiState.Success)?.data ?: repository.getRulesOnce()
            val existingTransactions = repository.getAllTransactionsOnce()
            if (!useAiUnderstanding) {
                _importUiState.value = ImportUiState.Parsing("正在解析内容并比对已有账单去重...")
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ManualImportManager.parseImportedContent(
                        rawContent = rawContent,
                        fileNameHint = fileNameHint,
                        customRules = rules,
                        useRuleMatching = useRuleMatching
                    ).map { preview ->
                        ManualImportManager.annotateDuplicates(preview, existingTransactions)
                    }
                }
                result
                    .onSuccess { preview ->
                        _importUiState.value = ImportUiState.Ready(preview)
                    }
                    .onFailure { err ->
                        _importUiState.value = ImportUiState.Error(err.message ?: "无法解析导入内容")
                    }
                return@launch
            }

            _importUiState.value = ImportUiState.Parsing("正在解析并调用 AI 理解增强账单...")
            val baseResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ManualImportManager.parseImportedContent(
                    rawContent = rawContent,
                    fileNameHint = fileNameHint,
                    customRules = rules,
                    useRuleMatching = useRuleMatching
                )
            }
            val aiConfig = repository.getAiConfigOnce()
            if (context == null || aiConfig.providerMode == AiProviderMode.DISABLED) {
                baseResult
                    .onSuccess { preview ->
                        val annotated = ManualImportManager.annotateDuplicates(
                            preview.copy(formatLabel = "${preview.formatLabel}（AI 未开启，已按常规模式解析）"),
                            existingTransactions
                        )
                        _importUiState.value = ImportUiState.Ready(annotated)
                    }
                    .onFailure { err ->
                        _importUiState.value = ImportUiState.Error(err.message ?: "无法解析导入内容（请先在「设置 -> AI理解」启用本地模型或在线 API）")
                    }
                return@launch
            }

            if (baseResult.isSuccess) {
                val basePreview = baseResult.getOrThrow()
                val enhanced = enhanceImportedTransactionsWithAi(
                    context = context,
                    aiConfig = aiConfig,
                    rules = rules,
                    items = basePreview.transactions
                )
                val annotated = ManualImportManager.annotateDuplicates(
                    basePreview.copy(
                        formatLabel = "${basePreview.formatLabel} + AI理解",
                        transactions = enhanced
                    ),
                    existingTransactions
                )
                _importUiState.value = ImportUiState.Ready(annotated)
            } else {
                val lines = rawContent.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(30)
                val aiItems = mutableListOf<LedgerTransaction>()
                var skipped = 0
                val activeCategories = com.example.model.CategoryCatalog.allActiveCategories(settingsState.value)
                for (line in lines) {
                    val res = aiManager.enhanceOrParseNotification(
                        context = context,
                        config = aiConfig,
                        packageName = "manual.import",
                        title = "手动导入",
                        content = line,
                        fallbackParsed = null,
                        availableRules = rules,
                        activeCategories = activeCategories
                    )
                    val parsed = res.getOrNull()
                    if (parsed != null && parsed.isFinancialTransaction && parsed.amountCents > 0L) {
                        aiItems.add(
                            LedgerTransaction(
                                amountCents = parsed.amountCents,
                                currencyCode = parsed.currencyCode,
                                type = parsed.type.name,
                                category = parsed.category,
                                merchant = parsed.merchant,
                                channel = if (parsed.channel == "未知渠道") "手动导入" else parsed.channel,
                                cardLastFour = parsed.cardLastFour,
                                sourceApp = SourceApp.IMPORT.name,
                                parseMethod = ParseMethod.LOCAL_NLP.name,
                                rawNotification = line.take(220),
                                occurredAtMillis = parsed.occurredAtMillis ?: System.currentTimeMillis(),
                                confirmed = true
                            )
                        )
                    } else {
                        skipped++
                    }
                }
                if (aiItems.isNotEmpty()) {
                    val annotated = ManualImportManager.annotateDuplicates(
                        ImportPreviewResult(
                            formatLabel = "AI 理解文本导入",
                            transactions = aiItems,
                            skippedCount = skipped
                        ),
                        existingTransactions
                    )
                    _importUiState.value = ImportUiState.Ready(annotated)
                } else {
                    _importUiState.value = ImportUiState.Error(
                        baseResult.exceptionOrNull()?.message ?: "规则与 AI 均未能从文本中提取有效账单"
                    )
                }
            }
        }
    }

    fun previewManualImportBytes(
        bytes: ByteArray,
        fileNameHint: String? = null,
        context: android.content.Context? = null,
        useRuleMatching: Boolean = true,
        useAiUnderstanding: Boolean = false
    ) {
        viewModelScope.launch {
            val rules = (rulesState.value as? UiState.Success)?.data ?: repository.getRulesOnce()
            val existingTransactions = repository.getAllTransactionsOnce()
            _importUiState.value = ImportUiState.Parsing(
                if (useAiUnderstanding) "正在读取文档并调用 AI 理解增强..." else "正在解析所选文档并识别重复条目..."
            )
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ManualImportManager.parseImportedBytes(
                    bytes = bytes,
                    fileNameHint = fileNameHint,
                    customRules = rules,
                    useRuleMatching = useRuleMatching
                )
            }
            result
                .onSuccess { preview ->
                    if (useAiUnderstanding && context != null) {
                        val aiConfig = repository.getAiConfigOnce()
                        if (aiConfig.providerMode != AiProviderMode.DISABLED) {
                            val enhanced = enhanceImportedTransactionsWithAi(
                                context = context,
                                aiConfig = aiConfig,
                                rules = rules,
                                items = preview.transactions
                            )
                            val annotated = ManualImportManager.annotateDuplicates(
                                preview.copy(
                                    formatLabel = "${preview.formatLabel} + AI理解",
                                    transactions = enhanced
                                ),
                                existingTransactions
                            )
                            _importUiState.value = ImportUiState.Ready(annotated)
                            return@onSuccess
                        }
                    }
                    val annotated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        ManualImportManager.annotateDuplicates(preview, existingTransactions)
                    }
                    _importUiState.value = ImportUiState.Ready(annotated)
                }
                .onFailure { err ->
                    _importUiState.value = ImportUiState.Error(err.message ?: "无法解析所选文件")
                }
        }
    }

    private suspend fun enhanceImportedTransactionsWithAi(
        context: android.content.Context,
        aiConfig: AiConfig,
        rules: List<CustomParsingRule>,
        items: List<LedgerTransaction>
    ): List<LedgerTransaction> {
        val aiLimit = if (aiConfig.providerMode == AiProviderMode.ONLINE_API) 25 else 80
        return items.mapIndexed { idx, tx ->
            if (idx >= aiLimit) return@mapIndexed tx
            val summaryText = buildString {
                append("交易对方:").append(tx.merchant)
                tx.productName?.let { append(" 商品:").append(it) }
                tx.tradeType?.let { append(" 交易类型:").append(it) }
                append(" 金额:").append("%.2f".format(java.util.Locale.US, tx.amountCents / 100.0)).append(" ").append(tx.currencyCode)
                append(" 支付方式:").append(tx.channel)
                tx.tradeStatus?.let { append(" 当前状态:").append(it) }
                tx.note?.let { append(" 备注:").append(it) }
                tx.rawNotification?.let { append(" 原文:").append(it) }
            }
            val activeCategories = com.example.model.CategoryCatalog.allActiveCategories(settingsState.value)
            val aiRes = aiManager.enhanceOrParseNotification(
                context = context,
                config = aiConfig,
                packageName = "manual.import",
                title = "账单导入增强",
                content = summaryText,
                fallbackParsed = null,
                availableRules = rules,
                activeCategories = activeCategories
            ).getOrNull()
            if (aiRes != null) {
                tx.copy(
                    category = aiRes.category.takeIf { it.isNotBlank() && it != "其他" } ?: tx.category,
                    merchant = if (tx.merchant == "导入账单" || tx.merchant == "导入记录") aiRes.merchant.ifBlank { tx.merchant } else tx.merchant,
                    parseMethod = ParseMethod.LOCAL_NLP.name
                )
            } else {
                tx
            }
        }
    }

    fun commitManualImport(
        items: List<LedgerTransaction>,
        skippedDuplicateCount: Int = 0,
        unselectedCount: Int = 0
    ) {
        viewModelScope.launch {
            if (items.isEmpty()) {
                _importUiState.value = ImportUiState.Idle
                _actionMessage.value = if (skippedDuplicateCount > 0) {
                    "未选择待导入账单（已跳过 $skippedDuplicateCount 笔重复）"
                } else {
                    "未选择可导入的账单"
                }
                return@launch
            }
            repository.importTransactions(items)
                .onSuccess { count ->
                    _importUiState.value = ImportUiState.Idle
                    val extra = when {
                        unselectedCount > 0 -> "（未勾选跳过 $unselectedCount 笔）"
                        skippedDuplicateCount > 0 -> "（已跳过 $skippedDuplicateCount 笔重复）"
                        else -> ""
                    }
                    _actionMessage.value = "成功导入 $count 笔账单$extra"
                }
                .onFailure { err ->
                    _actionMessage.value = "导入失败：${err.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun saveWebDavConfig(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
                .onFailure { _actionMessage.value = "保存 WebDAV 配置失败" }
        }
    }

    fun testWebDavConnection(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
            _webDavActionState.value = WebDavActionState.Working("正在测试连接...")
            webDavSyncManager.testConnection(config)
                .onSuccess { msg -> _webDavActionState.value = WebDavActionState.Success(msg) }
                .onFailure { err -> _webDavActionState.value = WebDavActionState.Error(err.message ?: "连接失败") }
        }
    }

    fun backupToWebDav(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
            _webDavActionState.value = WebDavActionState.Working("正在上传备份...")
            webDavSyncManager.backupToWebDav(config, repository)
                .onSuccess { msg -> _webDavActionState.value = WebDavActionState.Success(msg) }
                .onFailure { err -> _webDavActionState.value = WebDavActionState.Error(err.message ?: "备份失败") }
        }
    }

    fun restoreFromWebDav(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
            _webDavActionState.value = WebDavActionState.Working("正在下载并合并账本...")
            webDavSyncManager.restoreFromWebDav(config, repository)
                .onSuccess { msg -> _webDavActionState.value = WebDavActionState.Success(msg) }
                .onFailure { err -> _webDavActionState.value = WebDavActionState.Error(err.message ?: "恢复失败") }
        }
    }

    fun exportLocalBackup(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _localBackupActionState.value = LocalBackupActionState.Working("正在导出本地备份...")
            LocalBackupManager.exportBackupToUri(context, uri, repository)
                .onSuccess { msg ->
                    _localBackupActionState.value = LocalBackupActionState.Success(msg)
                    val s = repository.getSettingsOnce()
                    BackupReminderScheduler.schedule(
                        context = context,
                        enabled = s.backupReminderEnabled,
                        intervalHours = s.backupReminderIntervalHours,
                        lastLocalBackupMillis = s.lastLocalBackupMillis,
                        lastBackupReminderMillis = s.lastBackupReminderMillis
                    )
                }
                .onFailure { err ->
                    _localBackupActionState.value = LocalBackupActionState.Error(err.message ?: "导出失败")
                }
        }
    }

    fun restoreLocalBackup(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _localBackupActionState.value = LocalBackupActionState.Working("正在读取并恢复本地备份...")
            LocalBackupManager.restoreBackupFromUri(context, uri, repository)
                .onSuccess { msg ->
                    _localBackupActionState.value = LocalBackupActionState.Success(msg)
                }
                .onFailure { err ->
                    _localBackupActionState.value = LocalBackupActionState.Error(err.message ?: "恢复失败")
                }
        }
    }

    fun updateBackupReminder(
        context: android.content.Context,
        enabled: Boolean,
        intervalHours: Int
    ) {
        viewModelScope.launch {
            val safeHours = intervalHours.coerceIn(1, 8760)
            val current = repository.getSettingsOnce()
            val updated = current.copy(
                backupReminderEnabled = enabled,
                backupReminderIntervalHours = safeHours,
                lastLocalBackupMillis = if (enabled && current.lastLocalBackupMillis == 0L && current.lastBackupReminderMillis == 0L) {
                    System.currentTimeMillis()
                } else {
                    current.lastLocalBackupMillis
                }
            )
            repository.saveSettings(updated)
            BackupReminderScheduler.schedule(
                context = context,
                enabled = enabled,
                intervalHours = safeHours,
                lastLocalBackupMillis = updated.lastLocalBackupMillis,
                lastBackupReminderMillis = updated.lastBackupReminderMillis
            )
        }
    }

    fun sendTestUrgentBackupNotification(context: android.content.Context) {
        val intervalText = BackupReminderScheduler.formatIntervalLabel(settingsState.value.backupReminderIntervalHours)
        val ok = NotificationBridge.postUrgentBackupReminder(
            context = context,
            title = "【通知】请及时备份小本本账单",
            body = "定期备份通知已生效（每 $intervalText），点击可直接前往导出本地 JSON 备份。"
        )
        if (ok) {
            _localBackupActionState.value = LocalBackupActionState.Success("已发送测试通知")
        } else {
            _localBackupActionState.value = LocalBackupActionState.Error("未获得通知发送权限，请先授权后再试")
        }
    }

    fun checkBackupReminderDue(context: android.content.Context) {
        viewModelScope.launch {
            BackupReminderScheduler.checkDueAndNotifyIfNeeded(context)
        }
    }

    fun buildMonthlyInsight(year: Int, monthZeroBased: Int): String {
        val cal = Calendar.getInstance().apply {
            set(year, monthZeroBased, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return buildPeriodInsight(AnalyticsGranularity.MONTH, cal.timeInMillis)
    }

    fun buildPeriodInsight(
        granularity: AnalyticsGranularity,
        anchorMillis: Long,
        scopedTransactions: List<LedgerTransaction>? = null,
        bookNamePrefix: String? = null
    ): String {
        val sourceTx = scopedTransactions ?: ((transactionsState.value as? UiState.Success)?.data ?: emptyList())
        val periodTx = filterByGranularity(sourceTx, granularity, anchorMillis)
        val rawLabel = formatPeriodTitle(granularity, anchorMillis)
        val periodLabel = if (!bookNamePrefix.isNullOrBlank()) "「$bookNamePrefix」$rawLabel" else rawLabel
        val settings = settingsState.value
        val rates = exchangeRateState.value.ratesToCny
        return LocalSpendingAnalyzer.generatePeriodSummary(
            periodLabel = periodLabel,
            granularity = granularity,
            transactions = periodTx,
            monthlyBudgetCents = settings.monthlyBudgetCents,
            baseCurrencyCode = settings.baseCurrencyCode,
            ratesToCny = rates
        )
    }

    fun convertToBaseCents(tx: LedgerTransaction): Long {
        return CurrencyExchangeManager.convertCents(
            amountCents = tx.amountCents,
            fromCurrency = tx.currencyCode,
            toCurrency = settingsState.value.baseCurrencyCode,
            ratesToCny = exchangeRateState.value.ratesToCny
        )
    }

    fun liveRateToBase(tx: LedgerTransaction): Double {
        return CurrencyExchangeManager.rateBetween(
            fromCurrency = tx.currencyCode,
            toCurrency = settingsState.value.baseCurrencyCode,
            ratesToCny = exchangeRateState.value.ratesToCny
        )
    }

    fun recordedSnapshotDisplay(tx: LedgerTransaction): Triple<String, Double, Long> {
        val base = if (tx.recordedRateToBase > 0.0) {
            tx.recordedBaseCurrencyCode.ifBlank { settingsState.value.baseCurrencyCode }
        } else {
            settingsState.value.baseCurrencyCode
        }
        val rate = if (tx.recordedRateToBase > 0.0) {
            tx.recordedRateToBase
        } else {
            CurrencyExchangeManager.rateBetween(
                fromCurrency = tx.currencyCode,
                toCurrency = base,
                ratesToCny = exchangeRateState.value.ratesToCny
            )
        }
        val cents = kotlin.math.round(tx.amountCents * rate).toLong()
        return Triple(base, rate, cents)
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun filterByYear(list: List<LedgerTransaction>, year: Int): List<LedgerTransaction> {
            val cal = Calendar.getInstance()
            return list.filter { tx ->
                cal.timeInMillis = tx.occurredAtMillis
                cal.get(Calendar.YEAR) == year
            }
        }

        fun filterByMonth(
            list: List<LedgerTransaction>,
            year: Int,
            monthZeroBased: Int
        ): List<LedgerTransaction> {
            val cal = Calendar.getInstance()
            return list.filter { tx ->
                cal.timeInMillis = tx.occurredAtMillis
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == monthZeroBased
            }
        }

        fun filterByWeek(list: List<LedgerTransaction>, anchorMillis: Long): List<LedgerTransaction> {
            val (start, end) = weekRangeMillis(anchorMillis)
            return list.filter { it.occurredAtMillis in start..end }
        }

        fun filterByDay(list: List<LedgerTransaction>, anchorMillis: Long): List<LedgerTransaction> {
            val cal = Calendar.getInstance()
            cal.timeInMillis = anchorMillis
            val y = cal.get(Calendar.YEAR)
            val d = cal.get(Calendar.DAY_OF_YEAR)
            return list.filter { tx ->
                cal.timeInMillis = tx.occurredAtMillis
                cal.get(Calendar.YEAR) == y && cal.get(Calendar.DAY_OF_YEAR) == d
            }
        }

        fun filterByGranularity(
            list: List<LedgerTransaction>,
            granularity: AnalyticsGranularity,
            anchorMillis: Long
        ): List<LedgerTransaction> {
            val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
            return when (granularity) {
                AnalyticsGranularity.YEAR -> filterByYear(list, cal.get(Calendar.YEAR))
                AnalyticsGranularity.MONTH -> filterByMonth(list, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                AnalyticsGranularity.WEEK -> filterByWeek(list, anchorMillis)
                AnalyticsGranularity.DAY -> filterByDay(list, anchorMillis)
            }
        }

        fun weekRangeMillis(anchorMillis: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                timeInMillis = anchorMillis
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return start to cal.timeInMillis
        }

        fun shiftPeriodAnchor(
            granularity: AnalyticsGranularity,
            anchorMillis: Long,
            delta: Int
        ): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
            when (granularity) {
                AnalyticsGranularity.YEAR -> cal.add(Calendar.YEAR, delta)
                AnalyticsGranularity.MONTH -> cal.add(Calendar.MONTH, delta)
                AnalyticsGranularity.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, delta)
                AnalyticsGranularity.DAY -> cal.add(Calendar.DAY_OF_YEAR, delta)
            }
            return cal.timeInMillis
        }

        fun formatPeriodTitle(
            granularity: AnalyticsGranularity,
            anchorMillis: Long
        ): String {
            val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
            return when (granularity) {
                AnalyticsGranularity.YEAR -> "${cal.get(Calendar.YEAR)}年"
                AnalyticsGranularity.MONTH -> "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月"
                AnalyticsGranularity.WEEK -> {
                    val (start, end) = weekRangeMillis(anchorMillis)
                    val sCal = Calendar.getInstance().apply { timeInMillis = start }
                    val eCal = Calendar.getInstance().apply { timeInMillis = end }
                    "${sCal.get(Calendar.MONTH) + 1}月${sCal.get(Calendar.DAY_OF_MONTH)}日 - ${eCal.get(Calendar.MONTH) + 1}月${eCal.get(Calendar.DAY_OF_MONTH)}日"
                }
                AnalyticsGranularity.DAY -> {
                    "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
                }
            }
        }

        fun filterByHomeDimension(
            list: List<LedgerTransaction>,
            dimension: HomeStatsDimension,
            nowMillis: Long = System.currentTimeMillis()
        ): List<LedgerTransaction> {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            return when (dimension) {
                HomeStatsDimension.TODAY -> filterByDay(list, nowMillis)
                HomeStatsDimension.THIS_WEEK -> filterByWeek(list, nowMillis)
                HomeStatsDimension.THIS_MONTH -> filterByMonth(list, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                HomeStatsDimension.LAST_MONTH -> {
                    cal.add(Calendar.MONTH, -1)
                    filterByMonth(list, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                }
                HomeStatsDimension.THIS_YEAR -> filterByYear(list, cal.get(Calendar.YEAR))
                HomeStatsDimension.ALL_TIME -> list
            }
        }
    }
}
