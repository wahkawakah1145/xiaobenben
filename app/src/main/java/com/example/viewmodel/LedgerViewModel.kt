package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LedgerRepository
import com.example.data.WebDavSyncManager
import com.example.model.BudgetSettings
import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import com.example.model.ParseMethod
import com.example.model.ParsedNotificationResult
import com.example.model.SourceApp
import com.example.model.TransactionType
import com.example.model.WebDavConfig
import com.example.parser.ChinaNotificationParser
import com.example.parser.LocalSpendingAnalyzer
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

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val webDavSyncManager: WebDavSyncManager = WebDavSyncManager()
) : ViewModel() {

    val transactionsState: StateFlow<UiState<List<LedgerTransaction>>> =
        repository.observeTransactions()
            .map<List<LedgerTransaction>, UiState<List<LedgerTransaction>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(UiState.Error(error.localizedMessage ?: "无法读取本地账单数据库"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val rulesState: StateFlow<UiState<List<CustomParsingRule>>> =
        repository.observeParsingRules()
            .map<List<CustomParsingRule>, UiState<List<CustomParsingRule>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(UiState.Error(error.localizedMessage ?: "无法读取本地解析规则"))
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

    private val _parseBenchState = MutableStateFlow<ParseBenchState>(ParseBenchState.Idle)
    val parseBenchState: StateFlow<ParseBenchState> = _parseBenchState.asStateFlow()

    private val _webDavActionState = MutableStateFlow<WebDavActionState>(WebDavActionState.Idle)
    val webDavActionState: StateFlow<WebDavActionState> = _webDavActionState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun resetParseBench() {
        _parseBenchState.value = ParseBenchState.Idle
    }

    fun addManualTransaction(
        amountCents: Long,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        cardLastFour: String?,
        note: String?,
        occurredAtMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val tx = LedgerTransaction(
                amountCents = amountCents,
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
                .onSuccess {
                    _actionMessage.value = "已记录：${merchant.ifBlank { category }} ¥%.2f".format(amountCents / 100.0)
                }
                .onFailure {
                    _actionMessage.value = "记录失败：${it.localizedMessage ?: "请检查输入"}"
                }
        }
    }

    fun updateTransaction(
        txId: String,
        amountCents: Long,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        cardLastFour: String?,
        note: String?,
        occurredAtMillis: Long
    ) {
        viewModelScope.launch {
            repository.updateTransaction(
                txId = txId,
                amountCents = amountCents,
                type = type,
                category = category,
                merchant = merchant,
                channel = channel,
                cardLastFour = cardLastFour,
                note = note,
                occurredAtMillis = occurredAtMillis,
                confirmed = true
            )
                .onSuccess { _actionMessage.value = "已更新本地账单" }
                .onFailure { _actionMessage.value = "更新失败：${it.localizedMessage ?: "请重试"}" }
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
                .onSuccess { _actionMessage.value = "已删除账单" }
                .onFailure { _actionMessage.value = "删除失败：${it.localizedMessage ?: "请重试"}" }
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
            _parseBenchState.value = ParseBenchState.Error("未能从该文本识别出有效动账金额（或命中验证码/非交易过滤规则）")
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
                type = parsed.type.name,
                category = parsed.category,
                merchant = parsed.merchant,
                channel = parsed.channel,
                cardLastFour = parsed.cardLastFour,
                sourceApp = parsed.sourceApp.name,
                parseMethod = parsed.parseMethod.name,
                rawNotification = "$rawTitle: $rawContent".trim().take(600),
                note = parsed.reasoning.take(200),
                occurredAtMillis = System.currentTimeMillis(),
                confirmed = true
            )
            repository.addTransaction(tx)
                .onSuccess {
                    _actionMessage.value = "已入账至本地数据库：${parsed.merchant} ¥%.2f".format(parsed.amountCents / 100.0)
                    _parseBenchState.value = ParseBenchState.Idle
                }
                .onFailure { error ->
                    _actionMessage.value = "入账失败：${error.localizedMessage ?: "请重试"}"
                }
        }
    }

    fun addCustomRule(
        name: String,
        packageFilter: String,
        keywordPattern: String,
        channelName: String,
        defaultCategory: String,
        defaultType: TransactionType
    ) {
        viewModelScope.launch {
            val rule = CustomParsingRule(
                name = name,
                packageFilter = packageFilter.ifBlank { "*" },
                keywordPattern = keywordPattern,
                channelName = channelName,
                defaultCategory = defaultCategory,
                defaultType = defaultType.name,
                enabled = true
            )
            repository.addParsingRule(rule)
                .onSuccess { _actionMessage.value = "已保存本地规则「$name」" }
                .onFailure { _actionMessage.value = "添加规则失败：${it.localizedMessage ?: "请检查输入"}" }
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
                .onSuccess { _actionMessage.value = "已删除规则" }
                .onFailure { _actionMessage.value = "删除规则失败" }
        }
    }

    fun saveSettings(settings: BudgetSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
                .onSuccess { _actionMessage.value = "本地偏好设置已保存" }
                .onFailure { _actionMessage.value = "保存设置失败" }
        }
    }

    fun saveWebDavConfig(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
                .onSuccess { _actionMessage.value = "WebDAV 配置已保存在本地" }
                .onFailure { _actionMessage.value = "保存 WebDAV 配置失败" }
        }
    }

    fun testWebDavConnection(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
            _webDavActionState.value = WebDavActionState.Working("正在测试 WebDAV 连接...")
            webDavSyncManager.testConnection(config)
                .onSuccess { msg -> _webDavActionState.value = WebDavActionState.Success(msg) }
                .onFailure { err -> _webDavActionState.value = WebDavActionState.Error(err.message ?: "连接失败") }
        }
    }

    fun backupToWebDav(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
            _webDavActionState.value = WebDavActionState.Working("正在打包并上传备份到 WebDAV...")
            webDavSyncManager.backupToWebDav(config, repository)
                .onSuccess { msg -> _webDavActionState.value = WebDavActionState.Success(msg) }
                .onFailure { err -> _webDavActionState.value = WebDavActionState.Error(err.message ?: "备份失败") }
        }
    }

    fun restoreFromWebDav(config: WebDavConfig) {
        viewModelScope.launch {
            repository.saveWebDavConfig(config)
            _webDavActionState.value = WebDavActionState.Working("正在从 WebDAV 下载并合并账本...")
            webDavSyncManager.restoreFromWebDav(config, repository)
                .onSuccess { msg -> _webDavActionState.value = WebDavActionState.Success(msg) }
                .onFailure { err -> _webDavActionState.value = WebDavActionState.Error(err.message ?: "恢复失败") }
        }
    }

    fun buildLocalMonthlyInsight(year: Int, monthZeroBased: Int): String {
        val allTx = (transactionsState.value as? UiState.Success)?.data ?: emptyList()
        val monthTx = filterByMonth(allTx, year, monthZeroBased)
        val monthLabel = "${year}年${monthZeroBased + 1}月"
        return LocalSpendingAnalyzer.generateMonthlySummary(
            monthLabel = monthLabel,
            transactions = monthTx,
            monthlyBudgetCents = settingsState.value.monthlyBudgetCents
        )
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5000L

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
    }
}
