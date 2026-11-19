package com.example.ui

import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CardDensity
import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateAutoUpdateFrequency
import com.example.model.ExchangeRateState
import com.example.model.FabPlacement
import com.example.model.HomeHeaderOrder
import com.example.model.LatinNumberFont
import com.example.model.PredictiveBackStyle
import com.example.model.TabClickAnimationStyle
import com.example.model.ThemeMode
import com.example.model.WebDavConfig
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateTimeFull
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.ImportUiState
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.RateUpdateState
import com.example.viewmodel.WebDavActionState

@Composable
fun WebDavSettingsScreen(
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val webDavConfig by viewModel.webDavConfigState.collectAsStateWithLifecycle()
    val webDavAction by viewModel.webDavActionState.collectAsStateWithLifecycle()

    var serverUrlInput by remember(webDavConfig.serverUrl) { mutableStateOf(webDavConfig.serverUrl) }
    var usernameInput by remember(webDavConfig.username) { mutableStateOf(webDavConfig.username) }
    var passwordInput by remember(webDavConfig.password) { mutableStateOf(webDavConfig.password) }
    var remoteFolderInput by remember(webDavConfig.remoteFolder) {
        mutableStateOf(webDavConfig.remoteFolder.ifBlank { "XiaoBenBen" })
    }
    var showPassword by remember { mutableStateOf(false) }

    val currentConfig = WebDavConfig(
        serverUrl = serverUrlInput.trim(),
        username = usernameInput.trim(),
        password = passwordInput,
        remoteFolder = remoteFolderInput.trim().ifBlank { "XiaoBenBen" },
        lastSyncMillis = webDavConfig.lastSyncMillis,
        lastSyncStatus = webDavConfig.lastSyncStatus
    )
    val isWorking = webDavAction is WebDavActionState.Working

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "webdav_form") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("WebDAV 服务器配置", style = MaterialTheme.typography.titleMedium)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {
                                    serverUrlInput = "https://dav.jianguoyun.com/dav/"
                                    if (remoteFolderInput.isBlank()) remoteFolderInput = "XiaoBenBen"
                                },
                                label = { Text("填入坚果云地址") }
                            )
                            AssistChip(
                                onClick = { onShowMessage("备份文件：xiaobenben_backup.json") },
                                label = { Text("目录: ${remoteFolderInput.ifBlank { "XiaoBenBen" }}") }
                            )
                        }

                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            label = { Text("WebDAV 服务器地址") },
                            placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_server_url_input")
                        )

                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            label = { Text("账号 / 邮箱") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_username_input")
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("应用密码 / 授权码") },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) AppIcons.VisibilityOff else AppIcons.Visibility,
                                        contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_password_input")
                        )

                        OutlinedTextField(
                            value = remoteFolderInput,
                            onValueChange = { remoteFolderInput = it },
                            label = { Text("远程文件夹") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("webdav_folder_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                enabled = !isWorking,
                                onClick = { viewModel.testWebDavConnection(currentConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_test_button")
                            ) {
                                Icon(AppIcons.WifiFind, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("测试连接")
                            }
                            OutlinedButton(
                                enabled = !isWorking,
                                onClick = { viewModel.saveWebDavConfig(currentConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_save_button")
                            ) {
                                Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("保存配置")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                enabled = !isWorking,
                                onClick = { viewModel.backupToWebDav(currentConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_backup_button")
                            ) {
                                Icon(AppIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即备份")
                            }
                            OutlinedButton(
                                enabled = !isWorking,
                                onClick = { viewModel.restoreFromWebDav(currentConfig) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("webdav_restore_button")
                            ) {
                                Icon(AppIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("恢复合并")
                            }
                        }

                        when (val action = webDavAction) {
                            is WebDavActionState.Idle -> Unit
                            is WebDavActionState.Working -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(action.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is WebDavActionState.Success -> {
                                Surface(
                                    color = IncomeJade.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = action.message,
                                        color = IncomeJade,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is WebDavActionState.Error -> {
                                Surface(
                                    color = ExpenseCoral.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = action.message,
                                        color = ExpenseCoral,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        if (webDavConfig.lastSyncMillis > 0L && webDavConfig.lastSyncStatus.isNotBlank()) {
                            Text(
                                text = "最近同步 (${formatDateTimeFull(webDavConfig.lastSyncMillis)}): ${webDavConfig.lastSyncStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrencySettingsScreen(
    settings: BudgetSettings,
    exchangeRates: ExchangeRateState,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val rateUpdateState by viewModel.rateUpdateState.collectAsStateWithLifecycle()
    val numberFont = LocalNumberFontFamily.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "base_currency") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("统计主币种", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "不同币种的账单将按汇率数据库折算为主币种计入月度总计与分析。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CurrencyCatalog.currencies.forEach { info ->
                                FilterChip(
                                    selected = settings.baseCurrencyCode.equals(info.code, ignoreCase = true),
                                    onClick = { viewModel.saveSettings(settings.copy(baseCurrencyCode = info.code)) },
                                    label = { Text("${info.code} ${info.chineseName}") }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "exchange_rate_db") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("汇率数据库", style = MaterialTheme.typography.titleMedium)
                                val timeHint = if (exchangeRates.updatedAtMillis > 0L) {
                                    "上次更新：${formatDateTimeFull(exchangeRates.updatedAtMillis)}"
                                } else {
                                    "使用内置参考汇率 · 仅点击更新时联网获取"
                                }
                                Text(
                                    text = timeHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "数据源：${exchangeRates.dataSource}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.testTag("exchange_rate_source_label")
                                        )
                                        if (exchangeRates.rateDateText.isNotBlank()) {
                                            Text(
                                                text = "牌价发布：${exchangeRates.rateDateText}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            enabled = rateUpdateState !is RateUpdateState.Loading,
                            onClick = { viewModel.updateExchangeRateDatabase() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("update_exchange_rates_button")
                        ) {
                            if (rateUpdateState is RateUpdateState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在联网更新汇率...")
                            } else {
                                Icon(AppIcons.CurrencyExchange, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("联网更新实时汇率数据库")
                            }
                        }

                        when (val state = rateUpdateState) {
                            is RateUpdateState.Success -> {
                                Text(state.message, color = IncomeJade, style = MaterialTheme.typography.bodySmall)
                            }
                            is RateUpdateState.Error -> {
                                Text(state.message, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                            }
                            else -> Unit
                        }

                        HorizontalDivider()

                        val currentAutoFreq = ExchangeRateAutoUpdateFrequency.fromRaw(settings.exchangeRateUpdateFrequency)
                        Text("自动更新频率", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = if (currentAutoFreq == ExchangeRateAutoUpdateFrequency.MANUAL) {
                                "当前为仅手动更新，点击上方按钮时才会联网获取牌价。"
                            } else {
                                "打开或回到应用时若距上次更新已超过「${currentAutoFreq.label}」，将自动静默刷新汇率库。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExchangeRateAutoUpdateFrequency.entries.forEach { freq ->
                                FilterChip(
                                    selected = currentAutoFreq == freq,
                                    onClick = {
                                        viewModel.saveSettings(settings.copy(exchangeRateUpdateFrequency = freq.name))
                                        if (freq != ExchangeRateAutoUpdateFrequency.MANUAL) {
                                            viewModel.checkExchangeRateAutoUpdateIfDue()
                                        }
                                    },
                                    label = { Text(freq.label) },
                                    modifier = Modifier.testTag("rate_freq_${freq.name}")
                                )
                            }
                        }

                        HorizontalDivider()

                        CurrencyCatalog.currencies.filter { it.code != "CNY" }.forEach { info ->
                            val rate = exchangeRates.ratesToCny[info.code] ?: info.defaultRateToCny
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1 ${info.code} (${info.chineseName})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "≈ ¥%.4f".format(rate),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = numberFont),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManualImportScreen(
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val importState by viewModel.importUiState.collectAsStateWithLifecycle()
    var pastedText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: ByteArray(0)
            selectedFileName = fileName
            viewModel.previewManualImportBytes(bytes, fileName)
        }.onFailure {
            onShowMessage("读取文件失败：${it.localizedMessage ?: "无法打开文件"}")
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "import_file_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("从文件导入", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "支持导入 XLSX 表格、微信/支付宝导出的 CSV、以及备份 JSON 文件（自动跳过前置说明并识别表头）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/csv",
                                        "application/json",
                                        "text/*",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pick_import_file_button")
                        ) {
                            Icon(AppIcons.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(selectedFileName?.let { "已选：$it (重新选择)" } ?: "选择 XLSX / CSV / JSON 文件")
                        }
                    }
                }
            }

            item(key = "import_text_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("粘贴文本批量导入", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            label = { Text("粘贴多行账单、银行短信、CSV 或 JSON") },
                            placeholder = { Text("例如：\n星巴克 42.00\nUSD 15.00 OpenAI\n【招商银行】您账户8812消费人民币128.00元。") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_paste_input")
                        )
                        OutlinedButton(
                            onClick = { viewModel.previewManualImport(pastedText, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("parse_import_text_button")
                        ) {
                            Text("解析并预览")
                        }
                    }
                }
            }

            when (val state = importState) {
                is ImportUiState.Idle -> Unit
                is ImportUiState.Error -> {
                    item(key = "import_error") {
                        Surface(
                            color = ExpenseCoral.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                color = ExpenseCoral,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is ImportUiState.Ready -> {
                    val preview = state.preview
                    item(key = "import_preview_header") {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "${preview.formatLabel} · 识别出 ${preview.transactions.size} 笔账单" +
                                        if (preview.skippedCount > 0) "（跳过 ${preview.skippedCount} 行）" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (preview.detectedColumnsSummary.isNotBlank()) {
                                    Text(
                                        text = "映射列：${preview.detectedColumnsSummary}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.clearImportPreview() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        onClick = { viewModel.commitManualImport(preview.transactions) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("commit_import_button")
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("确认导入")
                                    }
                                }
                            }
                        }
                    }
                    items(preview.transactions.take(12), key = { it.id }) { tx ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "${tx.category} · ${tx.channel} · ${tx.currencyCode}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(
    appearance: AppearanceSettings,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "color_and_font") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("色彩与字体", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("莫奈动态取色 (Material You)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (supportsMonet) "跟随系统壁纸自动生成主题配色" else "当前系统低于 Android 12，自动使用默认配色",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearance.dynamicColor,
                                enabled = supportsMonet,
                                onCheckedChange = { enabled ->
                                    viewModel.saveAppearanceSettings(appearance.copy(dynamicColor = enabled))
                                },
                                modifier = Modifier.testTag("toggle_monet_color")
                            )
                        }

                        HorizontalDivider()

                        Text("深浅色模式", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = appearance.themeMode == mode,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(themeMode = mode)) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        Text("英文与数字字体", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LatinNumberFont.entries.forEach { font ->
                                FilterChip(
                                    selected = appearance.latinNumberFont == font,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(latinNumberFont = font)) },
                                    label = { Text(font.label) },
                                    modifier = Modifier.testTag("font_chip_${font.name}")
                                )
                            }
                        }
                    }
                }
            }

            item(key = "layout_customization") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("卡片大小与元素位置", style = MaterialTheme.typography.titleMedium)

                        Text("卡片大小 / 列表密度", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CardDensity.entries.forEach { density ->
                                FilterChip(
                                    selected = appearance.cardDensity == density,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(cardDensity = density)) },
                                    label = { Text(density.label) },
                                    modifier = Modifier.testTag("density_chip_${density.name}")
                                )
                            }
                        }

                        Text("主页金融 Tips 位置", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeHeaderOrder.entries.forEach { order ->
                                FilterChip(
                                    selected = appearance.homeHeaderOrder == order,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(homeHeaderOrder = order)) },
                                    label = { Text(order.label) },
                                    modifier = Modifier.testTag("header_order_chip_${order.name}")
                                )
                            }
                        }

                        Text("记一笔按钮位置", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FabPlacement.entries.forEach { placement ->
                                FilterChip(
                                    selected = appearance.fabPlacement == placement,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(fabPlacement = placement)) },
                                    label = { Text(placement.label) },
                                    modifier = Modifier.testTag("fab_placement_chip_${placement.name}")
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("账单行显示支付渠道标签", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = appearance.showChannelBadge,
                                onCheckedChange = {
                                    viewModel.saveAppearanceSettings(appearance.copy(showChannelBadge = it))
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("账单行显示识别方式标签", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = appearance.showParseBadge,
                                onCheckedChange = {
                                    viewModel.saveAppearanceSettings(appearance.copy(showParseBadge = it))
                                }
                            )
                        }
                    }
                }
            }

            item(key = "navigation_and_gestures") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("导航动画与返回手势", style = MaterialTheme.typography.titleMedium)

                        Text("主页点击 Tab 切换动画（支持左右横滑切换页面）", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TabClickAnimationStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = appearance.tabClickAnimation == style,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(tabClickAnimation = style)) },
                                    label = { Text(style.label) },
                                    modifier = Modifier.testTag("tab_anim_chip_${style.name}")
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("预测性返回与横滑返回上一级", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "支持系统边缘预测性返回过渡动画，并可在多级菜单中向右横滑返回上一级",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearance.predictiveBackEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.saveAppearanceSettings(appearance.copy(predictiveBackEnabled = enabled))
                                },
                                modifier = Modifier.testTag("toggle_predictive_back")
                            )
                        }

                        if (appearance.predictiveBackEnabled) {
                            Text("预测性返回与横滑过渡样式", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PredictiveBackStyle.entries.forEach { style ->
                                    FilterChip(
                                        selected = appearance.predictiveBackStyle == style,
                                        onClick = {
                                            viewModel.saveAppearanceSettings(appearance.copy(predictiveBackStyle = style))
                                        },
                                        label = { Text(style.label) },
                                        modifier = Modifier.testTag("predictive_back_style_chip_${style.name}")
                                    )
                                }
                            }
                            Text(
                                text = appearance.predictiveBackStyle.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "home_tips_customization") {
                var tagInput by remember(appearance.tipTagText) { mutableStateOf(appearance.tipTagText) }
                var tipsContentInput by remember(appearance.customTipsRaw) { mutableStateOf(appearance.customTipsRaw) }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("首页顶部 Tips 文案与轮换内容", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = { next ->
                                tagInput = next.take(16)
                                viewModel.saveAppearanceSettings(appearance.copy(tipTagText = tagInput))
                            },
                            label = { Text("Tag 标签文案（留空默认显示“我告诉你”）") },
                            placeholder = { Text("默认显示：我告诉你") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tips_tag_input")
                        )

                        OutlinedTextField(
                            value = tipsContentInput,
                            onValueChange = { next ->
                                tipsContentInput = next
                                viewModel.saveAppearanceSettings(appearance.copy(customTipsRaw = next))
                            },
                            label = { Text("自定义轮换内容（每行一条，留空则使用默认词库）") },
                            placeholder = { Text("每行一条句子，点击首页横幅即可轮换展示") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tips_custom_content_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val defaultText = com.example.parser.FinanceHumorTips.defaultTipsMultiline()
                                    tipsContentInput = defaultText
                                    viewModel.saveAppearanceSettings(appearance.copy(customTipsRaw = defaultText))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("填入内置内容编辑")
                            }
                            OutlinedButton(
                                onClick = {
                                    tagInput = ""
                                    tipsContentInput = ""
                                    viewModel.saveAppearanceSettings(
                                        appearance.copy(tipTagText = "", customTipsRaw = "")
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("恢复默认")
                            }
                        }
                    }
                }
            }
        }
    }
}
