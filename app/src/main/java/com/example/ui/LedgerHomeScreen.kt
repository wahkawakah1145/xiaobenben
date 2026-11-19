package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import com.example.ui.components.formatDateTimeFull
import com.example.ui.components.parseDateTimeInputMillis
import com.example.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.CurrencyExchangeManager
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.CardDensity
import com.example.model.CategoryCatalog
import com.example.model.CurrencyCatalog
import com.example.model.ExchangeRateState
import com.example.model.FabPlacement
import com.example.model.HomeHeaderOrder
import com.example.model.LedgerTransaction
import com.example.model.NotificationInquiry
import com.example.model.NumberFormatStyle
import com.example.model.TransactionType
import com.example.parser.ChinaNotificationParser
import com.example.parser.FinanceHumorTips
import com.example.service.NotificationBridge
import com.example.ui.components.BatchEditTransactionsDialog
import com.example.ui.components.BatchSelectionActionBar
import com.example.ui.components.ChannelBadge
import com.example.ui.components.ParseMethodBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.categoryIcon
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatTimeShort
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.random.Random

@Composable
fun LedgerHomeScreen(
    transactionsState: UiState<List<LedgerTransaction>>,
    inquiries: List<NotificationInquiry>,
    settings: BudgetSettings,
    appearance: AppearanceSettings,
    exchangeRates: ExchangeRateState,
    isListenerEnabled: Boolean,
    viewModel: LedgerViewModel,
    onNavigateToParser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    var selectedTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var resolvingInquiry by remember { mutableStateOf<NotificationInquiry?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var bannerTapCount by remember { mutableIntStateOf(0) }
    var lastBannerTapMillis by remember { mutableLongStateOf(0L) }
    val activeTips = remember(appearance.customTipsRaw) {
        FinanceHumorTips.effectiveTips(appearance.customTipsRaw)
    }
    var tipIndex by remember { mutableIntStateOf(Random.nextInt(activeTips.size.coerceAtLeast(1))) }
    val tipTagText = appearance.tipTagText.ifBlank { "我告诉你" }
    val currentTipText = FinanceHumorTips.tipAt(tipIndex, appearance.customTipsRaw)
    val onTipBannerTapped: () -> Unit = {
        tipIndex += 1
        val now = System.currentTimeMillis()
        val nextCount = if (now - lastBannerTapMillis <= 3000L) bannerTapCount + 1 else 1
        lastBannerTapMillis = now
        if (nextCount >= 7) {
            bannerTapCount = 0
            showEasterEggDialog = true
        } else {
            bannerTapCount = nextCount
        }
    }

    val haptic = LocalHapticFeedback.current
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchEditDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val filterOptions = listOf("全部", "支出", "收入", "外币", "支付宝", "微信支付", "银行卡", "云闪付")
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()

    val pendingTransactions by remember(allTransactions) {
        derivedStateOf { allTransactions.filter { !it.confirmed } }
    }
    val confirmedTransactions by remember(allTransactions) {
        derivedStateOf { allTransactions.filter { it.confirmed } }
    }

    LaunchedEffect(confirmedTransactions) {
        if (selectedIds.isNotEmpty()) {
            val validIds = confirmedTransactions.mapTo(HashSet()) { it.id }
            val pruned = selectedIds.intersect(validIds)
            if (pruned.size != selectedIds.size) {
                selectedIds = pruned
            }
        }
    }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    val nowCal = remember { Calendar.getInstance() }
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentMonth = nowCal.get(Calendar.MONTH)
    val baseCurrency = settings.baseCurrencyCode

    val monthlyStats by remember(confirmedTransactions, exchangeRates.ratesToCny, baseCurrency) {
        derivedStateOf {
            val monthItems = LedgerViewModel.filterByMonth(confirmedTransactions, currentYear, currentMonth)
            fun toBase(tx: LedgerTransaction) = CurrencyExchangeManager.convertCents(
                tx.amountCents,
                tx.currencyCode,
                baseCurrency,
                exchangeRates.ratesToCny
            )
            val expense = monthItems.filter { it.txType == TransactionType.EXPENSE }.sumOf { toBase(it) }
            val income = monthItems.filter { it.txType == TransactionType.INCOME }.sumOf { toBase(it) }
            Triple(expense, income, monthItems.size)
        }
    }

    val filteredTransactions by remember(confirmedTransactions, searchQuery, selectedFilter) {
        derivedStateOf {
            confirmedTransactions.filter { tx ->
                val matchesFilter = when (selectedFilter) {
                    "全部" -> true
                    "支出" -> tx.txType == TransactionType.EXPENSE
                    "收入" -> tx.txType == TransactionType.INCOME
                    "外币" -> !tx.currencyCode.equals(baseCurrency, ignoreCase = true)
                    "支付宝" -> tx.channel.contains("支付宝")
                    "微信支付" -> tx.channel.contains("微信")
                    "云闪付" -> tx.channel.contains("云闪付") || tx.channel.contains("银联")
                    "银行卡" -> tx.channel.contains("银行")
                    else -> true
                }
                val q = searchQuery.trim()
                val matchesSearch = q.isEmpty() ||
                    tx.merchant.contains(q, ignoreCase = true) ||
                    tx.category.contains(q, ignoreCase = true) ||
                    tx.channel.contains(q, ignoreCase = true) ||
                    tx.currencyCode.contains(q, ignoreCase = true) ||
                    (tx.note?.contains(q, ignoreCase = true) == true)
                matchesFilter && matchesSearch
            }
        }
    }

    val groupedByDate by remember(filteredTransactions) {
        derivedStateOf {
            filteredTransactions.groupBy { formatDateHeader(it.occurredAtMillis) }
        }
    }

    val isSelectionMode = selectedIds.isNotEmpty()
    val visibleFilteredIds = remember(filteredTransactions) {
        filteredTransactions.map { it.id }.filter { it.isNotBlank() }.toSet()
    }
    val allVisibleSelected = visibleFilteredIds.isNotEmpty() && visibleFilteredIds.all { it in selectedIds }

    val toggleItemSelection: (String) -> Unit = { txId ->
        if (txId.isNotBlank()) {
            selectedIds = if (txId in selectedIds) selectedIds - txId else selectedIds + txId
        }
    }

    val cardSpacing = when (appearance.cardDensity) {
        CardDensity.COMPACT -> 8.dp
        CardDensity.STANDARD -> 12.dp
        CardDensity.COMFORTABLE -> 16.dp
    }
    val rowCornerRadius = when (appearance.cardDensity) {
        CardDensity.COMPACT -> 14.dp
        CardDensity.STANDARD -> 18.dp
        CardDensity.COMFORTABLE -> 22.dp
    }
    val rowVerticalPadding = when (appearance.cardDensity) {
        CardDensity.COMPACT -> 8.dp
        CardDensity.STANDARD -> 12.dp
        CardDensity.COMFORTABLE -> 16.dp
    }
    val fabAlignment = when (appearance.fabPlacement) {
        FabPlacement.BOTTOM_END -> Alignment.BottomEnd
        FabPlacement.BOTTOM_START -> Alignment.BottomStart
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            if (appearance.homeHeaderOrder == HomeHeaderOrder.TIPS_TOP) {
                item(key = "humor_tip_top") {
                    HumorFinanceTipCard(
                        tagText = tipTagText,
                        tipText = currentTipText,
                        onNextTip = onTipBannerTapped
                    )
                }
            }

            if (inquiries.isNotEmpty()) {
                item(key = "notification_inquiries_section") {
                    NotificationInquirySectionCard(
                        inquiries = inquiries,
                        onResolve = { resolvingInquiry = it },
                        onDismiss = { viewModel.dismissInquiry(it.id) }
                    )
                }
            }

            if (pendingTransactions.isNotEmpty()) {
                item(key = "pending_confirm_section") {
                    PendingConfirmationCard(
                        pendingList = pendingTransactions,
                        onConfirmOne = { viewModel.confirmTransaction(it.id) },
                        onEditOne = { editingTransaction = it },
                        onDiscardOne = { viewModel.deleteTransaction(it.id) },
                        onConfirmAll = { viewModel.confirmAllPendingTransactions() }
                    )
                }
            }

            if (!isListenerEnabled) {
                item(key = "listener_banner") {
                    ListenerStatusBanner(
                        isEnabled = false,
                        onOpenSettings = { NotificationBridge.openNotificationListenerSettings(context) },
                        onOpenTestBench = onNavigateToParser
                    )
                }
            }

            item(key = "summary_card") {
                MonthlySummaryCard(
                    monthTitle = "${currentYear}年${currentMonth + 1}月",
                    baseCurrencyCode = baseCurrency,
                    expenseCents = monthlyStats.first,
                    incomeCents = monthlyStats.second,
                    txCount = monthlyStats.third,
                    budgetCents = settings.monthlyBudgetCents,
                    density = appearance.cardDensity
                )
            }

            if (appearance.homeHeaderOrder == HomeHeaderOrder.SUMMARY_TOP) {
                item(key = "humor_tip_below_summary") {
                    HumorFinanceTipCard(
                        tagText = tipTagText,
                        tipText = currentTipText,
                        onNextTip = onTipBannerTapped
                    )
                }
            }

            if (isListenerEnabled) {
                item(key = "listener_banner_compact") {
                    ListenerStatusBanner(
                        isEnabled = true,
                        onOpenSettings = { NotificationBridge.openNotificationListenerSettings(context) },
                        onOpenTestBench = onNavigateToParser
                    )
                }
            }

            item(key = "search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索商户、银行、币种或备注（长按条目可批量编辑）") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.testTag("ledger_search_clear_button")
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ledger_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filterOptions.forEach { option ->
                            FilterChip(
                                selected = selectedFilter == option,
                                onClick = { selectedFilter = option },
                                label = { Text(option) },
                                modifier = Modifier.testTag("filter_chip_$option")
                            )
                        }
                    }

                    if ((searchQuery.isNotBlank() || selectedFilter != "全部") && filteredTransactions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "筛选结果共 ${filteredTransactions.size} 笔（长按任意条目可多选）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    selectedIds = if (allVisibleSelected) {
                                        selectedIds - visibleFilteredIds
                                    } else {
                                        selectedIds + visibleFilteredIds
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("search_batch_select_button")
                            ) {
                                Text(if (allVisibleSelected) "取消选中结果" else "多选全部结果")
                            }
                        }
                    }
                }
            }

            when (transactionsState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "error") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ExpenseCoral.copy(alpha = 0.10f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "账单读取失败：${transactionsState.message}",
                                color = ExpenseCoral,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (filteredTransactions.isEmpty()) {
                        item(key = "empty") {
                            EmptyLedgerCard(
                                hasSearch = searchQuery.isNotBlank() || selectedFilter != "全部",
                                onOpenParser = onNavigateToParser,
                                onAddManual = { showCreateDialog = true }
                            )
                        }
                    } else {
                        groupedByDate.forEach { (dateHeader, itemsForDay) ->
                            val dayExpense = itemsForDay
                                .filter { it.txType == TransactionType.EXPENSE }
                                .sumOf { viewModel.convertToBaseCents(it) }
                            val dayIncome = itemsForDay
                                .filter { it.txType == TransactionType.INCOME }
                                .sumOf { viewModel.convertToBaseCents(it) }

                            item(key = "header_$dateHeader") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (dayExpense > 0L) {
                                            Text(
                                                text = "支 ${formatCurrency(dayExpense, baseCurrency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ExpenseCoral
                                            )
                                        }
                                        if (dayIncome > 0L) {
                                            Text(
                                                text = "收 ${formatCurrency(dayIncome, baseCurrency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IncomeJade
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = itemsForDay,
                                key = { it.id.ifBlank { "${it.occurredAtMillis}_${it.merchant}" } }
                            ) { tx ->
                                val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
                                val isSelected = tx.id in selectedIds
                                TransactionRowCard(
                                    transaction = tx,
                                    recordedBaseCurrencyCode = recordedBase,
                                    recordedRateToBase = recordedRate,
                                    recordedBaseCents = recordedCents,
                                    cornerRadius = rowCornerRadius,
                                    verticalPadding = rowVerticalPadding,
                                    showChannelBadge = appearance.showChannelBadge,
                                    showParseBadge = appearance.showParseBadge,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleItemSelection(tx.id)
                                        } else {
                                            selectedTransaction = tx
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isSelectionMode) {
                                            toggleItemSelection(tx.id)
                                        } else if (tx.id.isNotBlank()) {
                                            selectedIds = setOf(tx.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            BatchSelectionActionBar(
                selectedCount = selectedIds.size,
                allVisibleSelected = allVisibleSelected,
                onToggleSelectAll = {
                    selectedIds = if (allVisibleSelected) {
                        selectedIds - visibleFilteredIds
                    } else {
                        selectedIds + visibleFilteredIds
                    }
                },
                onClearSelection = { selectedIds = emptySet() },
                onBatchEdit = { showBatchEditDialog = true },
                onBatchDelete = { showBatchDeleteConfirm = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(fabAlignment)
                    .padding(20.dp)
                    .testTag("add_transaction_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.add_transaction_cd)
                )
            }
        }
    }

    selectedTransaction?.let { tx ->
        val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
        TransactionDetailSheet(
            transaction = tx,
            baseCurrencyCode = baseCurrency,
            liveRateToBase = viewModel.liveRateToBase(tx),
            liveConvertedBaseCents = viewModel.convertToBaseCents(tx),
            recordedBaseCurrencyCode = recordedBase,
            recordedRateToBase = recordedRate,
            recordedConvertedBaseCents = recordedCents,
            onDismiss = { selectedTransaction = null },
            onConfirmPending = if (!tx.confirmed) {
                {
                    viewModel.confirmTransaction(tx.id)
                    selectedTransaction = null
                }
            } else null,
            onEdit = {
                editingTransaction = tx
                selectedTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                selectedTransaction = null
            }
        )
    }

    if (showCreateDialog) {
        TransactionEditorDialog(
            initialTransaction = null,
            defaultCurrencyCode = baseCurrency,
            onDismiss = { showCreateDialog = false },
            onSave = { amountCents, currencyCode, type, category, merchant, channel, tail, note, occurredAtMillis ->
                viewModel.addManualTransaction(
                    amountCents = amountCents,
                    currencyCode = currencyCode,
                    type = type,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    cardLastFour = tail,
                    note = note,
                    occurredAtMillis = occurredAtMillis
                )
                showCreateDialog = false
            }
        )
    }

    editingTransaction?.let { tx ->
        TransactionEditorDialog(
            initialTransaction = tx,
            defaultCurrencyCode = baseCurrency,
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
    resolvingInquiry?.let { inquiry ->
        ResolveInquiryDialog(
            inquiry = inquiry,
            defaultCurrencyCode = baseCurrency,
            onDismiss = { resolvingInquiry = null },
            onIgnore = {
                viewModel.dismissInquiry(inquiry.id)
                resolvingInquiry = null
            },
            onConfirm = { amountCents, currencyCode, type, category, merchant, channel, learnRule, ruleKeyword, ruleName, numberFormatPattern, ruleDefaultType, occurredAtMillis ->
                viewModel.resolveInquiry(
                    inquiry = inquiry,
                    amountCents = amountCents,
                    currencyCode = currencyCode,
                    type = type,
                    category = category,
                    merchant = merchant,
                    channel = channel,
                    learnRule = learnRule,
                    ruleKeyword = ruleKeyword,
                    ruleName = ruleName,
                    numberFormatPattern = numberFormatPattern,
                    ruleDefaultType = ruleDefaultType,
                    occurredAtMillis = occurredAtMillis
                )
                resolvingInquiry = null
            }
        )
    }

    if (showBatchEditDialog && selectedIds.isNotEmpty()) {
        BatchEditTransactionsDialog(
            selectedCount = selectedIds.size,
            onDismiss = { showBatchEditDialog = false },
            onConfirm = { type, category, channel, merchant, currencyCode, note ->
                viewModel.batchUpdateTransactions(
                    txIds = selectedIds.toList(),
                    type = type,
                    category = category,
                    channel = channel,
                    merchant = merchant,
                    currencyCode = currencyCode,
                    note = note
                )
                showBatchEditDialog = false
                selectedIds = emptySet()
            }
        )
    }

    if (showBatchDeleteConfirm && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("确认批量删除") },
            text = { Text("确定要删除所选的 ${selectedIds.size} 笔账单记录吗？删除后无法撤销。") },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransactions(selectedIds.toList())
                        showBatchDeleteConfirm = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                    modifier = Modifier.testTag("batch_delete_confirm_button")
                ) {
                    Text("确认删除")
                }
            }
        )
    }

    if (showEasterEggDialog) {
        AlertDialog(
            onDismissRequest = { /* 必须点击指定按钮才能关闭 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text("彩蛋发现") },
            text = {
                Text(
                    text = "老实赚钱，很枯燥吧？！",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showEasterEggDialog = false },
                    modifier = Modifier.testTag("easter_egg_close_button")
                ) {
                    Text("继续搬砖")
                }
            }
        )
    }
}

@Composable
private fun HumorFinanceTipCard(
    tagText: String,
    tipText: String,
    onNextTip: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNextTip)
            .testTag("humor_finance_tip_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = tagText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Text(
                text = tipText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "换一条提示",
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun NotificationInquirySectionCard(
    inquiries: List<NotificationInquiry>,
    onResolve: (NotificationInquiry) -> Unit,
    onDismiss: (NotificationInquiry) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_inquiry_section_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "待识别询问 (${inquiries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "确认后可自动学习规则",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            inquiries.take(4).forEach { inquiry ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResolve(inquiry) }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inquiry.title.ifBlank { inquiry.appLabel },
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = inquiry.reason.ifBlank { "待补充信息" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = inquiry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val amountLabel = if (inquiry.suggestedAmountCents > 0L) {
                                "猜想 ${formatCurrency(inquiry.suggestedAmountCents, inquiry.suggestedCurrencyCode)} · ${inquiry.suggestedChannel}"
                            } else {
                                "渠道 ${inquiry.suggestedChannel}"
                            }
                            Text(
                                text = amountLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { onDismiss(inquiry) }) {
                                    Text("忽略")
                                }
                                Button(
                                    onClick = { onResolve(inquiry) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("告知并学习")
                                }
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
private fun ResolveInquiryDialog(
    inquiry: NotificationInquiry,
    defaultCurrencyCode: String,
    onDismiss: () -> Unit,
    onIgnore: () -> Unit,
    onConfirm: (
        amountCents: Long,
        currencyCode: String,
        type: TransactionType,
        category: String,
        merchant: String,
        channel: String,
        learnRule: Boolean,
        ruleKeyword: String,
        ruleName: String,
        numberFormatPattern: String,
        ruleDefaultType: String,
        occurredAtMillis: Long
    ) -> Unit
) {
    val initialAmount = if (inquiry.suggestedAmountCents > 0L) {
        String.format(Locale.US, "%.2f", inquiry.suggestedAmountCents / 100.0)
    } else ""
    var amountText by remember(inquiry.id) { mutableStateOf(initialAmount) }
    var currencyCode by remember(inquiry.id) {
        mutableStateOf(inquiry.suggestedCurrencyCode.ifBlank { defaultCurrencyCode })
    }
    var txType by remember(inquiry.id) {
        mutableStateOf(TransactionType.fromRaw(inquiry.suggestedType))
    }
    var merchant by remember(inquiry.id) {
        mutableStateOf(inquiry.suggestedMerchant.ifBlank { inquiry.title.take(20) })
    }
    var channel by remember(inquiry.id) {
        mutableStateOf(inquiry.suggestedChannel.ifBlank { inquiry.appLabel.ifBlank { "银行卡" } })
    }
    var category by remember(inquiry.id) {
        mutableStateOf(inquiry.suggestedCategory.ifBlank { "餐饮美食" })
    }
    var dateText by remember(inquiry.id) {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(inquiry.occurredAtMillis)))
    }
    var timeText by remember(inquiry.id) {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.US).format(Date(inquiry.occurredAtMillis)))
    }
    var learnRule by remember(inquiry.id) { mutableStateOf(true) }

    val keywordCandidates = remember(inquiry.id) {
        ChinaNotificationParser.extractKeywordCandidates(
            title = inquiry.title,
            content = inquiry.content,
            merchant = inquiry.suggestedMerchant,
            channel = inquiry.suggestedChannel
        )
    }
    var selectedKeywords by remember(inquiry.id) {
        mutableStateOf(listOfNotNull(keywordCandidates.firstOrNull()))
    }
    var customExtraKeyword by remember(inquiry.id) { mutableStateOf("") }
    var numberFormatStyle by remember(inquiry.id) {
        mutableStateOf(ChinaNotificationParser.detectNumberFormatStyle("${inquiry.title} ${inquiry.content}"))
    }
    var followAutoTypeInRule by remember(inquiry.id) { mutableStateOf(true) }
    var showManualKeywordInput by remember(inquiry.id) { mutableStateOf(false) }

    var ruleName by remember(inquiry.id) {
        mutableStateOf(
            if (merchant.isNotBlank()) "学习 · $merchant" else "学习 · $channel"
        )
    }
    var amountError by remember { mutableStateOf<String?>(null) }

    val availableCategories = remember(txType) {
        when (txType) {
            TransactionType.INCOME -> CategoryCatalog.incomeCategories
            TransactionType.TRANSFER -> listOf("转账红包", "其他")
            TransactionType.EXPENSE -> CategoryCatalog.expenseCategories
        }
    }
    val currencyOptions = remember { CurrencyCatalog.currencies.take(6) }

    val resolvedKeyword = remember(selectedKeywords, customExtraKeyword) {
        (selectedKeywords + customExtraKeyword.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("|")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(AppIcons.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("确认通知并学习规则")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${inquiry.title} (${inquiry.packageName})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = inquiry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { t ->
                        FilterChip(
                            selected = txType == t,
                            onClick = {
                                txType = t
                                val validCats = when (t) {
                                    TransactionType.INCOME -> CategoryCatalog.incomeCategories
                                    TransactionType.TRANSFER -> listOf("转账红包", "其他")
                                    TransactionType.EXPENSE -> CategoryCatalog.expenseCategories
                                }
                                if (category !in validCats) category = validCats.first()
                            },
                            label = { Text(t.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = null
                    },
                    label = { Text("金额") },
                    isError = amountError != null,
                    supportingText = amountError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currencyOptions.forEach { c ->
                        FilterChip(
                            selected = currencyCode == c.code,
                            onClick = { currencyCode = c.code },
                            label = { Text("${c.code} ${c.symbol}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商户 / 对方名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("支付渠道 / 银行") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it.take(10); amountError = null },
                        label = { Text("日期 (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.25f)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it.take(5); amountError = null },
                        label = { Text("时间 (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(0.95f)
                    )
                }

                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动更新识别规则", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "优先点选下方提取的关键词与数字格式即可",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = learnRule,
                        onCheckedChange = { learnRule = it }
                    )
                }

                if (learnRule) {
                    if (keywordCandidates.isNotEmpty()) {
                        Text(
                            text = "点选提取的关键词（可多选组合）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            keywordCandidates.forEach { kw ->
                                val selected = kw in selectedKeywords
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedKeywords = if (selected) {
                                            selectedKeywords - kw
                                        } else {
                                            selectedKeywords + kw
                                        }
                                    },
                                    label = { Text(kw) }
                                )
                            }
                        }
                    }

                    Text(
                        text = "学习的数字格式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NumberFormatStyle.entries.forEach { style ->
                            FilterChip(
                                selected = numberFormatStyle == style,
                                onClick = { numberFormatStyle = style },
                                label = { Text(style.label) }
                            )
                        }
                    }
                    Text(
                        text = "示例：${numberFormatStyle.example}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "规则收支类型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = followAutoTypeInRule,
                            onClick = { followAutoTypeInRule = true },
                            label = { Text("智能区分收支") }
                        )
                        FilterChip(
                            selected = !followAutoTypeInRule,
                            onClick = { followAutoTypeInRule = false },
                            label = { Text("固定为${txType.label}") }
                        )
                    }

                    TextButton(
                        onClick = { showManualKeywordInput = !showManualKeywordInput },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (showManualKeywordInput) "收起手动调整" else "手动补充关键词或规则名（当前：$resolvedKeyword）",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (showManualKeywordInput) {
                        OutlinedTextField(
                            value = customExtraKeyword,
                            onValueChange = { customExtraKeyword = it },
                            label = { Text("追加关键词或正则（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("规则名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onIgnore) {
                    Text("非账单")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val yuan = amountText.trim().replace(",", "").toDoubleOrNull()
                    if (yuan == null || yuan <= 0.0 || yuan > 10_000_000.0) {
                        amountError = "请输入有效金额"
                        return@Button
                    }
                    val parsedMillis = parseDateTimeInputMillis(dateText, timeText) ?: inquiry.occurredAtMillis
                    onConfirm(
                        (yuan * 100.0).roundToLong(),
                        currencyCode,
                        txType,
                        category,
                        merchant.trim().ifBlank { channel.ifBlank { "未知商户" } },
                        channel.trim().ifBlank { "未知渠道" },
                        learnRule,
                        resolvedKeyword.ifBlank { channel },
                        ruleName.trim(),
                        numberFormatStyle.name,
                        if (followAutoTypeInRule) "AUTO" else txType.name,
                        parsedMillis
                    )
                }
            ) {
                Text("确认记账")
            }
        }
    )
}

@Composable
private fun PendingConfirmationCard(
    pendingList: List<LedgerTransaction>,
    onConfirmOne: (LedgerTransaction) -> Unit,
    onEditOne: (LedgerTransaction) -> Unit,
    onDiscardOne: (LedgerTransaction) -> Unit,
    onConfirmAll: () -> Unit
) {
    val numberFont = LocalNumberFontFamily.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_confirmation_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = AppIcons.PendingActions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "待确认账单 (${pendingList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = onConfirmAll,
                    modifier = Modifier.testTag("confirm_all_pending_button")
                ) {
                    Icon(AppIcons.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("全部确认")
                }
            }

            pendingList.take(5).forEach { tx ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.merchant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${tx.channel} · ${tx.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                            color = transactionTypeColor(tx.txType),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { onEditOne(tx) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(AppIcons.Tune, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onDiscardOne(tx) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "删除", tint = ExpenseCoral, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onConfirmOne(tx) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("confirm_pending_item_${tx.id}")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = "确认", tint = IncomeJade, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListenerStatusBanner(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenTestBench: () -> Unit
) {
    Surface(
        color = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Rounded.CheckCircle else AppIcons.NotificationsOff,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) stringResource(R.string.listener_enabled) else stringResource(R.string.listener_disabled),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = if (isEnabled) "自动监测支付与银行通知" else "开启权限即可自动捕捉动账通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isEnabled) {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("banner_enable_listener_button")
                ) {
                    Text(stringResource(R.string.enable_listener_action))
                }
            } else {
                TextButton(
                    onClick = onOpenTestBench,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("识别规则")
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    monthTitle: String,
    baseCurrencyCode: String,
    expenseCents: Long,
    incomeCents: Long,
    txCount: Int,
    budgetCents: Long,
    density: CardDensity
) {
    val numberFont = LocalNumberFontFamily.current
    val progress = if (budgetCents > 0L) (expenseCents.toFloat() / budgetCents.toFloat()).coerceIn(0f, 1f) else 0f
    val isOverBudget = budgetCents > 0L && expenseCents > budgetCents
    val cardPadding = when (density) {
        CardDensity.COMPACT -> 14.dp
        CardDensity.STANDARD -> 20.dp
        CardDensity.COMFORTABLE -> 24.dp
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
                        )
                    )
                )
                .padding(cardPadding)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (density == CardDensity.COMPACT) 8.dp else 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$monthTitle · 本月支出 ($baseCurrencyCode)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${txCount} 笔",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = formatCurrency(expenseCents, baseCurrencyCode),
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = numberFont),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column {
                        Text(
                            text = "本月收入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                        Text(
                            text = formatCurrency(incomeCents, baseCurrencyCode),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = numberFont),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "本月结余",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                        val net = incomeCents - expenseCents
                        Text(
                            text = (if (net < 0) "-" else "") + formatCurrency(kotlin.math.abs(net), baseCurrencyCode),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = numberFont),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                if (budgetCents > 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isOverBudget) "已超出预算" else "预算进度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% / ${formatCurrency(budgetCents, baseCurrencyCode)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = if (isOverBudget) Color(0xFFFFB4AB) else Color(0xFFA7F3D0),
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRowCard(
    transaction: LedgerTransaction,
    recordedBaseCurrencyCode: String,
    recordedRateToBase: Double,
    recordedBaseCents: Long,
    cornerRadius: Dp,
    verticalPadding: Dp,
    showChannelBadge: Boolean,
    showParseBadge: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val typeColor = transactionTypeColor(transaction.txType)
    val numberFont = LocalNumberFontFamily.current
    val isForeign = !transaction.currencyCode.equals(recordedBaseCurrencyCode, ignoreCase = true)

    Card(
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("transaction_card_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(visible = isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.testTag("tx_select_checkbox_${transaction.id}")
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(transaction.category),
                    contentDescription = transaction.category,
                    tint = typeColor,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showChannelBadge) {
                        ChannelBadge(channel = transaction.channel)
                    }
                    if (!transaction.cardLastFour.isNullOrBlank()) {
                        Text(
                            text = "•${transaction.cardLastFour}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${transaction.category} · ${formatTimeShort(transaction.occurredAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = formatCurrency(
                        cents = transaction.amountCents,
                        currencyCode = transaction.currencyCode,
                        showSign = true,
                        type = transaction.txType
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = numberFont),
                    color = typeColor,
                    fontWeight = FontWeight.Bold
                )
                if (isForeign) {
                    Text(
                        text = "当时汇率 %.2f · ≈ %s".format(
                            Locale.US,
                            recordedRateToBase,
                            formatCurrency(recordedBaseCents, recordedBaseCurrencyCode)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (showParseBadge) {
                    ParseMethodBadge(method = transaction.txParseMethod)
                }
            }
        }
    }
}

@Composable
private fun EmptyLedgerCard(
    hasSearch: Boolean,
    onOpenParser: () -> Unit,
    onAddManual: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_ledger),
                contentDescription = stringResource(R.string.empty_ledger_cd),
                modifier = Modifier
                    .height(132.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
            Text(
                text = if (hasSearch) "没有匹配的账单记录" else "还没有账单记录",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (hasSearch) {
                    "可以切换筛选条件或清空搜索关键词。"
                } else {
                    "开启通知权限后会自动捕捉消费通知，也可以手动记一笔或测试识别规则。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasSearch) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onAddManual,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("记一笔")
                    }
                    Button(
                        onClick = onOpenParser,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_open_parser_button")
                    ) {
                        Icon(AppIcons.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("识别规则")
                    }
                }
            }
        }
    }
}
