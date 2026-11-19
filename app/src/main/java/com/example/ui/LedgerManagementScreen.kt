package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BudgetSettings
import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateAutoUpdateFrequency
import com.example.model.LedgerTransaction
import com.example.model.StorageFootprintInfo
import com.example.ui.components.formatCurrency
import com.example.ui.theme.AppIcons
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import kotlin.math.roundToLong

@Composable
fun BillToolsMenuScreen(
    settings: BudgetSettings,
    transactionsState: UiState<List<LedgerTransaction>>,
    storageFootprint: StorageFootprintInfo,
    onOpenBehaviorBudget: () -> Unit,
    onOpenCurrencyRates: () -> Unit,
    onOpenSmartDedup: () -> Unit,
    onOpenStorageOpt: () -> Unit,
    onRefreshFootprint: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onRefreshFootprint()
    }

    val baseCurrencyInfo = CurrencyCatalog.resolve(settings.baseCurrencyCode)
    val rateFreq = ExchangeRateAutoUpdateFrequency.fromRaw(settings.exchangeRateUpdateFrequency)
    val txCount = (transactionsState as? UiState.Success)?.data?.size ?: storageFootprint.transactionCount

    val behaviorSummary = buildString {
        append(if (settings.autoRecordEnabled) "自动捕捉已开启" else "仅手动记账")
        append(" · ")
        append("预算 ${formatCurrency(settings.monthlyBudgetCents, settings.baseCurrencyCode)}")
    }
    val currencySummary = "主币种 ${baseCurrencyInfo.code} (${baseCurrencyInfo.chineseName}) · 自动更新：${rateFreq.label}"
    val dedupSummary = "在 $txCount 笔账单中检索短时间重复支付与退款冲正"
    val storageSummary = "当前占用 ${formatStorageBytes(storageFootprint.totalUserDataBytes)} · $txCount 笔账单 / ${storageFootprint.rawNotificationCount} 条原文"

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "bill_tools_list") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        BillToolMenuRow(
                            icon = AppIcons.Tune,
                            title = "记账与预算",
                            subtitle = behaviorSummary,
                            testTag = "bill_tool_behavior_budget",
                            onClick = onOpenBehaviorBudget
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        BillToolMenuRow(
                            icon = AppIcons.CurrencyExchange,
                            title = "币种与汇率",
                            subtitle = currencySummary,
                            testTag = "bill_tool_currency_rates",
                            onClick = onOpenCurrencyRates
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        BillToolMenuRow(
                            icon = AppIcons.CleaningServices,
                            title = "智能去重",
                            subtitle = dedupSummary,
                            testTag = "bill_tool_smart_dedup",
                            onClick = onOpenSmartDedup
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        BillToolMenuRow(
                            icon = AppIcons.Storage,
                            title = "数据空间与存储优化",
                            subtitle = storageSummary,
                            testTag = "bill_tool_storage_opt",
                            onClick = onOpenStorageOpt
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BillToolMenuRow(
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

@Composable
fun BookkeepingBehaviorScreen(
    settings: BudgetSettings,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var budgetInput by remember(settings.monthlyBudgetCents) {
        mutableStateOf("%.0f".format(settings.monthlyBudgetCents / 100.0))
    }
    val baseCurrencyInfo = CurrencyCatalog.resolve(settings.baseCurrencyCode)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "behavior_preferences_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("记账行为与预算", style = MaterialTheme.typography.titleMedium)

                        BehaviorToggleRow(
                            title = "自动记录通知",
                            subtitle = "收到支付或银行通知时自动解析账单（复杂通知可配合「AI理解」识别）",
                            checked = settings.autoRecordEnabled,
                            testTag = "toggle_auto_record",
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(autoRecordEnabled = checked))
                            }
                        )

                        BehaviorToggleRow(
                            title = "自动记录，进本软件后手动确认",
                            subtitle = "开启后，自动捕捉的账单先进入「待确认」列表，确认后才计入统计",
                            checked = settings.requireManualConfirm,
                            testTag = "toggle_manual_confirm",
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(requireManualConfirm = checked))
                            }
                        )

                        BehaviorToggleRow(
                            title = "入账后发送通知提醒",
                            subtitle = "在状态栏提示已识别金额与分类",
                            checked = settings.notifyOnRecorded,
                            testTag = "toggle_notify_recorded",
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(notifyOnRecorded = checked))
                            }
                        )

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = budgetInput,
                                onValueChange = { budgetInput = it },
                                label = { Text("每月支出预算 (${baseCurrencyInfo.code})") },
                                prefix = { Text(baseCurrencyInfo.symbol) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_budget_input")
                            )
                            Button(
                                onClick = {
                                    val yuan = budgetInput.trim().toDoubleOrNull()
                                    if (yuan == null || yuan < 0.0 || yuan > 10_000_000.0) {
                                        onShowMessage("请输入 0 至 10,000,000 之间的有效预算金额")
                                    } else {
                                        viewModel.saveSettings(
                                            settings.copy(monthlyBudgetCents = (yuan * 100.0).roundToLong())
                                        )
                                        onShowMessage("已保存每月支出预算")
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_save_budget_button")
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageOptimizationScreen(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val footprint by viewModel.storageFootprintState.collectAsStateWithLifecycle()
    val optimizing by viewModel.storageOptimizing.collectAsStateWithLifecycle()
    val numberFont = LocalNumberFontFamily.current

    LaunchedEffect(Unit) {
        viewModel.refreshStorageFootprint(context)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "storage_overview") {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("用户数据空间统计", style = MaterialTheme.typography.titleMedium)
                            }
                            OutlinedButton(
                                enabled = !optimizing,
                                onClick = { viewModel.refreshStorageFootprint(context) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("刷新")
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("总计用户数据占用", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = "${footprint.transactionCount} 笔账单 · ${footprint.rawNotificationCount} 条保留原文 · ${footprint.inquiryCount} 条待询",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatStorageBytes(footprint.totalUserDataBytes),
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = numberFont),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        StorageBreakdownRow("SQLite 主账本库", formatStorageBytes(footprint.databaseBytes))
                        StorageBreakdownRow("WAL / SHM 事务日志", formatStorageBytes(footprint.walBytes))
                        StorageBreakdownRow("偏好与规则设置 (DataStore)", formatStorageBytes(footprint.preferencesBytes))
                        StorageBreakdownRow("本地 AI 模型目录", formatStorageBytes(footprint.aiModelBytes))
                        StorageBreakdownRow("临时缓存目录", formatStorageBytes(footprint.cacheBytes))
                    }
                }
            }

            item(key = "storage_actions") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("体积与空间优化", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "已启用 SQLite 增量空间回收与 WAL 归档收缩；手动执行 VACUUM 可立即整理空闲页并清空应用临时缓存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            enabled = !optimizing,
                            onClick = { viewModel.optimizeStorage(context, pruneRawLogsOlderThanDays = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("optimize_storage_vacuum_button")
                        ) {
                            if (optimizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正在整理空间...")
                            } else {
                                Icon(AppIcons.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("压缩数据库并清理缓存 (VACUUM)")
                            }
                        }

                        OutlinedButton(
                            enabled = !optimizing,
                            onClick = { viewModel.optimizeStorage(context, pruneRawLogsOlderThanDays = 30) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("optimize_storage_prune_raw_button")
                        ) {
                            Text("精简 30 天前已确认账单的原始通知正文")
                        }

                        Surface(
                            color = IncomeJade.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "说明：精简历史原始通知仅移除冗长的通知原文字符串，并清理 30 天前的过期询问记录；金额、商户、分类、卡尾号及交易时间等账目核心数据会完整保留。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownRow(label: String, sizeText: String) {
    val numberFont = LocalNumberFontFamily.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = numberFont),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatStorageBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe B"
        safe < 1024L * 1024L -> "%.1f KB".format(safe / 1024.0)
        else -> "%.2f MB".format(safe / (1024.0 * 1024.0))
    }
}

@Composable
private fun BehaviorToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
