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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
    onOpenSearchResult: (SettingsSearchItem, String) -> Unit = { _, _ -> },
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchResults = remember(searchQuery) {
        SettingsSearchCatalog.search(searchQuery)
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "settings_search_bar") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索设置项") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("settings_search_clear")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空设置搜索")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_search_input")
                )
            }

            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    item(key = "settings_search_empty") {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("未找到匹配的设置项", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "可尝试搜索：预算、分类、渠道、账单字段、通知日志、去重、汇率、AI、备份、导出、个性化、触感、DPI、图标、权限",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item(key = "settings_search_count") {
                        Text(
                            text = "找到 ${searchResults.size} 个匹配设置项，点击直达并高亮定位",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(searchResults, key = { "search_${it.id}" }) { item ->
                        val primaryColor = MaterialTheme.colorScheme.primary
                        Card(
                            onClick = { onOpenSearchResult(item, searchQuery.trim()) },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_search_result_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    ) {
                                        Text(
                                            text = buildHighlightedSettingsAnnotatedString(
                                                text = item.categoryLabel,
                                                rawQuery = searchQuery,
                                                highlightColor = primaryColor
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = buildHighlightedSettingsAnnotatedString(
                                            text = item.title,
                                            rawQuery = searchQuery,
                                            highlightColor = primaryColor
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = buildHighlightedSettingsAnnotatedString(
                                            text = item.subtitle,
                                            rawQuery = searchQuery,
                                            highlightColor = primaryColor
                                        ),
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
                    }
                }
            } else {
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
                                subtitle = "账本 · 分类与渠道归属 · 账单字段排序 · 导入 · 预算 · 币种 ${baseCurrencyInfo.code}(${rateFreq.label}) · 去重 · 存储",
                                testTag = "menu_ledger_management",
                                onClick = onOpenLedgerManagement
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsNavRow(
                                icon = AppIcons.Rule,
                                title = "通知识别与规则",
                                subtitle = "通知读取日志、监听应用、解析测试、自定义关键词与银行规则",
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
                                subtitle = "加密 ZIP 备份恢复 · CSV/XLSX/JSON 导出 · 定时提醒 · WebDAV 云同步",
                                testTag = "menu_data_hub",
                                onClick = onOpenDataHub
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsNavRow(
                                icon = AppIcons.Palette,
                                title = "个性化设置",
                                subtitle = "主题配色、DPI 密度、主页与导航、触感反馈、提示横栏、图标与快捷方式",
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
