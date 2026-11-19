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
import com.example.model.CustomParsingRule
import com.example.model.ExchangeRateAutoUpdateFrequency
import com.example.model.ExchangeRateState
import com.example.model.LedgerTransaction
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

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
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
        occurredAtMillis: Long = System.currentTimeMillis()
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
        confirmed: Boolean = true
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
                confirmed = confirmed
            )
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

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
                .onFailure { _actionMessage.value = "删除失败：${it.localizedMessage ?: "请重试"}" }
        }
    }

    fun deleteTransactions(txIds: Collection<String>) {
        viewModelScope.launch {
            repository.deleteTransactions(txIds)
                .onSuccess { count ->
                    if (count > 0) _actionMessage.value = "已删除 $count 笔账单"
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
            val result = aiManager.enhanceOrParseNotification(
                context = context,
                config = config,
                packageName = packageName,
                title = title,
                content = content,
                fallbackParsed = null
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
            repository.deleteParsingRule(ruleId)
                .onFailure { _actionMessage.value = "删除规则失败" }
        }
    }

    fun saveSettings(settings: BudgetSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
                .onFailure { _actionMessage.value = "保存设置失败" }
        }
    }

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
        _aiModelDownloadState.value = AiModelDownloadState.Idle
        _actionMessage.value = "已清理本地模型缓存"
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
            aiManager.enhanceOrParseNotification(
                context = context,
                config = config,
                packageName = "manual.ai.test",
                title = sampleTitle,
                content = sampleContent,
                fallbackParsed = null
            )
                .onSuccess { parsed -> _aiTestState.value = AiTestState.Success(parsed) }
                .onFailure { err -> _aiTestState.value = AiTestState.Error(err.message ?: "AI理解测试失败") }
        }
    }

    fun previewManualImport(rawContent: String, fileNameHint: String? = null) {
        val rules = (rulesState.value as? UiState.Success)?.data ?: emptyList()
        ManualImportManager.parseImportedContent(rawContent, fileNameHint, rules)
            .onSuccess { preview ->
                _importUiState.value = ImportUiState.Ready(preview)
            }
            .onFailure { err ->
                _importUiState.value = ImportUiState.Error(err.message ?: "无法解析导入内容")
            }
    }

    fun previewManualImportBytes(bytes: ByteArray, fileNameHint: String? = null) {
        val rules = (rulesState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ManualImportManager.parseImportedBytes(bytes, fileNameHint, rules)
            }
            result
                .onSuccess { preview ->
                    _importUiState.value = ImportUiState.Ready(preview)
                }
                .onFailure { err ->
                    _importUiState.value = ImportUiState.Error(err.message ?: "无法解析所选文件")
                }
        }
    }

    fun commitManualImport(items: List<LedgerTransaction>) {
        viewModelScope.launch {
            repository.importTransactions(items)
                .onSuccess { count ->
                    _importUiState.value = ImportUiState.Idle
                    _actionMessage.value = "成功导入 $count 笔账单"
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

    fun buildPeriodInsight(granularity: AnalyticsGranularity, anchorMillis: Long): String {
        val allTx = (transactionsState.value as? UiState.Success)?.data ?: emptyList()
        val periodTx = filterByGranularity(allTx, granularity, anchorMillis)
        val periodLabel = formatPeriodTitle(granularity, anchorMillis)
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
    }
}
