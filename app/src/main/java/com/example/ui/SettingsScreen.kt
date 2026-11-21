package com.example.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.AiUnderstandingManager
import com.example.model.AiConfig
import com.example.model.AiProviderMode
import com.example.model.BudgetSettings
import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateAutoUpdateFrequency

@Composable
fun SettingsScreen(
    settings: BudgetSettings,
    aiConfig: AiConfig,
    isListenerEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    isIgnoringBattery: Boolean,
    onOpenLedgerManagement: () -> Unit,
    onOpenParser: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenDataHub: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAbout: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseCurrencyInfo = CurrencyCatalog.resolve(settings.baseCurrencyCode)
    val rateFreq = ExchangeRateAutoUpdateFrequency.fromRaw(settings.exchangeRateUpdateFrequency)
    val aiStatusSummary = when (aiConfig.providerMode) {
        AiProviderMode.DISABLED -> "未启用 · 仅使用规则引擎（点击接入本地模型或在线 API）"
        AiProviderMode.LOCAL_SMALL_MODEL -> {
            val modelName = AiUnderstandingManager().resolveModelDisplayName(context, aiConfig.localModelId)
            "本地开源小模型 · $modelName"
        }
        AiProviderMode.ONLINE_API -> {
            val modelName = aiConfig.apiModelName.ifBlank { "待填写模型" }
            "在线 API · $modelName"
        }
    }
    val permissionSummary = buildString {
        append(if (isListenerEnabled) "通知读取已开启" else "通知读取未开启")
        append(" · ")
        append(if (isAccessibilityEnabled) "无障碍保活运行中" else "保活待配置")
        if (isIgnoringBattery) append(" · 电池白名单")
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "core_ledger_menus") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        SettingsNavRow(
                            icon = AppIcons.AccountBalanceWallet,
                            title = "账单工具",
                            subtitle = "账本 · 文档/剪贴板导入 · 预算 · 币种 ${baseCurrencyInfo.code}(${rateFreq.label}) · 秒级去重 · 存储优化",
                            testTag = "menu_ledger_management",
                            onClick = onOpenLedgerManagement
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = AppIcons.Rule,
                            title = "通知识别与规则",
                            subtitle = "通知解析测试、自定义关键词与银行规则",
                            testTag = "menu_parser_rules",
                            onClick = onOpenParser
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = AppIcons.Psychology,
                            title = "AI理解",
                            subtitle = aiStatusSummary,
                            testTag = "menu_ai_settings",
                            onClick = onOpenAiSettings
                        )
                    }
                }
            }

            item(key = "system_and_about_menus") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        SettingsNavRow(
                            icon = AppIcons.FolderCopy,
                            title = "备份与恢复",
                            subtitle = "本地备份导出与恢复 · 定时备份提醒 · WebDAV 云端同步",
                            testTag = "menu_data_hub",
                            onClick = onOpenDataHub
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = AppIcons.Palette,
                            title = "外观与布局",
                            subtitle = "主题配色、DPI 密度、主页与导航、提示横栏时长、图标与快捷方式",
                            testTag = "menu_appearance",
                            onClick = onOpenAppearance
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = AppIcons.Security,
                            title = "权限管理",
                            subtitle = permissionSummary,
                            testTag = "menu_permissions",
                            onClick = onOpenPermissions
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.Info,
                            title = "关于",
                            subtitle = "版本 v${BuildConfig.VERSION_NAME} · 手动检查更新 · GitHub 仓库与协议",
                            testTag = "menu_about",
                            onClick = onOpenAbout
                        )
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
