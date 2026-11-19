package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.BudgetSettings
import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateState
import com.example.model.LedgerTransaction
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import kotlin.math.roundToLong

enum class LedgerMgmtTab(val label: String) {
    BEHAVIOR_BUDGET("记账与预算"),
    CURRENCY_RATES("币种与汇率"),
    SMART_DEDUP("智能去重")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerManagementScreen(
    settings: BudgetSettings,
    exchangeRates: ExchangeRateState,
    transactionsState: UiState<List<LedgerTransaction>>,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    initialTab: LedgerMgmtTab = LedgerMgmtTab.BEHAVIOR_BUDGET,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LedgerMgmtTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = LedgerMgmtTab.entries.size
                        ),
                        modifier = Modifier.testTag("ledger_mgmt_tab_${tab.name}")
                    ) {
                        Text(tab.label, maxLines = 1)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                LedgerMgmtTab.BEHAVIOR_BUDGET -> BookkeepingBehaviorAndBudgetTab(
                    settings = settings,
                    viewModel = viewModel,
                    onShowMessage = onShowMessage
                )
                LedgerMgmtTab.CURRENCY_RATES -> CurrencySettingsScreen(
                    settings = settings,
                    exchangeRates = exchangeRates,
                    viewModel = viewModel
                )
                LedgerMgmtTab.SMART_DEDUP -> SmartDeduplicationScreen(
                    transactionsState = transactionsState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun BookkeepingBehaviorAndBudgetTab(
    settings: BudgetSettings,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit
) {
    var budgetInput by remember(settings.monthlyBudgetCents) {
        mutableStateOf("%.0f".format(settings.monthlyBudgetCents / 100.0))
    }
    val baseCurrencyInfo = CurrencyCatalog.resolve(settings.baseCurrencyCode)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                            subtitle = "收到支付或银行通知时自动解析账单",
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
                            title = "语义增强识别",
                            subtitle = "未命中固定模板时自动提取商户与分类",
                            checked = settings.smartSemanticEnabled,
                            testTag = "toggle_semantic",
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(smartSemanticEnabled = checked))
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
