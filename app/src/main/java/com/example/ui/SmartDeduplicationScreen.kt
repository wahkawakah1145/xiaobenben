package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartDeduplicationScreen(
    transactionsState: UiState<List<LedgerTransaction>>,
    viewModel: LedgerViewModel,
    settings: BudgetSettings? = null,
    books: List<LedgerBook> = emptyList(),
    bookLinks: List<TransactionBookLink> = emptyList(),
    modifier: Modifier = Modifier
) {
    val observedSettings by viewModel.settingsState.collectAsStateWithLifecycle()
    val effectiveSettings = settings ?: observedSettings
    val haptic = LocalHapticFeedback.current
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val txById = remember(allTransactions) { allTransactions.associateBy { it.id } }
    val excludedIdSet = remember(effectiveSettings.dedupExcludedTransactionIds) {
        effectiveSettings.dedupExcludedTransactionIds.toSet()
    }
    val excludedTransactions = remember(excludedIdSet, txById) {
        excludedIdSet.mapNotNull { txById[it] }.sortedByDescending { it.occurredAtMillis }
    }
    var customWindowSeconds by rememberSaveable { mutableIntStateOf(SmartDeduplicator.DEFAULT_SHORT_WINDOW_SECONDS) }
    var customValueInput by rememberSaveable { mutableStateOf(SmartDeduplicator.DEFAULT_SHORT_WINDOW_SECONDS.toString()) }
    var customUnitMode by rememberSaveable { mutableIntStateOf(0) }
    var customErrorText by remember { mutableStateOf<String?>(null) }
    var channelFilter by remember { mutableStateOf(DeduplicationChannelFilter.ALL) }
    var requireMerchantSimilarity by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showExcludedManagerDialog by remember { mutableStateOf(false) }
    var inspectingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }

    val candidateGroups by remember(
        allTransactions,
        customWindowSeconds,
        channelFilter,
        requireMerchantSimilarity,
        excludedIdSet
    ) {
        derivedStateOf {
            SmartDeduplicator.findDuplicateGroups(
                transactions = allTransactions,
                maxWindowMillis = customWindowSeconds * 1_000L,
                channelFilter = channelFilter,
                requireMerchantSimilarity = requireMerchantSimilarity,
                excludedTransactionIds = excludedIdSet
            )
        }
    }

    val isSearching = searchQuery.trim().isNotEmpty()
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
    var showConfirmTransferDialog by remember { mutableStateOf(false) }
    var initializedFilterKey by remember { mutableStateOf<String?>(null) }
    val filterKey = "$customWindowSeconds|${channelFilter.name}|$requireMerchantSimilarity"

    LaunchedEffect(candidateGroups, filterKey) {
        val validIds = candidateGroups.flatMap { it.items }.map { it.id }.toSet()
        val defaultSuggested = candidateGroups.flatMap { group ->
            group.items.filter { it.id != group.suggestedKeepId }.map { it.id }
        }.toSet()
        if (initializedFilterKey != filterKey) {
            initializedFilterKey = filterKey
            selectedDeleteIds = defaultSuggested
        } else {
            selectedDeleteIds = selectedDeleteIds.intersect(validIds)
        }
    }

    val visibleItemIds by remember(visibleGroups) {
        derivedStateOf {
            visibleGroups.flatMap { it.items }.mapTo(LinkedHashSet()) { it.id }
        }
    }
    val visibleDeleteCount by remember(visibleItemIds, selectedDeleteIds) {
        derivedStateOf {
            visibleItemIds.count { it in selectedDeleteIds }
        }
    }
    val hiddenDeleteCount = (selectedDeleteIds.size - visibleDeleteCount).coerceAtLeast(0)

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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "dedup_header") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .settingsHighlightSection(ROUTE_SETTINGS_DEDUP, "dedup_header")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsSearchArrivalBanner(
                                route = ROUTE_SETTINGS_DEDUP,
                                onDismiss = { viewModel.clearSettingsHighlight() }
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = settingsHighlightedText("短时智能去重筛选", ROUTE_SETTINGS_DEDUP),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = settingsHighlightedText(
                                    "默认仅判断「金额相同 + 时间相近」的条目，并优先勾选删除信息量较少的条目；可按需开启「交易对象相似」缩小范围，支持删除或改为不计收支的转账（当前窗口：${SmartDeduplicator.formatWindowLabelSeconds(customWindowSeconds)}；点击勾选，长按条目弹窗查看详情）。",
                                    ROUTE_SETTINGS_DEDUP
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("筛选交易对象、渠道或金额（批量操作仅影响当前搜索结果）") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                                        }
                                    }
                                } else null,
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
                                FilterChip(
                                    selected = requireMerchantSimilarity,
                                    onClick = { requireMerchantSimilarity = !requireMerchantSimilarity },
                                    label = { Text("交易对象相似") },
                                    modifier = Modifier.testTag("dedup_require_merchant_similarity_chip")
                                )
                                DeduplicationChannelFilter.entries.forEach { cf ->
                                    FilterChip(
                                        selected = channelFilter == cf,
                                        onClick = { channelFilter = cf },
                                        label = { Text(cf.label) },
                                        modifier = Modifier.testTag("dedup_channel_${cf.name}")
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (visibleGroups.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            val visibleSuggested = visibleGroups.flatMap { group ->
                                                group.items.filter { it.id != group.suggestedKeepId }.map { it.id }
                                            }.toSet()
                                            selectedDeleteIds = (selectedDeleteIds - visibleItemIds) + visibleSuggested
                                        },
                                        modifier = Modifier.testTag("dedup_smart_select_button")
                                    ) {
                                        Icon(AppIcons.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isSearching) "智能勾选当前" else "智能勾选重复项")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedDeleteIds = (selectedDeleteIds - visibleItemIds) + (visibleItemIds - selectedDeleteIds)
                                        },
                                        modifier = Modifier.testTag("dedup_invert_selection_button")
                                    ) {
                                        Text(if (isSearching) "反选当前" else "反选")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedDeleteIds = selectedDeleteIds - visibleItemIds
                                        },
                                        modifier = Modifier.testTag("dedup_clear_selection_button")
                                    ) {
                                        Text(if (isSearching) "取消勾选当前" else "全部取消勾选")
                                    }
                                    val visibleSelectedIds = selectedDeleteIds.intersect(visibleItemIds)
                                    OutlinedButton(
                                        onClick = {
                                            val targets = if (visibleSelectedIds.isNotEmpty()) visibleSelectedIds else visibleItemIds
                                            selectedDeleteIds = selectedDeleteIds - targets
                                            viewModel.excludeTransactionsFromDedup(targets)
                                        },
                                        modifier = Modifier.testTag("dedup_exclude_visible_button")
                                    ) {
                                        Icon(AppIcons.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (visibleSelectedIds.isNotEmpty()) {
                                                "排除已选 (${visibleSelectedIds.size})"
                                            } else {
                                                if (isSearching) "排除当前结果" else "排除去重"
                                            }
                                        )
                                    }
                                }
                                if (excludedTransactions.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showExcludedManagerDialog = true },
                                        modifier = Modifier.testTag("dedup_manage_excluded_button")
                                    ) {
                                        Icon(AppIcons.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("已排除去重 (${excludedTransactions.size})")
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
                                    text = if (requireMerchantSimilarity) {
                                        "当前筛选条件下没有金额相同、时间相近且交易对象相似的冲突记录。"
                                    } else {
                                        "当前时间窗口内没有金额相同的疑似重复记录。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item(key = "dedup_stats_bar") {
                        val statsText = if (isSearching && hiddenDeleteCount > 0) {
                            "当前搜索命中 ${visibleGroups.size} 组 · 当前已勾选 $visibleDeleteCount 笔（另有 $hiddenDeleteCount 笔非搜索项保持勾选）"
                        } else {
                            "共发现 ${visibleGroups.size} 组疑似重复 · 已勾选待清理 $visibleDeleteCount 笔"
                        }
                        Text(
                            text = statsText,
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
                            },
                            onExcludeGroup = {
                                val groupIds = group.items.map { it.id }.toSet()
                                selectedDeleteIds = selectedDeleteIds - groupIds
                                viewModel.excludeTransactionsFromDedup(groupIds)
                            },
                            onExcludeItem = { txId ->
                                selectedDeleteIds = selectedDeleteIds - txId
                                viewModel.excludeTransactionsFromDedup(listOf(txId))
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已选重复项 ${selectedDeleteIds.size} 笔",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isSearching && hiddenDeleteCount > 0) {
                                    "当前结果 $visibleDeleteCount 笔 + 其他 $hiddenDeleteCount 笔"
                                } else {
                                    "未勾选记录完整保留"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showConfirmTransferDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dedup_convert_transfer_button")
                            ) {
                                Icon(AppIcons.CurrencyExchange, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("改为不计收支转账", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = { showConfirmDeleteDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dedup_delete_selected_button")
                            ) {
                                Icon(AppIcons.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("删除所选重复", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("确认删除重复账单") },
            text = {
                Text("确定删除选中的 ${selectedDeleteIds.size} 笔重复账单吗？未勾选的保留记录不会受影响（删除后可在底栏撤销）。")
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

    if (showConfirmTransferDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmTransferDialog = false },
            title = { Text("改为不计收支的转账") },
            text = {
                Text("确定将选中的 ${selectedDeleteIds.size} 笔重复账单修改为「转账 · 不计收支」吗？记录会完整保留在列表中，但不再重复计入收支统计（修改后可在底栏撤销）。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.convertTransactionsToNonCountingTransfer(selectedDeleteIds)
                        selectedDeleteIds = emptySet()
                        showConfirmTransferDialog = false
                    },
                    modifier = Modifier.testTag("dedup_confirm_transfer_button")
                ) {
                    Text("确认转换")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmTransferDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showExcludedManagerDialog) {
        AlertDialog(
            onDismissRequest = { showExcludedManagerDialog = false },
            title = { Text("已排除智能去重 (${excludedTransactions.size})") },
            text = {
                if (excludedTransactions.isEmpty()) {
                    Text(
                        text = "暂无被排除智能去重的账单。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "以下账单不会再出现在智能去重候选组中，点击右侧可随时恢复参与去重：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        excludedTransactions.forEach { tx ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.merchant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType)} · ${formatDateTimeFull(tx.occurredAtMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.restoreExcludedDedupTransactions(listOf(tx.id))
                                        },
                                        modifier = Modifier.testTag("dedup_restore_excluded_${tx.id}")
                                    ) {
                                        Text("恢复")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (excludedTransactions.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.restoreExcludedDedupTransactions(excludedTransactions.map { it.id })
                            showExcludedManagerDialog = false
                        },
                        modifier = Modifier.testTag("dedup_restore_all_excluded_button")
                    ) {
                        Text("全部恢复")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showExcludedManagerDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    inspectingTransaction?.let { tx ->
        val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
        val txAssignedBookIds = remember(bookLinks, tx.id) {
            bookLinks.asSequence()
                .filter { it.transactionId == tx.id }
                .map { it.bookId }
                .toSet()
        }
        TransactionDetailSheet(
            transaction = tx,
            baseCurrencyCode = effectiveSettings.baseCurrencyCode,
            liveRateToBase = viewModel.liveRateToBase(tx),
            liveConvertedBaseCents = viewModel.convertToBaseCents(tx),
            recordedBaseCurrencyCode = recordedBase,
            recordedRateToBase = recordedRate,
            recordedConvertedBaseCents = recordedCents,
            onDismiss = { inspectingTransaction = null },
            onMarkNonBill = if (!tx.confirmed || !tx.rawNotification.isNullOrBlank()) {
                {
                    viewModel.markTransactionAsNonBill(tx)
                    selectedDeleteIds = selectedDeleteIds - tx.id
                    inspectingTransaction = null
                }
            } else null,
            books = books,
            assignedBookIds = txAssignedBookIds,
            onUpdateTransactionBooks = { chosen ->
                viewModel.setBooksForTransaction(tx.id, chosen)
            },
            onCreateBookQuick = { name, onCreated ->
                viewModel.saveBook(name = name, onSaved = onCreated)
            },
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
            defaultCurrencyCode = effectiveSettings.baseCurrencyCode,
            enabledCurrencyCodes = effectiveSettings.enabledCurrencyCodes,
            expenseCategories = CategoryCatalog.activeExpenseCategories(effectiveSettings),
            incomeCategories = CategoryCatalog.activeIncomeCategories(effectiveSettings),
            transferCategories = CategoryCatalog.activeTransferCategories(effectiveSettings),
            allTransactions = allTransactions,
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
            },
            onBatchApplySimilar = { targetIds, type, category, channel, merchant, currencyCode, note ->
                viewModel.batchUpdateTransactions(
                    txIds = targetIds,
                    type = type,
                    category = category,
                    channel = channel,
                    merchant = merchant,
                    currencyCode = currencyCode,
                    note = note
                )
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
    onIgnoreGroup: () -> Unit,
    onExcludeGroup: () -> Unit,
    onExcludeItem: (String) -> Unit
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    val badgeText = when {
                                        markedForDelete && tx.id != group.suggestedKeepId -> "待删除 · 信息较少"
                                        markedForDelete -> "待删除"
                                        tx.id == group.suggestedKeepId -> "保留 · 信息较全"
                                        else -> "保留"
                                    }
                                    Text(
                                        text = badgeText,
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
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                onClick = { onKeepOnlyThis(tx.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("仅留此条", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = { onExcludeItem(tx.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("dedup_exclude_item_${tx.id}")
                            ) {
                                Text(
                                    text = "排除去重",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onExcludeGroup,
                    modifier = Modifier.testTag("dedup_exclude_group_${group.groupId}")
                ) {
                    Icon(AppIcons.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("标记本组排除去重", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onIgnoreGroup) {
                    Text("取消本组勾选", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
