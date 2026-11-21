package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import com.example.ui.theme.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.LedgerTransaction
import com.example.parser.DeduplicationChannelFilter
import com.example.parser.DeduplicationTimeWindow
import com.example.parser.DuplicateGroup
import com.example.parser.SmartDeduplicator
import com.example.ui.components.ChannelBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateTimeFull
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState

@Composable
fun SmartDeduplicationScreen(
    transactionsState: UiState<List<LedgerTransaction>>,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    var customWindowSeconds by rememberSaveable { mutableIntStateOf(SmartDeduplicator.DEFAULT_SHORT_WINDOW_SECONDS) }
    var customValueInput by rememberSaveable { mutableStateOf(SmartDeduplicator.DEFAULT_SHORT_WINDOW_SECONDS.toString()) }
    var customUnitMode by rememberSaveable { mutableIntStateOf(0) }
    var customErrorText by remember { mutableStateOf<String?>(null) }
    var channelFilter by remember { mutableStateOf(DeduplicationChannelFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var inspectingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }

    val candidateGroups by remember(allTransactions, customWindowSeconds, channelFilter) {
        derivedStateOf {
            SmartDeduplicator.findDuplicateGroups(
                transactions = allTransactions,
                maxWindowMillis = customWindowSeconds * 1_000L,
                channelFilter = channelFilter
            )
        }
    }

    val visibleGroups by remember(candidateGroups, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                candidateGroups
            } else {
                candidateGroups.filter { group ->
                    group.representativeMerchant.contains(q, ignoreCase = true) ||
                        group.items.any { tx -> tx.matchesSearchQuery(q) }
                }
            }
        }
    }

    var selectedDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(candidateGroups) {
        val validIds = candidateGroups.flatMap { it.items }.map { it.id }.toSet()
        val defaultSuggested = candidateGroups.flatMap { group ->
            group.items.filter { it.id != group.suggestedKeepId }.map { it.id }
        }.toSet()
        selectedDeleteIds = if (selectedDeleteIds.isEmpty()) {
            defaultSuggested
        } else {
            selectedDeleteIds.intersect(validIds)
        }
    }

    val visibleDeleteCount by remember(visibleGroups, selectedDeleteIds) {
        derivedStateOf {
            visibleGroups.sumOf { g -> g.items.count { it.id in selectedDeleteIds } }
        }
    }

    val numberFont = LocalNumberFontFamily.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "dedup_header") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("短时智能去重筛选", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "仅识别短时间窗口内金额相同且交易对象相似的重复入账，自动排除跨周期的长期重复消费（当前窗口：${SmartDeduplicator.formatWindowLabelSeconds(customWindowSeconds)}；点击勾选，长按条目可弹窗查看详情）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("筛选交易对象、渠道或金额") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dedup_search_input")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DeduplicationTimeWindow.entries.forEach { win ->
                                    val winSeconds = (win.maxDiffMillis / 1_000L).toInt()
                                    FilterChip(
                                        selected = customWindowSeconds == winSeconds,
                                        onClick = {
                                            customWindowSeconds = winSeconds
                                            customErrorText = null
                                            when {
                                                winSeconds < 60 -> {
                                                    customUnitMode = 0
                                                    customValueInput = winSeconds.toString()
                                                }
                                                winSeconds >= 3600 && winSeconds % 3600 == 0 -> {
                                                    customUnitMode = 2
                                                    customValueInput = (winSeconds / 3600).toString()
                                                }
                                                else -> {
                                                    customUnitMode = 1
                                                    customValueInput = (winSeconds / 60).toString()
                                                }
                                            }
                                        },
                                        label = { Text(win.label) },
                                        modifier = Modifier.testTag("dedup_window_${win.name}")
                                    )
                                }
                            }

                            // Custom Short Time Window Controls (Seconds / Minutes / Hours)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = customValueInput,
                                    onValueChange = {
                                        customValueInput = it.filter(Char::isDigit).take(5)
                                        customErrorText = null
                                    },
                                    label = { Text("自定义窗口") },
                                    leadingIcon = { Icon(AppIcons.Schedule, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("dedup_custom_window_input")
                                )
                                FilterChip(
                                    selected = customUnitMode == 0,
                                    onClick = { customUnitMode = 0 },
                                    label = { Text("秒") },
                                    modifier = Modifier.testTag("dedup_unit_seconds")
                                )
                                FilterChip(
                                    selected = customUnitMode == 1,
                                    onClick = { customUnitMode = 1 },
                                    label = { Text("分") },
                                    modifier = Modifier.testTag("dedup_unit_minutes")
                                )
                                FilterChip(
                                    selected = customUnitMode == 2,
                                    onClick = { customUnitMode = 2 },
                                    label = { Text("时") },
                                    modifier = Modifier.testTag("dedup_unit_hours")
                                )
                                OutlinedButton(
                                    onClick = {
                                        val num = customValueInput.trim().toIntOrNull()
                                        val maxSeconds = (SmartDeduplicator.MAX_SHORT_WINDOW_MILLIS / 1_000L).toInt()
                                        if (num == null || num <= 0) {
                                            customErrorText = "请输入大于 0 的整数"
                                        } else {
                                            val computedSeconds = when (customUnitMode) {
                                                0 -> num.toLong()
                                                1 -> num.toLong() * 60L
                                                else -> num.toLong() * 3600L
                                            }
                                            if (computedSeconds > maxSeconds.toLong()) {
                                                customErrorText = "仅支持 7 天（168 小时）以内的短时重复检测"
                                            } else {
                                                customWindowSeconds = computedSeconds.toInt()
                                                customErrorText = null
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dedup_apply_custom_window")
                                ) {
                                    Text("应用")
                                }
                            }
                            if (customErrorText != null) {
                                Text(
                                    text = customErrorText!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseCoral
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DeduplicationChannelFilter.entries.forEach { cf ->
                                    FilterChip(
                                        selected = channelFilter == cf,
                                        onClick = { channelFilter = cf },
                                        label = { Text(cf.label) },
                                        modifier = Modifier.testTag("dedup_channel_${cf.name}")
                                    )
                                }
                            }

                            if (visibleGroups.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedDeleteIds = visibleGroups.flatMap { group ->
                                                group.items.filter { it.id != group.suggestedKeepId }.map { it.id }
                                            }.toSet()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(AppIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("智能勾选重复项")
                                    }
                                    OutlinedButton(
                                        onClick = { selectedDeleteIds = emptySet() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("全部保留 (取消勾选)")
                                    }
                                }
                            }
                        }
                    }
                }

                if (visibleGroups.isEmpty()) {
                    item(key = "dedup_empty") {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dedup_empty_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = IncomeJade,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text("未发现匹配的疑似重复账单", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "当前筛选条件下没有相同金额且交易对象相似的冲突记录。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item(key = "dedup_stats_bar") {
                        Text(
                            text = "共发现 ${visibleGroups.size} 组疑似重复 · 已勾选待清理 $visibleDeleteCount 笔",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(visibleGroups, key = { it.groupId }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            selectedDeleteIds = selectedDeleteIds,
                            numberFont = numberFont,
                            onToggleItem = { txId ->
                                selectedDeleteIds = if (txId in selectedDeleteIds) {
                                    selectedDeleteIds - txId
                                } else {
                                    selectedDeleteIds + txId
                                }
                            },
                            onInspectItem = { tx ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                inspectingTransaction = tx
                            },
                            onKeepOnlyThis = { keepId ->
                                val otherIds = group.items.map { it.id }.filter { it != keepId }.toSet()
                                selectedDeleteIds = (selectedDeleteIds - keepId) + otherIds
                            },
                            onIgnoreGroup = {
                                val groupIds = group.items.map { it.id }.toSet()
                                selectedDeleteIds = selectedDeleteIds - groupIds
                            }
                        )
                    }
                }
            }

            if (selectedDeleteIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "已选待删除 ${selectedDeleteIds.size} 笔",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "未勾选的记录将完整保留",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showConfirmDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dedup_delete_selected_button")
                        ) {
                            Icon(AppIcons.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("一键清理重复")
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("确认清理重复账单") },
            text = {
                Text("确定删除选中的 ${selectedDeleteIds.size} 笔重复账单吗？未勾选的保留记录不会受影响。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransactions(selectedDeleteIds)
                        selectedDeleteIds = emptySet()
                        showConfirmDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                    modifier = Modifier.testTag("dedup_confirm_delete_button")
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    inspectingTransaction?.let { tx ->
        val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
        TransactionDetailSheet(
            transaction = tx,
            baseCurrencyCode = settings.baseCurrencyCode,
            liveRateToBase = viewModel.liveRateToBase(tx),
            liveConvertedBaseCents = viewModel.convertToBaseCents(tx),
            recordedBaseCurrencyCode = recordedBase,
            recordedRateToBase = recordedRate,
            recordedConvertedBaseCents = recordedCents,
            onDismiss = { inspectingTransaction = null },
            onEdit = {
                editingTransaction = tx
                inspectingTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                selectedDeleteIds = selectedDeleteIds - tx.id
                inspectingTransaction = null
            }
        )
    }

    editingTransaction?.let { tx ->
        TransactionEditorDialog(
            initialTransaction = tx,
            defaultCurrencyCode = settings.baseCurrencyCode,
            onDismiss = { editingTransaction = null },
            onSave = { amountCents, currencyCode, type, category, merchant, channel, tail, note, occurredAtMillis ->
                viewModel.updateTransaction(
                    txId = tx.id,
                    amountCents = amountCents,
                    currencyCode = currencyCode,
                    type = type,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    cardLastFour = tail,
                    note = note,
                    occurredAtMillis = occurredAtMillis,
                    confirmed = true
                )
                editingTransaction = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedDeleteIds: Set<String>,
    numberFont: androidx.compose.ui.text.font.FontFamily,
    onToggleItem: (String) -> Unit,
    onInspectItem: (LedgerTransaction) -> Unit,
    onKeepOnlyThis: (String) -> Unit,
    onIgnoreGroup: () -> Unit
) {
    val typeColor = transactionTypeColor(group.txType)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dedup_group_${group.groupId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${group.representativeMerchant} (${group.items.size} 条相似)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = group.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatCurrency(
                        cents = group.amountCents,
                        currencyCode = group.currencyCode,
                        showSign = true,
                        type = group.txType
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = numberFont),
                    color = typeColor,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider()

            group.items.forEach { tx ->
                val markedForDelete = tx.id in selectedDeleteIds
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (markedForDelete) {
                        ExpenseCoral.copy(alpha = 0.08f)
                    } else {
                        IncomeJade.copy(alpha = 0.08f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (markedForDelete) ExpenseCoral.copy(alpha = 0.35f) else IncomeJade.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onToggleItem(tx.id) },
                            onLongClick = { onInspectItem(tx) }
                        )
                        .testTag("dedup_item_${tx.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = markedForDelete,
                            onCheckedChange = { onToggleItem(tx.id) }
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = tx.merchant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    color = if (markedForDelete) ExpenseCoral.copy(alpha = 0.16f) else IncomeJade.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (markedForDelete) "待删除" else "保留",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (markedForDelete) ExpenseCoral else IncomeJade,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ChannelBadge(channel = tx.channel)
                                Text(
                                    text = "${tx.category} · ${formatDateTimeFull(tx.occurredAtMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!tx.note.isNullOrBlank()) {
                                Text(
                                    text = "备注：${tx.note}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        TextButton(
                            onClick = { onKeepOnlyThis(tx.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("仅留此条", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onIgnoreGroup) {
                    Text("全部保留（非重复）", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
