package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BudgetSettings
import com.example.model.CurrencyCatalog
import com.example.service.NotificationBridge
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.LedgerViewModel
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(
    settings: BudgetSettings,
    isListenerEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    isIgnoringBattery: Boolean,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    onOpenParser: () -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenLocalBackup: () -> Unit,
    onOpenWebDav: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rootStatus by viewModel.rootStatusState.collectAsStateWithLifecycle()
    var budgetInput by remember(settings.monthlyBudgetCents) {
        mutableStateOf("%.0f".format(settings.monthlyBudgetCents / 100.0))
    }
    var hasPostNotifPermission by remember {
        mutableStateOf(NotificationBridge.hasPostNotificationPermission(context))
    }

    val postNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifPermission = granted
        onShowMessage(if (granted) "已授予通知发送权限" else "未授予通知发送权限")
    }

    val baseCurrencyInfo = CurrencyCatalog.resolve(settings.baseCurrencyCode)

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
            // 1. Secondary Sub-menus Hub
            item(key = "submenus_card") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        SettingsNavRow(
                            icon = Icons.AutoMirrored.Rounded.Rule,
                            title = "通知识别与规则",
                            subtitle = "通知解析测试、自定义关键词与银行规则",
                            testTag = "menu_parser_rules",
                            onClick = onOpenParser
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.CurrencyExchange,
                            title = "币种与汇率",
                            subtitle = "当前主币种 ${baseCurrencyInfo.code} (${baseCurrencyInfo.chineseName}) · 手动更新汇率库",
                            testTag = "menu_currency_rates",
                            onClick = onOpenCurrency
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.FileUpload,
                            title = "手动导入",
                            subtitle = "导入 JSON / 微信支付宝 CSV 或批量文本账单",
                            testTag = "menu_manual_import",
                            onClick = onOpenImport
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.SaveAlt,
                            title = "本地备份与定时提醒",
                            subtitle = "本地导出恢复 JSON 备份 · 定期紧急通知提醒",
                            testTag = "menu_local_backup",
                            onClick = onOpenLocalBackup
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.CloudSync,
                            title = "WebDAV 备份同步",
                            subtitle = "配置 WebDAV 服务器地址并备份或恢复账本",
                            testTag = "menu_webdav_sync",
                            onClick = onOpenWebDav
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.Palette,
                            title = "外观与布局",
                            subtitle = "莫奈动态取色、英文数字字体、卡片大小与元素位置",
                            testTag = "menu_appearance",
                            onClick = onOpenAppearance
                        )
                    }
                }
            }

            // 2. Auto-Recording & Confirmation Behavior + Budget
            item(key = "preferences_card") {
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

                        SettingToggleRow(
                            title = "自动记录通知",
                            subtitle = "收到支付或银行通知时自动解析账单",
                            checked = settings.autoRecordEnabled,
                            testTag = "toggle_auto_record",
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(autoRecordEnabled = checked))
                            }
                        )

                        SettingToggleRow(
                            title = "自动记录，进本软件后手动确认",
                            subtitle = "开启后，自动捕捉的账单先进入「待确认」列表，确认后才计入统计",
                            checked = settings.requireManualConfirm,
                            testTag = "toggle_manual_confirm",
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(requireManualConfirm = checked))
                            }
                        )

                        SettingToggleRow(
                            title = "语义增强识别",
                            subtitle = "未命中固定模板时自动提取商户与分类",
                            checked = settings.smartSemanticEnabled,
                            testTag = "toggle_semantic",
                            onCheckedChange = { checked ->
                                viewModel.saveSettings(settings.copy(smartSemanticEnabled = checked))
                            }
                        )

                        SettingToggleRow(
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

            // 3. System Permissions & Keep-Alive (Accessibility / Root)
            item(key = "permissions_card") {
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("系统权限与进程保活", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("通知使用权", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isListenerEnabled) "已授权" else "未授权 · 点击前往系统设置开启",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isListenerEnabled) IncomeJade else ExpenseCoral
                                )
                            }
                            Icon(
                                imageVector = if (isListenerEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (isListenerEnabled) IncomeJade else ExpenseCoral
                            )
                        }

                        Button(
                            onClick = { NotificationBridge.openNotificationListenerSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("settings_open_listener_button")
                        ) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isListenerEnabled) "管理通知使用权" else "开启通知读取权限")
                        }

                        HorizontalDivider()

                        // Accessibility Keep-Alive
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("无障碍保活守护", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isAccessibilityEnabled) "运行中 · 断开时自动重绑监听" else "未开启 · 可辅助防止后台被杀",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAccessibilityEnabled) IncomeJade else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { NotificationBridge.openAccessibilitySettings(context) },
                                modifier = Modifier.testTag("settings_accessibility_button")
                            ) {
                                Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isAccessibilityEnabled) "已开启" else "去开启")
                            }
                        }

                        // Battery Optimization Whitelist
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("忽略电池优化", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (isIgnoringBattery) "已加入电池白名单" else "建议设为不限制后台耗电",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isIgnoringBattery) IncomeJade else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isIgnoringBattery) {
                                OutlinedButton(
                                    onClick = { NotificationBridge.requestIgnoreBatteryOptimizations(context) },
                                    modifier = Modifier.testTag("settings_battery_button")
                                ) {
                                    Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("去设置")
                                }
                            } else {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "已优化", tint = IncomeJade)
                            }
                        }

                        HorizontalDivider()

                        // Root Keep-Alive
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Root 进程保活", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (rootStatus.lastResultMessage.isNotBlank()) {
                                            rootStatus.lastResultMessage
                                        } else {
                                            "通过 su 设置 Doze 白名单、后台常驻与进程优先级"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (rootStatus.isRootAvailable) IncomeJade else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.rootKeepAliveEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.saveSettings(settings.copy(rootKeepAliveEnabled = checked))
                                        if (checked) {
                                            viewModel.applyRootKeepAlive(context)
                                        }
                                    },
                                    modifier = Modifier.testTag("toggle_root_keepalive")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.checkRootStatus() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("检测 su 权限")
                                }
                                Button(
                                    onClick = { viewModel.applyRootKeepAlive(context) },
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("apply_root_keepalive_button")
                                ) {
                                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("执行 Root 保活")
                                }
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("入账提醒通知权限", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = if (hasPostNotifPermission) "已允许发送入账提醒" else "未允许",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!hasPostNotifPermission) {
                                    OutlinedButton(
                                        onClick = { postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier.testTag("request_post_notif_button")
                                    ) {
                                        Text("授权")
                                    }
                                } else {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "已授权", tint = IncomeJade)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
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
private fun SettingToggleRow(
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
