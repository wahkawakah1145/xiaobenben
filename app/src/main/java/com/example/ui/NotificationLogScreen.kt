package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BudgetSettings
import com.example.model.CapturedNotificationLog
import com.example.ui.components.formatDateTimeFull
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.viewmodel.LedgerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationLogScreen(
    settings: BudgetSettings,
    viewModel: LedgerViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logsState by viewModel.notificationLogsState.collectAsStateWithLifecycle()
    val totalCount by viewModel.notificationLogCountState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLog by remember { mutableStateOf<CapturedNotificationLog?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadNotificationLogs("")
    }

    val listState = rememberLazyListState()
    SettingsAutoScrollEffect(
        listState = listState,
        route = ROUTE_SETTINGS_NOTIFICATION_LOGS,
        sectionKeysInOrder = listOf("notification_log_retention_card", "notification_log_search")
    )

    val countPresets = listOf(
        100 to "100 条",
        500 to "500 条",
        2000 to "2000 条",
        0 to "无限条数"
    )
    val dayPresets = listOf(
        7 to "7 天",
        30 to "30 天",
        90 to "90 天",
        0 to "永久保存"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .testTag("notification_log_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "notification_log_retention_card") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSearchArrivalBanner(
                        route = ROUTE_SETTINGS_NOTIFICATION_LOGS,
                        onDismiss = { viewModel.clearSettingsHighlight() }
                    )
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsHighlightSection(ROUTE_SETTINGS_NOTIFICATION_LOGS, "notification_log_retention_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = settingsHighlightedText("通知读取日志", ROUTE_SETTINGS_NOTIFICATION_LOGS),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "已记录 $totalCount 条通知 · 分页查询低内存占用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { viewModel.loadNotificationLogs(searchQuery) },
                                        modifier = Modifier.testTag("notification_log_refresh_button")
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新日志")
                                    }
                                    OutlinedButton(
                                        enabled = totalCount > 0,
                                        onClick = { showClearConfirm = true },
                                        modifier = Modifier.testTag("notification_log_clear_button")
                                    ) {
                                        Text("清空")
                                    }
                                }
                            }

                            HorizontalDivider()

                            Text(
                                text = "保存条数上限",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                countPresets.forEach { (countVal, label) ->
                                    FilterChip(
                                        selected = settings.notificationLogMaxCount == countVal,
                                        onClick = {
                                            viewModel.updateNotificationLogRetention(
                                                maxCount = countVal,
                                                maxDays = settings.notificationLogMaxDays
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("notification_log_count_$countVal")
                                    )
                                }
                            }

                            Text(
                                text = "保存时间",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                dayPresets.forEach { (daysVal, label) ->
                                    FilterChip(
                                        selected = settings.notificationLogMaxDays == daysVal,
                                        onClick = {
                                            viewModel.updateNotificationLogRetention(
                                                maxCount = settings.notificationLogMaxCount,
                                                maxDays = daysVal
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("notification_log_days_$daysVal")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "notification_log_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { next ->
                        searchQuery = next
                        viewModel.loadNotificationLogs(next)
                    },
                    placeholder = { Text("搜索应用名、包名、通知内容或处理状态") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.loadNotificationLogs("")
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notification_log_search_input")
                )
            }

            if (logsState.isLoading && logsState.items.isEmpty()) {
                item(key = "logs_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else if (logsState.items.isEmpty()) {
                item(key = "logs_empty") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "未搜索到匹配的通知日志" else "暂无通知读取记录",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "开启通知使用权后，系统接收到的每一条通知都会按当前保留策略记录于此。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(logsState.items, key = { it.id }) { log ->
                    val statusColor = when {
                        log.status.contains("自动入账") || log.status.contains("已入账") -> IncomeJade
                        log.status.contains("询问") || log.status.contains("待确认") -> MaterialTheme.colorScheme.primary
                        log.status.contains("忽略") || log.status.contains("无有效金额") -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLog = log }
                            .testTag("notification_log_item_${log.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = log.appLabel.ifBlank { log.packageName },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = statusColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = log.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = formatDateTimeFull(log.capturedAtMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (log.title.isNotBlank()) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (log.content.isNotBlank()) {
                                Text(
                                    text = log.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (log.summary.isNotBlank()) {
                                Text(
                                    text = log.summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (logsState.hasMore) {
                    item(key = "logs_load_more") {
                        OutlinedButton(
                            enabled = !logsState.isLoading,
                            onClick = { viewModel.loadMoreNotificationLogs() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("notification_log_load_more")
                        ) {
                            if (logsState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("加载中...")
                            } else {
                                Text("加载更多日志")
                            }
                        }
                    }
                }
            }

            item(key = "logs_bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    selectedLog?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLog = null },
            title = { Text("${log.appLabel} · ${log.status}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "时间：${formatDateTimeFull(log.capturedAtMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "包名：${log.packageName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    if (log.title.isNotBlank()) {
                        Text("标题", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(log.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (log.content.isNotBlank()) {
                        Text("正文", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(log.content, style = MaterialTheme.typography.bodySmall)
                    }
                    if (log.summary.isNotBlank()) {
                        Text("处理结果", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(log.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = buildString {
                            append(log.title)
                            if (log.title.isNotBlank() && log.content.isNotBlank()) append("\n")
                            append(log.content)
                        }
                        cm?.setPrimaryClip(ClipData.newPlainText("notification_log", clip))
                        onShowMessage("已复制通知原文")
                    }
                ) {
                    Text("复制原文")
                }
            },
            confirmButton = {
                Button(onClick = { selectedLog = null }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部通知日志？") },
            text = { Text("将删除当前已保存的 $totalCount 条通知读取日志（不会影响已入账的账单数据）。") },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllNotificationLogs()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                    modifier = Modifier.testTag("notification_log_clear_confirm")
                ) {
                    Text("确认清空")
                }
            }
        )
    }
}
