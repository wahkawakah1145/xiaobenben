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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.LocalModelCatalog
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
    modifier: Modifier = Modifier
) {
    val baseCurrencyInfo = CurrencyCatalog.resolve(settings.baseCurrencyCode)
    val rateFreq = ExchangeRateAutoUpdateFrequency.fromRaw(settings.exchangeRateUpdateFrequency)
    val aiStatusSummary = when (aiConfig.providerMode) {
        AiProviderMode.DISABLED -> "未启用 · 仅使用规则引擎（点击接入本地模型或在线 API）"
        AiProviderMode.LOCAL_SMALL_MODEL -> {
            val modelName = LocalModelCatalog.resolve(aiConfig.localModelId).name
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
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = "账单管理",
                            subtitle = "记账与预算 · 币种 ${baseCurrencyInfo.code}(${rateFreq.label}) · 短时智能去重",
                            testTag = "menu_ledger_management",
                            onClick = onOpenLedgerManagement
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.AutoMirrored.Rounded.Rule,
                            title = "通知识别与规则",
                            subtitle = "通知解析测试、自定义关键词与银行规则",
                            testTag = "menu_parser_rules",
                            onClick = onOpenParser
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.Psychology,
                            title = "AI 理解接口",
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
                            icon = Icons.Rounded.FolderCopy,
                            title = "导入与备份中心",
                            subtitle = "手动导入 (XLSX/CSV/JSON) · 本地备份与通知 · WebDAV 同步",
                            testTag = "menu_data_hub",
                            onClick = onOpenDataHub
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.Palette,
                            title = "外观与布局",
                            subtitle = "莫奈动态取色、英文数字字体、卡片大小与元素位置",
                            testTag = "menu_appearance",
                            onClick = onOpenAppearance
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.Security,
                            title = "系统权限与进程保活",
                            subtitle = permissionSummary,
                            testTag = "menu_permissions",
                            onClick = onOpenPermissions
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavRow(
                            icon = Icons.Rounded.Info,
                            title = "关于",
                            subtitle = "版本 v${BuildConfig.VERSION_NAME} · MIT 开源协议 · GitHub 与构建工具",
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
