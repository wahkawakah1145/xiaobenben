package com.example.ui

import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import com.example.model.ExchangeRateSource
import com.example.model.AiProviderMode
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Search
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.example.model.DeleteUndoDuration
import com.example.model.GlobalToastDuration
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import com.example.model.FeatureTipsCatalog
import com.example.ui.components.FeatureTipBanner
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_DATA_HUB, "webdav_form")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_DATA_HUB,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Text(
                            text = settingsHighlightedText("WebDAV 服务器配置", ROUTE_SETTINGS_DATA_HUB),
                            style = MaterialTheme.typography.titleMedium
                        )

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
    var currencySearchQuery by remember { mutableStateOf("") }
    var showAddCurrencyPanel by remember { mutableStateOf(false) }
    var customRateUrlInput by remember(settings.customExchangeRateUrl) {
        mutableStateOf(settings.customExchangeRateUrl)
    }
    var customUrlSavedHint by remember { mutableStateOf<String?>(null) }

    val selectedSource = ExchangeRateSource.fromRaw(settings.exchangeRateSource)
    val enabledForeignCodes = remember(settings.enabledCurrencyCodes, settings.baseCurrencyCode) {
        CurrencyCatalog.normalizeEnabledForeignCodes(
            settings.enabledCurrencyCodes,
            ensureCode = settings.baseCurrencyCode
        )
    }
    val enabledForeignCurrencies = remember(enabledForeignCodes) {
        enabledForeignCodes.map { CurrencyCatalog.resolve(it) }
    }
    val baseCurrencyChoices = remember(enabledForeignCodes, settings.baseCurrencyCode) {
        CurrencyCatalog.enabledCurrenciesWithCny(enabledForeignCodes, ensureCode = settings.baseCurrencyCode)
    }
    val sourceCurrencyPool = remember(selectedSource, exchangeRates.sourceId, exchangeRates.sourceAvailableCodes) {
        CurrencyCatalog.availableCurrenciesForSource(selectedSource, exchangeRates)
    }
    var expandAllAddableCurrencies by remember(selectedSource) { mutableStateOf(false) }
    val allAddableCurrencies = remember(sourceCurrencyPool, enabledForeignCodes) {
        val enabledSet = enabledForeignCodes.toSet()
        sourceCurrencyPool.filter { it.code != "CNY" && it.code !in enabledSet }
    }
    val availableToAddCurrencies = remember(allAddableCurrencies, currencySearchQuery) {
        CurrencyCatalog.search(currencySearchQuery, pool = allAddableCurrencies)
    }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_CURRENCY, "base_currency")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_CURRENCY,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Text(
                            text = settingsHighlightedText("统计主币种", ROUTE_SETTINGS_CURRENCY),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = settingsHighlightedText(
                                "不同币种的账单将按汇率数据库折算为主币种计入统计（默认展示人民币及已启用的 ${enabledForeignCurrencies.size} 种常用外币）。",
                                ROUTE_SETTINGS_CURRENCY
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            baseCurrencyChoices.forEach { info ->
                                FilterChip(
                                    selected = settings.baseCurrencyCode.equals(info.code, ignoreCase = true),
                                    onClick = { viewModel.saveSettings(settings.copy(baseCurrencyCode = info.code)) },
                                    label = { Text("${info.code} ${info.chineseName}") },
                                    modifier = Modifier.testTag("base_currency_chip_${info.code}")
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
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("汇率数据库与数据源", style = MaterialTheme.typography.titleMedium)
                            val timeHint = if (exchangeRates.updatedAtMillis > 0L) {
                                "上次更新：${formatDateTimeFull(exchangeRates.updatedAtMillis)}"
                            } else {
                                "使用内置参考汇率 · 仅点击更新或自动到期时联网获取"
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
                                        text = "当前牌价来源：${exchangeRates.dataSource}",
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

                        Text("选择汇率数据源", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExchangeRateSource.entries.forEach { src ->
                                FilterChip(
                                    selected = selectedSource == src,
                                    onClick = {
                                        viewModel.saveSettings(settings.copy(exchangeRateSource = src.name))
                                        customUrlSavedHint = null
                                    },
                                    label = { Text(src.label) },
                                    modifier = Modifier.testTag("rate_source_${src.name}")
                                )
                            }
                        }
                        Text(
                            text = selectedSource.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (selectedSource == ExchangeRateSource.CUSTOM) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customRateUrlInput,
                                    onValueChange = {
                                        customRateUrlInput = it
                                        customUrlSavedHint = null
                                    },
                                    label = { Text("自定义 JSON 汇率链接") },
                                    placeholder = { Text("https://open.er-api.com/v6/latest/CNY") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("custom_rate_url_input")
                                )
                                OutlinedButton(
                                    onClick = {
                                        val trimmed = customRateUrlInput.trim()
                                        if (!trimmed.startsWith("http://", ignoreCase = true) &&
                                            !trimmed.startsWith("https://", ignoreCase = true)
                                        ) {
                                            customUrlSavedHint = "链接必须以 http:// 或 https:// 开头"
                                        } else {
                                            viewModel.saveSettings(
                                                settings.copy(
                                                    exchangeRateSource = ExchangeRateSource.CUSTOM.name,
                                                    customExchangeRateUrl = trimmed
                                                )
                                            )
                                            customUrlSavedHint = "已保存自定义汇率链接"
                                        }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("save_custom_rate_url_button")
                                ) {
                                    Text("保存")
                                }
                            }
                            Text(
                                text = "兼容 rates / conversion_rates / cny / data 等标准 JSON 汇率格式（自动支持非 CNY 基准交叉换算）。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            customUrlSavedHint?.let { hint ->
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hint.contains("必须")) ExpenseCoral else IncomeJade
                                )
                            }
                        }

                        Button(
                            enabled = rateUpdateState !is RateUpdateState.Loading,
                            onClick = {
                                if (selectedSource == ExchangeRateSource.CUSTOM) {
                                    val trimmed = customRateUrlInput.trim()
                                    if (trimmed.isNotBlank() && trimmed != settings.customExchangeRateUrl) {
                                        viewModel.saveSettings(settings.copy(customExchangeRateUrl = trimmed))
                                    }
                                }
                                viewModel.updateExchangeRateDatabase()
                            },
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
                                Text("从「${selectedSource.label}」更新已启用汇率")
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已启用外币汇率（${enabledForeignCurrencies.size} 项）",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "默认启用最常用的 5 种外币，其余可按需添加或随时移除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (enabledForeignCodes != CurrencyCatalog.defaultEnabledForeignCodes) {
                                TextButton(
                                    onClick = {
                                        viewModel.saveSettings(
                                            settings.copy(
                                                enabledCurrencyCodes = CurrencyCatalog.normalizeEnabledForeignCodes(
                                                    CurrencyCatalog.defaultEnabledForeignCodes,
                                                    ensureCode = settings.baseCurrencyCode
                                                )
                                            )
                                        )
                                    },
                                    modifier = Modifier.testTag("reset_default_currencies_button")
                                ) {
                                    Text("恢复默认 5 项")
                                }
                            }
                        }

                        enabledForeignCurrencies.forEach { info ->
                            val rate = exchangeRates.ratesToCny[info.code] ?: info.defaultRateToCny
                            val isCurrentBase = settings.baseCurrencyCode.equals(info.code, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1 ${info.code} (${info.chineseName})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "≈ ¥%.4f".format(rate),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = numberFont),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!isCurrentBase && enabledForeignCodes.size > 1) {
                                    IconButton(
                                        onClick = {
                                            viewModel.removeEnabledCurrency(info.code)
                                        },
                                        modifier = Modifier.testTag("remove_currency_${info.code}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "移除 ${info.code}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        OutlinedButton(
                            onClick = { showAddCurrencyPanel = !showAddCurrencyPanel },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_add_currency_panel")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (showAddCurrencyPanel) "收起更多外币列表"
                                else "按需添加其他外币（${selectedSource.label} 还有 ${allAddableCurrencies.size} 种可选）"
                            )
                        }

                        if (showAddCurrencyPanel) {
                            val customSynced = selectedSource == ExchangeRateSource.CUSTOM &&
                                exchangeRates.sourceId.equals(ExchangeRateSource.CUSTOM.name, ignoreCase = true) &&
                                exchangeRates.sourceAvailableCodes.isNotEmpty()
                            if (selectedSource == ExchangeRateSource.CUSTOM) {
                                Text(
                                    text = if (customSynced) {
                                        "已按自定义数据源返回值自动载入 ${sourceCurrencyPool.size} 种币种"
                                    } else {
                                        "更新自定义数据源后，将按返回值自动载入全部可选币种"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedTextField(
                                value = currencySearchQuery,
                                onValueChange = { currencySearchQuery = it },
                                placeholder = { Text("搜索待添加外币（代码/中文名）") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("currency_search_input")
                            )
                            if (availableToAddCurrencies.isEmpty()) {
                                Text(
                                    text = if (currencySearchQuery.isBlank()) "当前数据源的全部币种均已启用" else "未找到未启用的匹配币种「$currencySearchQuery」",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val displayedAddable = remember(availableToAddCurrencies, currencySearchQuery, expandAllAddableCurrencies) {
                                    if (currencySearchQuery.isNotBlank() || expandAllAddableCurrencies || availableToAddCurrencies.size <= 48) {
                                        availableToAddCurrencies
                                    } else {
                                        availableToAddCurrencies.take(48)
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    displayedAddable.forEach { info ->
                                        val labelText = if (info.chineseName.equals(info.code, ignoreCase = true)) {
                                            info.code
                                        } else {
                                            "${info.code} ${info.chineseName}"
                                        }
                                        AssistChip(
                                            onClick = {
                                                viewModel.saveSettings(
                                                    settings.copy(enabledCurrencyCodes = enabledForeignCodes + info.code)
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(labelText) },
                                            modifier = Modifier.testTag("add_currency_chip_${info.code}")
                                        )
                                    }
                                }
                                if (displayedAddable.size < availableToAddCurrencies.size) {
                                    TextButton(
                                        onClick = { expandAllAddableCurrencies = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("expand_all_addable_currencies_button")
                                    ) {
                                        Text("展开全部 ${availableToAddCurrencies.size} 种可选币种")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ManualImportScreen(
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val importState by viewModel.importUiState.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfigState.collectAsStateWithLifecycle()
    var pastedText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var useRuleMatching by rememberSaveable { mutableStateOf(true) }
    var useAiUnderstanding by rememberSaveable { mutableStateOf(false) }
    val readyPreview = (importState as? ImportUiState.Ready)?.preview
    var showAllRecognizedItems by remember(readyPreview) { mutableStateOf(true) }
    var selectedImportIds by remember(readyPreview) {
        mutableStateOf(readyPreview?.defaultSelectedIds() ?: emptySet())
    }
    var inspectingImportTx by remember(readyPreview) {
        mutableStateOf<com.example.model.LedgerTransaction?>(null)
    }

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
            viewModel.previewManualImportBytes(
                bytes = bytes,
                fileNameHint = fileName,
                context = context,
                useRuleMatching = useRuleMatching,
                useAiUnderstanding = useAiUnderstanding
            )
        }.onFailure {
            onShowMessage("读取文件失败：${it.localizedMessage ?: "无法打开文件"}")
        }
    }

    val importAppearance by viewModel.appearanceState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = listState,
        route = ROUTE_SETTINGS_MANUAL_IMPORT,
        sectionKeysInOrder = listOf("import_arrival_banner", "import_options_card", "import_file_card")
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "import_arrival_banner") {
                SettingsSearchArrivalBanner(
                    route = ROUTE_SETTINGS_MANUAL_IMPORT,
                    onDismiss = { viewModel.clearSettingsHighlight() }
                )
            }
            item(key = "import_options_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = settingsHighlightedText("导入解析增强选项", ROUTE_SETTINGS_MANUAL_IMPORT),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("启用规则匹配", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "套用自定义关键词、忽略规则与商户/分类/渠道覆写规则",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useRuleMatching,
                                onCheckedChange = { useRuleMatching = it },
                                modifier = Modifier.testTag("import_toggle_rule_matching")
                            )
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("使用 AI 理解", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                val aiHint = when (aiConfig.providerMode) {
                                    AiProviderMode.DISABLED -> "当前 AI理解未启用，开启后请先在「设置 -> AI理解」配置本地模型或在线 API"
                                    AiProviderMode.LOCAL_SMALL_MODEL -> "将使用已配置的本地开源小模型辅助识别分类与商户"
                                    AiProviderMode.ONLINE_API -> "将使用在线 API (${aiConfig.apiModelName.ifBlank { "已配置接口" }}) 增强理解复杂文本与商品描述"
                                }
                                Text(
                                    text = aiHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useAiUnderstanding,
                                onCheckedChange = { checked ->
                                    useAiUnderstanding = checked
                                    if (checked && aiConfig.providerMode == AiProviderMode.DISABLED) {
                                        onShowMessage("提示：AI理解当前未开启，请先前往「设置 -> AI理解」启用")
                                    }
                                },
                                modifier = Modifier.testTag("import_toggle_ai_understanding")
                            )
                        }
                    }
                }
            }

            item(key = "import_file_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_MANUAL_IMPORT, "import_file_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = settingsHighlightedText("从文档导入", ROUTE_SETTINGS_MANUAL_IMPORT),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = settingsHighlightedText(
                                "支持微信、支付宝及农业银行等银行流水 XLSX / CSV / JSON 文件。",
                                ROUTE_SETTINGS_MANUAL_IMPORT
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FeatureTipBanner(
                            tipId = FeatureTipsCatalog.MANUAL_IMPORT_FORMAT,
                            text = "数字日期时间会自动转为时间戳；解析后自动标出重复项与本人互转，默认仅勾选新增账单。",
                            appearance = importAppearance,
                            onDismiss = { viewModel.dismissFeatureTip(it) }
                        )
                        Button(
                            enabled = importState !is ImportUiState.Parsing,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("从剪贴板或文本导入", style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                    if (clipText.isBlank()) {
                                        onShowMessage("剪贴板中没有可读取的文本内容")
                                    } else {
                                        pastedText = clipText
                                        onShowMessage("已读取剪贴板内容（${clipText.length} 字符）")
                                    }
                                },
                                modifier = Modifier.testTag("read_clipboard_import_button")
                            ) {
                                Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("读取剪贴板")
                            }
                        }
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            label = { Text("粘贴多行账单、银行短信、CSV 或 JSON") },
                            placeholder = { Text("例如：\n星巴克 42.00\nHKD 68.50 大家乐\n交易日期,交易时间,交易摘要,交易金额,本次余额,对手信息,日志号,交易渠道,交易附言") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_paste_input")
                        )
                        OutlinedButton(
                            enabled = importState !is ImportUiState.Parsing,
                            onClick = {
                                viewModel.previewManualImport(
                                    rawContent = pastedText,
                                    fileNameHint = null,
                                    context = context,
                                    useRuleMatching = useRuleMatching,
                                    useAiUnderstanding = useAiUnderstanding
                                )
                            },
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
                is ImportUiState.Parsing -> {
                    item(key = "import_parsing") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
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
                    val recommendedItems = preview.recommendedTransactions
                    val duplicateCount = preview.duplicateCount
                    val selfTransferCount = preview.selfTransferExcludedCount
                    val displayedItems = if (showAllRecognizedItems) preview.transactions else recommendedItems
                    val displayedIds = displayedItems.mapTo(LinkedHashSet()) { it.id }
                    val allDisplayedSelected = displayedIds.isNotEmpty() && displayedIds.all { it in selectedImportIds }
                    val selectedItemsToCommit = preview.transactions.filter { it.id in selectedImportIds }

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
                                    text = "${preview.formatLabel} · 共识别 ${preview.transactions.size} 笔" +
                                        if (preview.skippedCount > 0) "（忽略无效行 ${preview.skippedCount}）" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "点击切换勾选 · 长按查看详情",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = IncomeJade.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "已勾选 ${selectedItemsToCommit.size} 笔",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IncomeJade,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(99.dp),
                                        color = if (duplicateCount > 0) {
                                            ExpenseCoral.copy(alpha = 0.14f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        }
                                    ) {
                                        Text(
                                            text = if (duplicateCount > 0) "重复 $duplicateCount 笔" else "无重复",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (duplicateCount > 0) ExpenseCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    if (selfTransferCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(99.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
                                        ) {
                                            Text(
                                                text = "本人互转/不计收支 $selfTransferCount 笔",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                                if (preview.detectedColumnsSummary.isNotBlank()) {
                                    Text(
                                        text = "映射列：${preview.detectedColumnsSummary}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = showAllRecognizedItems,
                                        onClick = { showAllRecognizedItems = true },
                                        label = { Text("全部识别 (${preview.transactions.size})") },
                                        modifier = Modifier.testTag("import_filter_show_all")
                                    )
                                    FilterChip(
                                        selected = !showAllRecognizedItems,
                                        onClick = { showAllRecognizedItems = false },
                                        label = { Text("仅看推荐 (${recommendedItems.size})") },
                                        modifier = Modifier.testTag("import_filter_new_only")
                                    )
                                    AssistChip(
                                        onClick = { selectedImportIds = preview.defaultSelectedIds() },
                                        label = { Text("恢复默认勾选") },
                                        modifier = Modifier.testTag("import_default_selection_button")
                                    )
                                    AssistChip(
                                        onClick = {
                                            selectedImportIds = if (allDisplayedSelected) {
                                                selectedImportIds - displayedIds
                                            } else {
                                                selectedImportIds + displayedIds
                                            }
                                        },
                                        label = { Text(if (allDisplayedSelected) "取消全选" else "全选当前") },
                                        modifier = Modifier.testTag("import_select_all_button")
                                    )
                                    AssistChip(
                                        onClick = {
                                            selectedImportIds = (selectedImportIds - displayedIds) + (displayedIds - selectedImportIds)
                                        },
                                        label = { Text("反选") },
                                        modifier = Modifier.testTag("import_invert_selection_button")
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.clearImportPreview() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Text("取消")
                                    }
                                    Button(
                                        enabled = selectedItemsToCommit.isNotEmpty(),
                                        onClick = {
                                            viewModel.commitManualImport(
                                                items = selectedItemsToCommit,
                                                skippedDuplicateCount = duplicateCount,
                                                unselectedCount = (preview.transactions.size - selectedItemsToCommit.size).coerceAtLeast(0)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1.45f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("commit_import_button")
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (selectedItemsToCommit.isNotEmpty()) {
                                                "导入已选 (${selectedItemsToCommit.size})"
                                            } else {
                                                "请先勾选条目"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (displayedItems.isEmpty() && !showAllRecognizedItems) {
                        item(key = "import_all_duplicate_notice") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_all_duplicate_notice")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "识别到的 ${preview.transactions.size} 笔账单均为重复或本人互转排除项，默认未勾选。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "如需核对或手动勾选导入，请切换至「全部识别 (${preview.transactions.size})」。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    items(displayedItems, key = { it.id }) { tx ->
                        val isDuplicate = tx.id in preview.duplicateIds
                        val exclusionTag = preview.defaultExclusionTag(tx)
                        val exclusionDetail = preview.defaultExclusionDetail(tx)
                        val isSelected = tx.id in selectedImportIds
                        val isExcludedDefault = exclusionTag != null
                        Surface(
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                isExcludedDefault -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f)
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        selectedImportIds = if (isSelected) {
                                            selectedImportIds - tx.id
                                        } else {
                                            selectedImportIds + tx.id
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        inspectingImportTx = tx
                                    }
                                )
                                .testTag(if (isDuplicate) "import_preview_dup_${tx.id}" else "import_preview_new_${tx.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedImportIds = if (isSelected) {
                                            selectedImportIds - tx.id
                                        } else {
                                            selectedImportIds + tx.id
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = tx.merchant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isExcludedDefault && !isSelected) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (exclusionTag != null) {
                                            val tagColor = if (isDuplicate) ExpenseCoral else MaterialTheme.colorScheme.tertiary
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = tagColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = "$exclusionTag · 默认不选",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tagColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (!exclusionDetail.isNullOrBlank()) {
                                        Text(
                                            text = exclusionDetail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isDuplicate) ExpenseCoral.copy(alpha = 0.85f) else MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    val extraMeta = listOfNotNull(
                                        tx.productName?.takeIf { it != tx.merchant },
                                        tx.tradeType?.takeIf { it != tx.merchant },
                                        tx.tradeStatus,
                                        tx.balanceAfterCents?.let { "本次余额 ${formatCurrency(it, tx.currencyCode)}" },
                                        tx.transactionNo?.let { "单号/日志号 $it" }
                                    ).joinToString(" · ")
                                    if (extraMeta.isNotBlank()) {
                                        Text(
                                            text = extraMeta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "${tx.category} · ${tx.channel} · ${tx.currencyCode} · ${formatDateTimeFull(tx.occurredAtMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = LocalNumberFontFamily.current),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExcludedDefault && !isSelected) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingImportTx?.let { tx ->
        val preview = readyPreview
        val exclusionTag = preview?.defaultExclusionTag(tx)
        val exclusionDetail = preview?.defaultExclusionDetail(tx)
        val isChecked = tx.id in selectedImportIds
        AlertDialog(
            onDismissRequest = { inspectingImportTx = null },
            title = { Text("导入条目详情") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = LocalNumberFontFamily.current),
                        fontWeight = FontWeight.Bold
                    )
                    if (exclusionTag != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ExpenseCoral.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "排除标记：$exclusionTag${exclusionDetail?.let { "（$it）" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ExpenseCoral,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Text("交易对方：${tx.merchant}", style = MaterialTheme.typography.bodySmall)
                    Text("收支与分类：${tx.txType.label} · ${tx.category}", style = MaterialTheme.typography.bodySmall)
                    Text("支付渠道：${tx.channel}", style = MaterialTheme.typography.bodySmall)
                    Text("发生时间：${formatDateTimeFull(tx.occurredAtMillis)}", style = MaterialTheme.typography.bodySmall)
                    tx.productName?.takeIf { it.isNotBlank() }?.let {
                        Text("商品/说明：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    tx.tradeType?.takeIf { it.isNotBlank() }?.let {
                        Text("交易类型：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    tx.tradeStatus?.takeIf { it.isNotBlank() }?.let {
                        Text("当前状态：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    tx.balanceAfterCents?.let {
                        Text("本次余额：${formatCurrency(it, tx.currencyCode)}", style = MaterialTheme.typography.bodySmall)
                    }
                    tx.transactionNo?.takeIf { it.isNotBlank() }?.let {
                        Text("交易单号/日志号：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    tx.merchantOrderNo?.takeIf { it.isNotBlank() }?.let {
                        Text("商户单号：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    tx.note?.takeIf { it.isNotBlank() }?.let {
                        Text("备注/附言：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    tx.rawNotification?.takeIf { it.isNotBlank() }?.let { raw ->
                        HorizontalDivider()
                        Text("原始记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer {
                            Text(raw, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingImportTx = null }) { Text("关闭") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedImportIds = if (isChecked) {
                            selectedImportIds - tx.id
                        } else {
                            selectedImportIds + tx.id
                        }
                        inspectingImportTx = null
                    },
                    modifier = Modifier.testTag("import_detail_toggle_select")
                ) {
                    Text(if (isChecked) "取消勾选此项" else "勾选导入此项")
                }
            }
        )
    }
}

@Composable
fun AppearanceSettingsScreen(
    appearance: AppearanceSettings,
    onOpenTheme: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenHomeNav: () -> Unit,
    onOpenInteraction: () -> Unit,
    onOpenIconShortcut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconStyleLabel = AppIconStyle.fromId(appearance.appIconStyleId).label
    val themeSummary = buildString {
        append(if (appearance.dynamicColor) "莫奈动态取色" else "自定义主色")
        append(" · ")
        append(appearance.themeMode.label)
        if (appearance.oledPureBlack && appearance.themeMode != ThemeMode.LIGHT) {
            append(" · OLED纯黑")
        }
    }
    val displaySummary = buildString {
        append("${appearance.latinNumberFont.label} · DPI ${(appearance.uiDensityScale * 100).roundToInt()}%")
        append(" · ${appearance.cardDensity.label}卡片 · ${appearance.iconVisualStyle.label}")
    }
    val homeNavSummary = buildString {
        append(if (appearance.showBooksTab) "底部 4 Tab" else "已隐藏账本 Tab")
        append(" · ${appearance.homeHeaderOrder.label} · ${appearance.fabPlacement.label} (+) 按钮")
    }
    val interactionSummary = buildString {
        append(if (appearance.hapticEnabled) {
            if (appearance.hapticFollowSystem) "触感：跟随系统" else "触感：自定义 ${(appearance.hapticIntensity * 100).roundToInt()}%"
        } else {
            "触感：已关闭"
        })
        append(" · 全局提示：${appearance.globalSnackbarDuration.label}")
    }
    val iconSummary = "当前图标：$iconStyleLabel · 支持相册自定义桌面快捷方式"

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "appearance_menu_visual") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        AppearanceMenuRow(
                            icon = AppIcons.Palette,
                            title = "主题与配色",
                            subtitle = themeSummary,
                            testTag = "appearance_menu_theme",
                            onClick = onOpenTheme
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        AppearanceMenuRow(
                            icon = AppIcons.TextFields,
                            title = "字体、DPI 与列表样式",
                            subtitle = displaySummary,
                            testTag = "appearance_menu_display",
                            onClick = onOpenDisplay
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        AppearanceMenuRow(
                            icon = AppIcons.ViewQuilt,
                            title = "主页、顶栏与导航",
                            subtitle = homeNavSummary,
                            testTag = "appearance_menu_home_nav",
                            onClick = onOpenHomeNav
                        )
                    }
                }
            }

            item(key = "appearance_menu_behavior_and_icon") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        AppearanceMenuRow(
                            icon = AppIcons.TouchApp,
                            title = "触感反馈、动画与手势",
                            subtitle = interactionSummary,
                            testTag = "appearance_menu_interaction",
                            onClick = onOpenInteraction
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        AppearanceMenuRow(
                            icon = AppIcons.AddToHomeScreen,
                            title = "图标风格与桌面快捷方式",
                            subtitle = iconSummary,
                            testTag = "appearance_menu_icon_shortcut",
                            onClick = onOpenIconShortcut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceThemeSettingsScreen(
    appearance: AppearanceSettings,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var showColorPickerDialog by remember { mutableStateOf(false) }

    val themeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = themeListState,
        route = ROUTE_SETTINGS_APPEARANCE_THEME,
        sectionKeysInOrder = listOf("color_and_palette", "theme_mode_and_oled")
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = themeListState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "color_and_palette") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_THEME, "color_and_palette")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_APPEARANCE_THEME,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Text(
                            text = settingsHighlightedText("色彩与调色板", ROUTE_SETTINGS_APPEARANCE_THEME),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settingsHighlightedText("莫奈动态取色 (Material You)", ROUTE_SETTINGS_APPEARANCE_THEME),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = settingsHighlightedText(
                                        if (supportsMonet) "跟随系统壁纸自动取色；开启后同样支持下方选择调色板衍生风格" else "当前系统低于 Android 12，自动使用自定义调色板",
                                        ROUTE_SETTINGS_APPEARANCE_THEME
                                    ),
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
                    }
                }
            }

            item(key = "theme_mode_and_oled") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_THEME, "theme_mode_and_oled")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = settingsHighlightedText("深浅色模式与屏幕优化", ROUTE_SETTINGS_APPEARANCE_THEME),
                            style = MaterialTheme.typography.titleMedium
                        )

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
                                Text(
                                    text = settingsHighlightedText("OLED 纯黑背景", ROUTE_SETTINGS_APPEARANCE_THEME),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceDisplaySettingsScreen(
    appearance: AppearanceSettings,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val displayListState = androidx.compose.foundation.lazy.rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = displayListState,
        route = ROUTE_SETTINGS_APPEARANCE_DISPLAY,
        sectionKeysInOrder = listOf("font_selection", "page_dpi_scaling", "card_and_badge_styles")
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = displayListState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "font_selection") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_DISPLAY, "font_selection")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_APPEARANCE_DISPLAY,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Text(
                            text = settingsHighlightedText("英文与数字字体", ROUTE_SETTINGS_APPEARANCE_DISPLAY),
                            style = MaterialTheme.typography.titleMedium
                        )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_DISPLAY, "page_dpi_scaling")
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
                                Text(
                                    text = settingsHighlightedText("页面缩放与显示密度 (DPI)", ROUTE_SETTINGS_APPEARANCE_DISPLAY),
                                    style = MaterialTheme.typography.titleMedium
                                )
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

            item(key = "card_and_badge_styles") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("账单列表与徽标样式", style = MaterialTheme.typography.titleMedium)

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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceHomeNavSettingsScreen(
    appearance: AppearanceSettings,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val homeNavListState = androidx.compose.foundation.lazy.rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = homeNavListState,
        route = ROUTE_SETTINGS_APPEARANCE_HOME_NAV,
        sectionKeysInOrder = listOf(
            "bottom_tab_bar_customization",
            "home_batch_load_settings",
            "feature_tips_management",
            "home_element_positions",
            "home_brand_header_customization",
            "home_tips_customization"
        )
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = homeNavListState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "bottom_tab_bar_customization") {
                val normalizedOrder = remember(appearance.bottomTabOrder) {
                    MainBottomTab.normalizeOrder(appearance.bottomTabOrder)
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_HOME_NAV, "bottom_tab_bar_customization")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_APPEARANCE_HOME_NAV,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = settingsHighlightedText("底部导航栏自定义", ROUTE_SETTINGS_APPEARANCE_HOME_NAV),
                                style = MaterialTheme.typography.titleMedium
                            )
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

            item(key = "home_batch_load_settings") {
                val batchSize = appearance.homePageLoadBatchSize.coerceIn(20, 500)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_HOME_NAV, "home_batch_load_settings")
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
                            Text(
                                text = settingsHighlightedText("首页账单单次加载条数", ROUTE_SETTINGS_APPEARANCE_HOME_NAV),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "$batchSize 条/批",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "减少初次加载量以提升进出主页流畅度；滚到底部后上划或点击即可继续加载。全选与反选仍覆盖全部匹配账单。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = batchSize.toFloat(),
                            onValueChange = { raw ->
                                val snapped = ((raw / 10f).roundToInt() * 10).coerceIn(20, 500)
                                viewModel.setHomePageLoadBatchSize(snapped)
                            },
                            valueRange = 20f..500f,
                            modifier = Modifier.testTag("home_batch_size_slider")
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(30, 50, 100, 200, 500).forEach { preset ->
                                FilterChip(
                                    selected = batchSize == preset,
                                    onClick = { viewModel.setHomePageLoadBatchSize(preset) },
                                    label = { Text(if (preset == 100) "100 条 (默认)" else "$preset 条") },
                                    modifier = Modifier.testTag("home_batch_preset_$preset")
                                )
                            }
                        }
                    }
                }
            }

            item(key = "feature_tips_management") {
                val hiddenCount = if (appearance.hideAllFeatureTips) {
                    FeatureTipsCatalog.allTips.size
                } else {
                    FeatureTipsCatalog.allTips.count { it.id in appearance.dismissedTipIds }
                }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_HOME_NAV, "feature_tips_management")
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
                                Text(
                                    text = settingsHighlightedText("功能提示管理", ROUTE_SETTINGS_APPEARANCE_HOME_NAV),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (hiddenCount == 0) "当前全部提示均处于开启状态" else "已关闭 $hiddenCount / ${FeatureTipsCatalog.allTips.size} 条提示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { viewModel.reopenAllFeatureTips() },
                                modifier = Modifier.testTag("reopen_all_feature_tips_button")
                            ) {
                                Text("全部重新打开")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settingsHighlightedText("一键关闭所有功能提示", ROUTE_SETTINGS_APPEARANCE_HOME_NAV),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "隐藏各页面功能旁的引导提示条，可随时在此重新开启",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearance.hideAllFeatureTips,
                                onCheckedChange = { viewModel.setHideAllFeatureTips(it) },
                                modifier = Modifier.testTag("toggle_hide_all_feature_tips")
                            )
                        }

                        HorizontalDivider()

                        FeatureTipsCatalog.allTips.forEach { spec ->
                            val isVisible = appearance.isFeatureTipVisible(spec.id)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = spec.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = spec.shortText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isVisible,
                                    onCheckedChange = { show ->
                                        viewModel.setFeatureTipDismissed(spec.id, dismissed = !show)
                                    },
                                    modifier = Modifier.testTag("toggle_feature_tip_${spec.id}")
                                )
                            }
                        }
                    }
                }
            }

            item(key = "home_element_positions") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("主页元素位置", style = MaterialTheme.typography.titleMedium)

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_HOME_NAV, "home_brand_header_customization")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = settingsHighlightedText("主页顶部品牌栏自定义", ROUTE_SETTINGS_APPEARANCE_HOME_NAV),
                            style = MaterialTheme.typography.titleMedium
                        )

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceInteractionSettingsScreen(
    appearance: AppearanceSettings,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val haptics = com.example.ui.theme.LocalAppHaptics.current
    SettingsAutoScrollEffect(
        listState = interactionListState,
        route = ROUTE_SETTINGS_APPEARANCE_INTERACTION,
        sectionKeysInOrder = listOf("haptic_feedback_card", "bottom_banner_durations", "navigation_and_gestures")
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = interactionListState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "haptic_feedback_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_INTERACTION, "haptic_feedback_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_APPEARANCE_INTERACTION,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = settingsHighlightedText("触感反馈", ROUTE_SETTINGS_APPEARANCE_INTERACTION),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "为点击、开关、长按、拖动排序与手势提供细腻振动反馈",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearance.hapticEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.saveAppearanceSettings(appearance.copy(hapticEnabled = enabled))
                                    if (enabled) haptics?.perform(com.example.ui.theme.AppHapticAction.TOGGLE, forcePreview = true)
                                },
                                modifier = Modifier.testTag("toggle_haptic_enabled")
                            )
                        }

                        if (appearance.hapticEnabled) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = settingsHighlightedText("跟随系统触感", ROUTE_SETTINGS_APPEARANCE_INTERACTION),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "开启时调用系统原生触感波形，关闭后可自定义振动强度",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = appearance.hapticFollowSystem,
                                    onCheckedChange = { follow ->
                                        viewModel.saveAppearanceSettings(appearance.copy(hapticFollowSystem = follow))
                                        haptics?.perform(com.example.ui.theme.AppHapticAction.TOGGLE, forcePreview = true)
                                    },
                                    modifier = Modifier.testTag("toggle_haptic_follow_system")
                                )
                            }

                            if (!appearance.hapticFollowSystem) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("自定义振动强度", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            text = "${(appearance.hapticIntensity * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Slider(
                                        value = appearance.hapticIntensity.coerceIn(0.15f, 1.0f),
                                        onValueChange = { next ->
                                            viewModel.saveAppearanceSettings(
                                                appearance.copy(hapticIntensity = next.coerceIn(0.15f, 1.0f))
                                            )
                                        },
                                        onValueChangeFinished = {
                                            haptics?.perform(com.example.ui.theme.AppHapticAction.CLICK, forcePreview = true)
                                        },
                                        valueRange = 0.15f..1.0f,
                                        modifier = Modifier.testTag("slider_haptic_intensity")
                                    )
                                    val presets = listOf(
                                        0.25f to "轻柔",
                                        0.55f to "标准",
                                        0.80f to "清脆",
                                        1.00f to "强烈"
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        presets.forEach { (level, label) ->
                                            FilterChip(
                                                selected = kotlin.math.abs(appearance.hapticIntensity - level) < 0.08f,
                                                onClick = {
                                                    viewModel.saveAppearanceSettings(appearance.copy(hapticIntensity = level))
                                                    haptics?.perform(com.example.ui.theme.AppHapticAction.CLICK, forcePreview = true)
                                                },
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()
                            Text("分场景细粒度控制", style = MaterialTheme.typography.labelLarge)
                            val sceneItems = listOf(
                                Triple("点击按钮与芯片", appearance.hapticOnClick, { v: Boolean -> appearance.copy(hapticOnClick = v) }),
                                Triple("开关与选项切换", appearance.hapticOnToggle, { v: Boolean -> appearance.copy(hapticOnToggle = v) }),
                                Triple("长按唤起操作", appearance.hapticOnLongPress, { v: Boolean -> appearance.copy(hapticOnLongPress = v) }),
                                Triple("拖动排序与归属高亮", appearance.hapticOnDragReorder, { v: Boolean -> appearance.copy(hapticOnDragReorder = v) }),
                                Triple("删除与危险操作", appearance.hapticOnDestructive, { v: Boolean -> appearance.copy(hapticOnDestructive = v) }),
                                Triple("滑动与缩放手势", appearance.hapticOnGesture, { v: Boolean -> appearance.copy(hapticOnGesture = v) })
                            )
                            sceneItems.forEachIndexed { idx, (label, checked, updater) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    Switch(
                                        checked = checked,
                                        onCheckedChange = { next ->
                                            viewModel.saveAppearanceSettings(updater(next))
                                            if (next) {
                                                val action = com.example.ui.theme.AppHapticAction.entries.getOrElse(idx) {
                                                    com.example.ui.theme.AppHapticAction.CLICK
                                                }
                                                haptics?.perform(action, forcePreview = true)
                                            }
                                        },
                                        modifier = Modifier.testTag("toggle_haptic_scene_$idx")
                                    )
                                }
                            }

                            HorizontalDivider()
                            Text("触感实时体验", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                com.example.ui.theme.AppHapticAction.entries.forEach { action ->
                                    OutlinedButton(
                                        onClick = { haptics?.perform(action, forcePreview = true) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(action.label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "bottom_banner_durations") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_INTERACTION, "bottom_banner_durations")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_APPEARANCE_INTERACTION,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Text(
                            text = settingsHighlightedText("底部提示横栏停留时间", ROUTE_SETTINGS_APPEARANCE_INTERACTION),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("全局提示横栏停留时间", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "当前：${appearance.globalSnackbarDuration.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GlobalToastDuration.entries.forEach { dur ->
                                    FilterChip(
                                        selected = appearance.globalSnackbarDuration == dur,
                                        onClick = {
                                            viewModel.saveAppearanceSettings(appearance.copy(globalSnackbarDuration = dur))
                                        },
                                        label = { Text(dur.label) },
                                        modifier = Modifier.testTag("global_snackbar_duration_${dur.name}")
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    onShowMessage("当前全局提示横栏停留时间：${appearance.globalSnackbarDuration.label}")
                                },
                                modifier = Modifier.testTag("test_global_snackbar_button")
                            ) {
                                Text("预览提示横栏")
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("删除/移出撤回横栏「仅手动关闭」", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "关闭后将跟随上方停留时长（${appearance.globalSnackbarDuration.label}）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appearance.deleteUndoDuration == DeleteUndoDuration.MANUAL_ONLY,
                                onCheckedChange = { manualOnly ->
                                    val nextDur = if (manualOnly) {
                                        DeleteUndoDuration.MANUAL_ONLY
                                    } else {
                                        DeleteUndoDuration.STANDARD
                                    }
                                    viewModel.saveAppearanceSettings(appearance.copy(deleteUndoDuration = nextDur))
                                },
                                modifier = Modifier.testTag("toggle_delete_undo_manual_only")
                            )
                        }
                    }
                }
            }

            item(key = "navigation_and_gestures") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_INTERACTION, "navigation_and_gestures")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = settingsHighlightedText("导航动画与返回手势", ROUTE_SETTINGS_APPEARANCE_INTERACTION),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text("主页点击 Tab 切换动画", style = MaterialTheme.typography.labelLarge)
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
                                    text = "启用系统边缘预测性返回与多级菜单右滑返回",
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
                            Text("预测性返回过渡样式", style = MaterialTheme.typography.labelLarge)
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceIconShortcutSettingsScreen(
    appearance: AppearanceSettings,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var shortcutCustomPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shortcutPhotoError by remember { mutableStateOf<String?>(null) }
    var desktopSyncFeedback by remember { mutableStateOf<String?>(null) }

    val shortcutImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val decoded = DesktopIconSyncManager.decodeSquareShortcutBitmap(context, uri, sizePx = 192)
        if (decoded != null) {
            shortcutCustomPhotoBitmap = decoded
            shortcutPhotoError = null
        } else {
            shortcutPhotoError = "未能解码所选图片，请更换其他相册图片重试"
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "icon_style_settings") {
                val pendingStyle = AppIconStyle.fromId(pendingIconStyleId)
                val hasPendingIconChange = !pendingIconStyleId.equals(appearance.appIconStyleId, ignoreCase = true)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .settingsHighlightSection(ROUTE_SETTINGS_APPEARANCE_ICON, "icon_style_settings")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsSearchArrivalBanner(
                            route = ROUTE_SETTINGS_APPEARANCE_ICON,
                            onDismiss = { viewModel.clearSettingsHighlight() }
                        )
                        Text(
                            text = settingsHighlightedText("图标风格", ROUTE_SETTINGS_APPEARANCE_ICON),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (hasPendingIconChange) {
                                "已预选「${pendingStyle.label}」，点选不会立即切换；点击下方「应用并重启」确认后再切换桌面入口图标"
                            } else {
                                "全部桌面图标均自动动态适配系统亮色/暗色模式（含支持壁纸莫奈取色的「莫奈徽标」）；也可创建自定义桌面快捷方式"
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
                                    shortcutPhotoError = null
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
                    }
                }
            }
        }
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
                    "当前系统未授予本应用「桌面快捷方式」权限或桌面限制了静默创建。请前往应用权限设置开启「桌面快捷方式」后，即可创建完全自定义名称、相册图片或矢量配色的桌面图标。"
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
                        text = "保留原有桌面图标的同时，额外添加一个可自定义名称、相册图片或矢量风格的桌面快捷方式。",
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

                    // Gallery photo icon picker + preview
                    Text("自定义图标图片（相册选图）", style = MaterialTheme.typography.labelLarge)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val pickedBmp = shortcutCustomPhotoBitmap
                            if (pickedBmp != null) {
                                Image(
                                    bitmap = pickedBmp.asImageBitmap(),
                                    contentDescription = "已选快捷方式图片预览",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .testTag("shortcut_gallery_preview_image")
                                )
                            } else {
                                AppBrandIconAvatar(
                                    styleId = shortcutCustomStyleId,
                                    size = 52.dp,
                                    cornerDp = 12.dp
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (pickedBmp != null) "已选用相册自定义图片" else "当前使用矢量风格图标",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { shortcutImagePickerLauncher.launch("image/*") },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("shortcut_pick_gallery_image_button")
                                    ) {
                                        Icon(AppIcons.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (pickedBmp != null) "更换图片" else "选择相册图片", style = MaterialTheme.typography.labelMedium)
                                    }
                                    if (pickedBmp != null) {
                                        TextButton(
                                            onClick = {
                                                shortcutCustomPhotoBitmap = null
                                                shortcutPhotoError = null
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("shortcut_clear_gallery_image_button")
                                        ) {
                                            Text("改用矢量", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    shortcutPhotoError?.let { err ->
                        Text(err, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                    }

                    if (shortcutCustomPhotoBitmap == null) {
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
                }
            },
            dismissButton = {
                TextButton(onClick = { showShortcutCustomDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
                            tertiaryColor = effectiveTertiary,
                            customImageBitmap = shortcutCustomPhotoBitmap,
                            useAdaptiveResource = shortcutUseThemeColor,
                            isDarkTheme = isDarkTheme
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
