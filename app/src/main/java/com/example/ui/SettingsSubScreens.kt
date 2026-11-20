package com.example.ui

import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.util.DesktopIconSyncManager
import com.example.util.ShortcutPermissionState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.luminance
import com.example.model.MainBottomTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppIconStyle
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CardDensity
import com.example.model.CurrencyCatalog
import com.example.model.CustomPaletteStyle
import com.example.model.ExchangeRateAutoUpdateFrequency
import com.example.model.ExchangeRateState
import com.example.model.FabPlacement
import com.example.model.HomeHeaderOrder
import com.example.model.IconVisualStyle
import com.example.model.LatinNumberFont
import com.example.model.PredictiveBackStyle
import com.example.model.TabClickAnimationStyle
import com.example.model.ThemeMode
import com.example.model.WebDavConfig
import com.example.ui.components.AppBrandIconAvatar
import com.example.ui.components.CustomColorPickerDialog
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateTimeFull
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.ImportUiState
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.RateUpdateState
import com.example.viewmodel.WebDavActionState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val context = LocalContext.current
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scope = rememberCoroutineScope()
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var pendingIconStyleId by remember(appearance.appIconStyleId) { mutableStateOf(appearance.appIconStyleId) }
    var showIconApplyConfirmDialog by remember { mutableStateOf(false) }
    var showShortcutPermissionDialog by remember { mutableStateOf(false) }
    var showShortcutCustomDialog by remember { mutableStateOf(false) }
    var showShortcutColorPicker by remember { mutableStateOf(false) }
    var shortcutCustomName by remember(appearance.homeBrandCustomName) {
        mutableStateOf(appearance.homeBrandCustomName.ifBlank { "小本本" })
    }
    var shortcutCustomStyleId by remember(appearance.appIconStyleId) { mutableStateOf(appearance.appIconStyleId) }
    var shortcutUseThemeColor by remember { mutableStateOf(true) }
    var shortcutCustomColorArgb by remember(appearance.customPrimaryColorArgb) {
        mutableStateOf(appearance.customPrimaryColorArgb)
    }
    var desktopSyncFeedback by remember { mutableStateOf<String?>(null) }

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
                        Text("色彩、调色板与字体", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("莫奈动态取色 (Material You)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (supportsMonet) "跟随系统壁纸自动取色；开启后同样支持下方选择调色板衍生风格" else "当前系统低于 Android 12，自动使用自定义调色板",
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

                        if (!appearance.dynamicColor || !supportsMonet) {
                            val currentSeed = Color(appearance.customPrimaryColorArgb.toInt())
                            val hexLabel = "#%06X".format(appearance.customPrimaryColorArgb.toInt() and 0xFFFFFF)

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showColorPickerDialog = true }
                                    .testTag("open_custom_color_picker_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(currentSeed)
                                            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "主题主色：$hexLabel",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "点击打开圆形色轮、HSV / RGB 滑块与 HEX 调色窗口",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(onClick = { showColorPickerDialog = true }) {
                                        Text("调色板")
                                    }
                                }
                            }
                        }

                        Text("调色板衍生风格", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CustomPaletteStyle.entries.forEach { paletteStyle ->
                                FilterChip(
                                    selected = appearance.customPaletteStyle == paletteStyle,
                                    onClick = {
                                        viewModel.saveAppearanceSettings(appearance.copy(customPaletteStyle = paletteStyle))
                                    },
                                    label = { Text(paletteStyle.label) },
                                    modifier = Modifier.testTag("palette_style_${paletteStyle.name}")
                                )
                            }
                        }
                        Text(
                            text = appearance.customPaletteStyle.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider()

                        Text("深浅色模式", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = appearance.themeMode == mode,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(themeMode = mode)) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("theme_mode_chip_${mode.name}")
                                )
                            }
                        }

                        val oledAvailable = appearance.themeMode != ThemeMode.LIGHT
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("OLED 纯黑背景", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (oledAvailable) {
                                        "深色模式生效时使用 #000000 纯黑背景与低亮度表面层级"
                                    } else {
                                        "切换到「深色」或「跟随系统」后可启用 OLED 纯黑背景"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearance.oledPureBlack,
                                enabled = oledAvailable,
                                onCheckedChange = { enabled ->
                                    viewModel.saveAppearanceSettings(appearance.copy(oledPureBlack = enabled))
                                },
                                modifier = Modifier.testTag("toggle_oled_pure_black")
                            )
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

            item(key = "page_dpi_scaling") {
                val dpiSnaps = listOf(
                    0.85f to "紧凑",
                    1.00f to "标准",
                    1.15f to "舒适",
                    1.30f to "大屏"
                )
                var draftDpiScale by remember(appearance.uiDensityScale) {
                    mutableFloatStateOf(appearance.uiDensityScale.coerceIn(0.80f, 1.35f))
                }
                var dpiTextInput by remember(appearance.uiDensityScale) {
                    mutableStateOf(String.format(Locale.US, "%.2f", appearance.uiDensityScale.coerceIn(0.80f, 1.35f)))
                }
                val hasPendingDpiChange = abs(draftDpiScale - appearance.uiDensityScale) >= 0.005f

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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("页面缩放与显示密度 (DPI)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (hasPendingDpiChange) {
                                        "已应用 ${(appearance.uiDensityScale * 100).roundToInt()}% · 待确认 ${(draftDpiScale * 100).roundToInt()}%，点击「应用」生效"
                                    } else {
                                        "支持 4 档磁吸微调及无级数值缩放，调整后点击「应用」生效"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasPendingDpiChange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${(draftDpiScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = LocalNumberFontFamily.current),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Slider(
                                value = draftDpiScale.coerceIn(0.80f, 1.35f),
                                onValueChange = { raw ->
                                    val snapped = dpiSnaps
                                        .firstOrNull { (target, _) -> abs(raw - target) <= 0.024f }
                                        ?.first
                                        ?: ((raw * 100f).roundToInt() / 100f)
                                    val clamped = snapped.coerceIn(0.80f, 1.35f)
                                    draftDpiScale = clamped
                                    dpiTextInput = String.format(Locale.US, "%.2f", clamped)
                                },
                                valueRange = 0.80f..1.35f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dpi_scale_slider")
                            )
                            OutlinedTextField(
                                value = dpiTextInput,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() || it == '.' }.take(5)
                                    dpiTextInput = filtered
                                    val parsed = filtered.toFloatOrNull()
                                    if (parsed != null && parsed in 0.80f..1.35f) {
                                        draftDpiScale = parsed
                                    }
                                },
                                label = { Text("倍率") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .width(86.dp)
                                    .testTag("dpi_scale_input")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dpiSnaps.forEach { (scale, label) ->
                                FilterChip(
                                    selected = abs(draftDpiScale - scale) < 0.015f,
                                    onClick = {
                                        draftDpiScale = scale
                                        dpiTextInput = String.format(Locale.US, "%.2f", scale)
                                    },
                                    label = { Text("$label (${String.format(Locale.US, "%.2f", scale)}x)") },
                                    modifier = Modifier.testTag("dpi_snap_${label}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                enabled = hasPendingDpiChange || abs(appearance.uiDensityScale - 1.00f) >= 0.005f,
                                onClick = {
                                    draftDpiScale = 1.00f
                                    dpiTextInput = "1.00"
                                    if (abs(appearance.uiDensityScale - 1.00f) >= 0.005f) {
                                        viewModel.saveAppearanceSettings(appearance.copy(uiDensityScale = 1.00f))
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dpi_reset_button")
                            ) {
                                Text("恢复标准")
                            }
                            Button(
                                enabled = hasPendingDpiChange,
                                onClick = {
                                    val parsed = dpiTextInput.toFloatOrNull()
                                    val finalScale = (parsed ?: draftDpiScale).coerceIn(0.80f, 1.35f)
                                    draftDpiScale = finalScale
                                    dpiTextInput = String.format(Locale.US, "%.2f", finalScale)
                                    viewModel.saveAppearanceSettings(appearance.copy(uiDensityScale = finalScale))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dpi_apply_button")
                            ) {
                                Text(
                                    if (hasPendingDpiChange) {
                                        "应用 (${(draftDpiScale * 100).roundToInt()}%)"
                                    } else {
                                        "应用 (${(appearance.uiDensityScale * 100).roundToInt()}%)"
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "icon_style_settings") {
                val pendingStyle = AppIconStyle.fromId(pendingIconStyleId)
                val hasPendingIconChange = !pendingIconStyleId.equals(appearance.appIconStyleId, ignoreCase = true)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("图标风格", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (hasPendingIconChange) {
                                "已预选「${pendingStyle.label}」，点选不会立即切换；点击下方「应用并重启」确认后再切换桌面入口图标"
                            } else {
                                "通过系统 activity-alias 切换桌面图标（含支持壁纸莫奈取色的「莫奈徽标」）；也可通过创建桌面快捷方式完全自定义图标与名称"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasPendingIconChange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppIconStyle.entries.forEach { style ->
                                val selected = pendingIconStyleId.equals(style.id, ignoreCase = true)
                                val isApplied = appearance.appIconStyleId.equals(style.id, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .width(96.dp)
                                        .clickable {
                                            pendingIconStyleId = style.id
                                            desktopSyncFeedback = null
                                        }
                                        .testTag("app_icon_style_${style.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AppBrandIconAvatar(
                                            styleId = style.id,
                                            size = 46.dp,
                                            cornerDp = 12.dp
                                        )
                                        Text(
                                            text = style.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        if (isApplied) {
                                            Text(
                                                text = "当前使用",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    shortcutCustomName = appearance.homeBrandCustomName.ifBlank { "小本本" }
                                    shortcutCustomStyleId = pendingIconStyleId
                                    val permState = DesktopIconSyncManager.checkPinShortcutPermission(context)
                                    if (permState == ShortcutPermissionState.GRANTED) {
                                        showShortcutCustomDialog = true
                                    } else {
                                        showShortcutPermissionDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("create_desktop_shortcut_button")
                            ) {
                                Icon(AppIcons.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("创建桌面快捷方式", maxLines = 1)
                            }
                            Button(
                                onClick = { showIconApplyConfirmDialog = true },
                                modifier = Modifier
                                    .weight(0.95f)
                                    .testTag("apply_icon_and_restart_button")
                            ) {
                                Text("应用并重启", maxLines = 1)
                            }
                        }

                        desktopSyncFeedback?.let { feedback ->
                            Text(
                                text = feedback,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        HorizontalDivider()

                        Text("账单分类徽标样式", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconVisualStyle.entries.forEach { iconStyle ->
                                FilterChip(
                                    selected = appearance.iconVisualStyle == iconStyle,
                                    onClick = { viewModel.saveAppearanceSettings(appearance.copy(iconVisualStyle = iconStyle)) },
                                    label = { Text(iconStyle.label) },
                                    modifier = Modifier.testTag("icon_visual_style_${iconStyle.name}")
                                )
                            }
                        }
                        Text(
                            text = appearance.iconVisualStyle.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "bottom_tab_bar_customization") {
                val normalizedOrder = remember(appearance.bottomTabOrder) {
                    MainBottomTab.normalizeOrder(appearance.bottomTabOrder)
                }

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
                            Text("底部导航栏自定义", style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = {
                                    viewModel.saveAppearanceSettings(
                                        appearance.copy(
                                            bottomTabOrder = MainBottomTab.defaultOrder,
                                            showBooksTab = true
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("bottom_tab_order_reset_button")
                            ) {
                                Text("恢复默认")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("底部显示「账本」标签页", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "隐藏后仍可在「设置 -> 账单工具 -> 账本管理与分析」中完整使用账本功能",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearance.showBooksTab,
                                onCheckedChange = { show ->
                                    viewModel.saveAppearanceSettings(appearance.copy(showBooksTab = show))
                                },
                                modifier = Modifier.testTag("toggle_show_books_tab")
                            )
                        }

                        HorizontalDivider()

                        Text("自定义底部 Tab 位置（从左到右顺序）", style = MaterialTheme.typography.labelLarge)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            normalizedOrder.forEachIndexed { index, tab ->
                                val isHidden = tab == MainBottomTab.BOOKS && !appearance.showBooksTab
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bottom_tab_order_row_${tab.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tab.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isHidden) {
                                                Text(
                                                    text = "当前已隐藏（可在账单工具内访问）",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(
                                            enabled = index > 0,
                                            onClick = {
                                                val mutable = normalizedOrder.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index - 1, item)
                                                viewModel.saveAppearanceSettings(appearance.copy(bottomTabOrder = mutable))
                                            },
                                            modifier = Modifier.testTag("tab_move_up_${tab.name}")
                                        ) {
                                            Icon(AppIcons.ArrowUpward, contentDescription = "前移", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            enabled = index < normalizedOrder.lastIndex,
                                            onClick = {
                                                val mutable = normalizedOrder.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index + 1, item)
                                                viewModel.saveAppearanceSettings(appearance.copy(bottomTabOrder = mutable))
                                            },
                                            modifier = Modifier.testTag("tab_move_down_${tab.name}")
                                        ) {
                                            Icon(AppIcons.ArrowDownward, contentDescription = "后移", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "home_brand_header_customization") {
                var nameInput by remember(appearance.homeBrandCustomName) {
                    mutableStateOf(appearance.homeBrandCustomName)
                }
                var subtitleInput by remember(appearance.homeBrandCustomSubtitle) {
                    mutableStateOf(appearance.homeBrandCustomSubtitle)
                }
                val previewTitle = nameInput.ifBlank { "小本本" }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("主页顶部品牌栏自定义", style = MaterialTheme.typography.titleMedium)

                        // Live left-aligned TopAppBar preview
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (appearance.homeBrandShowIcon) {
                                    AppBrandIconAvatar(
                                        styleId = appearance.appIconStyleId,
                                        size = appearance.homeBrandIconSizeDp.dp,
                                        cornerDp = appearance.homeBrandIconCornerDp.dp
                                    )
                                    if (appearance.homeBrandShowTitle) {
                                        Spacer(modifier = Modifier.width(appearance.homeBrandIconSpacingDp.dp))
                                    }
                                }
                                if (appearance.homeBrandShowTitle) {
                                    Column {
                                        Text(
                                            text = previewTitle,
                                            fontSize = appearance.homeBrandTitleSizeSp.sp,
                                            fontWeight = if (appearance.homeBrandTitleBold) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        if (subtitleInput.isNotBlank()) {
                                            Text(
                                                text = subtitleInput,
                                                fontSize = appearance.homeBrandSubtitleSizeSp.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                } else if (!appearance.homeBrandShowIcon) {
                                    Text(
                                        text = "已隐藏图标与标题",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("顶栏显示应用图标", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = appearance.homeBrandShowIcon,
                                onCheckedChange = {
                                    viewModel.saveAppearanceSettings(appearance.copy(homeBrandShowIcon = it))
                                },
                                modifier = Modifier.testTag("toggle_home_brand_icon")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("顶栏显示左对齐大号名称", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = appearance.homeBrandShowTitle,
                                onCheckedChange = {
                                    viewModel.saveAppearanceSettings(appearance.copy(homeBrandShowTitle = it))
                                },
                                modifier = Modifier.testTag("toggle_home_brand_title")
                            )
                        }

                        if (appearance.homeBrandShowTitle) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("标题加粗显示", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = appearance.homeBrandTitleBold,
                                    onCheckedChange = {
                                        viewModel.saveAppearanceSettings(appearance.copy(homeBrandTitleBold = it))
                                    }
                                )
                            }

                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { next ->
                                    nameInput = next.take(24)
                                    viewModel.saveAppearanceSettings(appearance.copy(homeBrandCustomName = nameInput))
                                },
                                label = { Text("主页显示名称（留空默认“小本本”）") },
                                placeholder = { Text("小本本") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_brand_name_input")
                            )

                            OutlinedTextField(
                                value = subtitleInput,
                                onValueChange = { next ->
                                    subtitleInput = next.take(32)
                                    viewModel.saveAppearanceSettings(appearance.copy(homeBrandCustomSubtitle = subtitleInput))
                                },
                                label = { Text("副标题（可选，例如个人手账格言）") },
                                placeholder = { Text("留空则只显示主名称") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_brand_subtitle_input")
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("名称字号", style = MaterialTheme.typography.labelLarge)
                                    Text("${appearance.homeBrandTitleSizeSp} sp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = appearance.homeBrandTitleSizeSp.toFloat().coerceIn(16f, 36f),
                                    onValueChange = { v ->
                                        viewModel.saveAppearanceSettings(
                                            appearance.copy(homeBrandTitleSizeSp = v.roundToInt().coerceIn(16, 36))
                                        )
                                    },
                                    valueRange = 16f..36f,
                                    modifier = Modifier.testTag("home_brand_title_size_slider")
                                )
                            }

                            if (subtitleInput.isNotBlank()) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("副标题字号", style = MaterialTheme.typography.labelLarge)
                                        Text("${appearance.homeBrandSubtitleSizeSp} sp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Slider(
                                        value = appearance.homeBrandSubtitleSizeSp.toFloat().coerceIn(9f, 16f),
                                        onValueChange = { v ->
                                            viewModel.saveAppearanceSettings(
                                                appearance.copy(homeBrandSubtitleSizeSp = v.roundToInt().coerceIn(9, 16))
                                            )
                                        },
                                        valueRange = 9f..16f
                                    )
                                }
                            }
                        }

                        if (appearance.homeBrandShowIcon) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("顶栏图标尺寸", style = MaterialTheme.typography.labelLarge)
                                    Text("${appearance.homeBrandIconSizeDp} dp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = appearance.homeBrandIconSizeDp.toFloat().coerceIn(20f, 52f),
                                    onValueChange = { v ->
                                        viewModel.saveAppearanceSettings(
                                            appearance.copy(homeBrandIconSizeDp = v.roundToInt().coerceIn(20, 52))
                                        )
                                    },
                                    valueRange = 20f..52f,
                                    modifier = Modifier.testTag("home_brand_icon_size_slider")
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("图标圆角弧度", style = MaterialTheme.typography.labelLarge)
                                    Text("${appearance.homeBrandIconCornerDp} dp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = appearance.homeBrandIconCornerDp.toFloat().coerceIn(0f, 26f),
                                    onValueChange = { v ->
                                        viewModel.saveAppearanceSettings(
                                            appearance.copy(homeBrandIconCornerDp = v.roundToInt().coerceIn(0, 26))
                                        )
                                    },
                                    valueRange = 0f..26f
                                )
                            }

                            if (appearance.homeBrandShowTitle) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("图标与名称间距", style = MaterialTheme.typography.labelLarge)
                                        Text("${appearance.homeBrandIconSpacingDp} dp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Slider(
                                        value = appearance.homeBrandIconSpacingDp.toFloat().coerceIn(4f, 24f),
                                        onValueChange = { v ->
                                            viewModel.saveAppearanceSettings(
                                                appearance.copy(homeBrandIconSpacingDp = v.roundToInt().coerceIn(4, 24))
                                            )
                                        },
                                        valueRange = 4f..24f
                                    )
                                }
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

                        Text("主页多功能 (+) 按钮位置", style = MaterialTheme.typography.labelLarge)
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

                        HorizontalDivider()

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val clampedPullDp = appearance.analyticsPullCalendarDistanceDp.coerceIn(80, 560)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("收支分析下拉打开全屏日历距离", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "$clampedPullDp dp",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "增大触发距离可明显减少分析页面滚动回弹时误触打开全屏日历（默认 260 dp，支持 80 ~ 560 dp 调节）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = clampedPullDp.toFloat(),
                                onValueChange = { raw ->
                                    viewModel.saveAppearanceSettings(
                                        appearance.copy(analyticsPullCalendarDistanceDp = raw.roundToInt().coerceIn(80, 560))
                                    )
                                },
                                valueRange = 80f..560f,
                                modifier = Modifier.testTag("analytics_pull_distance_slider")
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    160 to "160dp (灵敏)",
                                    260 to "260dp (默认)",
                                    380 to "380dp (防误触)",
                                    500 to "500dp (超长)"
                                ).forEach { (presetDp, label) ->
                                    FilterChip(
                                        selected = clampedPullDp == presetDp,
                                        onClick = {
                                            viewModel.saveAppearanceSettings(
                                                appearance.copy(analyticsPullCalendarDistanceDp = presetDp)
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("analytics_pull_preset_$presetDp")
                                    )
                                }
                            }
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

    if (showColorPickerDialog) {
        CustomColorPickerDialog(
            initialColorArgb = appearance.customPrimaryColorArgb,
            onDismiss = { showColorPickerDialog = false },
            onConfirm = { newColorArgb ->
                showColorPickerDialog = false
                viewModel.saveAppearanceSettings(
                    appearance.copy(
                        dynamicColor = false,
                        customPrimaryColorArgb = newColorArgb
                    )
                )
            }
        )
    }

    if (showIconApplyConfirmDialog) {
        val pendingStyle = AppIconStyle.fromId(pendingIconStyleId)
        AlertDialog(
            onDismissRequest = { showIconApplyConfirmDialog = false },
            title = { Text("确认切换图标风格并重启") },
            text = {
                Text(
                    "确认将桌面应用图标切换为「${pendingStyle.label}」吗？确认后将更新系统 activity-alias 入口并重启应用以刷新桌面图标。"
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { showIconApplyConfirmDialog = false },
                    modifier = Modifier.testTag("icon_apply_cancel_button")
                ) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showIconApplyConfirmDialog = false
                        scope.launch {
                            viewModel.saveAppearanceSettingsSuspend(
                                appearance.copy(appIconStyleId = pendingStyle.id)
                            )
                            DesktopIconSyncManager.applyLauncherIconAlias(context, pendingStyle)
                            delay(150L)
                            DesktopIconSyncManager.restartApp(context)
                        }
                    },
                    modifier = Modifier.testTag("icon_apply_confirm_restart_button")
                ) {
                    Text("确认并重启")
                }
            }
        )
    }

    if (showShortcutPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showShortcutPermissionDialog = false },
            title = { Text("需要桌面快捷方式权限") },
            text = {
                Text(
                    "当前系统未授予本应用「桌面快捷方式」权限或桌面限制了静默创建。请前往应用权限设置开启「桌面快捷方式」后，即可创建完全自定义名称、风格与配色的桌面图标。"
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showShortcutPermissionDialog = false }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            showShortcutPermissionDialog = false
                            showShortcutCustomDialog = true
                        },
                        modifier = Modifier.testTag("shortcut_perm_try_anyway_button")
                    ) {
                        Text("仍尝试创建")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShortcutPermissionDialog = false
                        val opened = DesktopIconSyncManager.openShortcutPermissionSettings(context)
                        desktopSyncFeedback = if (opened) {
                            "请在打开的权限页中允许「桌面快捷方式」，返回后再点击创建"
                        } else {
                            "未能自动跳转系统权限页，请手动在系统设置中授予「桌面快捷方式」权限"
                        }
                    },
                    modifier = Modifier.testTag("shortcut_perm_open_settings_button")
                ) {
                    Text("前往授权")
                }
            }
        )
    }

    if (showShortcutCustomDialog) {
        val livePrimary = MaterialTheme.colorScheme.primary
        val liveContainer = MaterialTheme.colorScheme.primaryContainer
        val liveOnPrimary = MaterialTheme.colorScheme.onPrimary
        val liveTertiary = MaterialTheme.colorScheme.tertiary

        val customSeedColor = Color(shortcutCustomColorArgb.toInt())
        val effectivePrimary = if (shortcutUseThemeColor) livePrimary else customSeedColor
        val effectiveContainer = if (shortcutUseThemeColor) liveContainer else customSeedColor.copy(alpha = 0.22f)
        val effectiveOnPrimary = if (shortcutUseThemeColor) liveOnPrimary else {
            if (customSeedColor.luminance() > 0.45f) Color(0xFF101413) else Color.White
        }
        val effectiveTertiary = if (shortcutUseThemeColor) liveTertiary else customSeedColor

        AlertDialog(
            onDismissRequest = { showShortcutCustomDialog = false },
            title = { Text("创建自定义桌面快捷方式") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "保留原有桌面图标的同时，额外添加一个可完全自定义名称、风格与配色的桌面快捷方式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = shortcutCustomName,
                        onValueChange = { shortcutCustomName = it.take(18) },
                        label = { Text("快捷方式桌面名称") },
                        placeholder = { Text("小本本") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shortcut_custom_name_input")
                    )

                    Text("选择快捷方式图标风格", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIconStyle.entries.forEach { st ->
                            FilterChip(
                                selected = shortcutCustomStyleId.equals(st.id, ignoreCase = true),
                                onClick = { shortcutCustomStyleId = st.id },
                                label = { Text(st.label) }
                            )
                        }
                    }

                    Text("快捷方式图标配色", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = shortcutUseThemeColor,
                            onClick = { shortcutUseThemeColor = true },
                            label = { Text("跟随当前主题色") }
                        )
                        FilterChip(
                            selected = !shortcutUseThemeColor,
                            onClick = { shortcutUseThemeColor = false },
                            label = { Text("自定义图标色") }
                        )
                    }

                    if (!shortcutUseThemeColor) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(customSeedColor)
                                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                                Text(
                                    text = "#%06X".format(shortcutCustomColorArgb.toInt() and 0xFFFFFF),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            OutlinedButton(onClick = { showShortcutColorPicker = true }) {
                                Text("选取颜色")
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortcutCustomDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShortcutCustomDialog = false
                        val (ok, msg) = DesktopIconSyncManager.pinCustomDesktopShortcut(
                            context = context,
                            customName = shortcutCustomName,
                            styleId = shortcutCustomStyleId,
                            primaryColor = effectivePrimary,
                            primaryContainerColor = effectiveContainer,
                            onPrimaryColor = effectiveOnPrimary,
                            tertiaryColor = effectiveTertiary
                        )
                        desktopSyncFeedback = msg
                        if (!ok) {
                            showShortcutPermissionDialog = true
                        }
                    },
                    modifier = Modifier.testTag("shortcut_custom_confirm_button")
                ) {
                    Text("创建到桌面")
                }
            }
        )
    }

    if (showShortcutColorPicker) {
        CustomColorPickerDialog(
            initialColorArgb = shortcutCustomColorArgb,
            onDismiss = { showShortcutColorPicker = false },
            onConfirm = { argb ->
                shortcutCustomColorArgb = argb
                shortcutUseThemeColor = false
                showShortcutColorPicker = false
            }
        )
    }
}
