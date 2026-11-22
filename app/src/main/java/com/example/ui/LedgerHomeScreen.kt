package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import com.example.ui.components.CategoryIconAvatar
import com.example.ui.components.formatDateTimeFull
import com.example.ui.components.parseDateTimeInputMillis
import com.example.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.example.parser.QuickAnalysisReport
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.model.FeatureTipsCatalog
import com.example.model.HomeHeaderOrder
import com.example.model.HomeStatsDimension
import com.example.model.IconVisualStyle
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import com.example.model.NotificationInquiry
import com.example.model.NumberFormatStyle
import com.example.model.TransactionType
import com.example.parser.ChinaNotificationParser
import com.example.parser.FinanceHumorTips
import com.example.service.NotificationBridge
import com.example.ui.components.BatchEditTransactionsDialog
import com.example.ui.components.BatchSelectionActionBar
import com.example.ui.components.ChannelBadge
import com.example.ui.components.FeatureTipBanner
import com.example.ui.components.ParseMethodBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatItemTimeCompact
import com.example.ui.components.formatTimeShort
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong
import kotlin.random.Random

private data class HomeDateSection(
    val dateHeader: String,
    val items: List<LedgerTransaction>,
    val dayExpense: Long,
    val dayIncome: Long
)

@OptIn(ExperimentalFoundationApi::class)
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
    onNavigateToManualImport: () -> Unit = {},
    books: List<LedgerBook> = emptyList(),
    bookLinks: List<TransactionBookLink> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("全部") }
    var selectedTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var resolvingInquiry by remember { mutableStateOf<NotificationInquiry?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
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
    var quickAnalysisDialogContent by remember { mutableStateOf<Pair<String, QuickAnalysisReport>?>(null) }
    var exportDialogPayload by remember { mutableStateOf<Pair<String, List<LedgerTransaction>>?>(null) }
    var showAddToBooksDialog by remember { mutableStateOf(false) }

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

    BackHandler(enabled = fabExpanded) {
        fabExpanded = false
    }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    val nowCal = remember { Calendar.getInstance() }
    val currentYear = nowCal.get(Calendar.YEAR)
    val currentMonth = nowCal.get(Calendar.MONTH)
    val baseCurrency = settings.baseCurrencyCode

    var statsDimension by remember { mutableStateOf(HomeStatsDimension.THIS_MONTH) }
    var showStatsDimensionDialog by remember { mutableStateOf(false) }
    var syncStatsSelectionToList by remember { mutableStateOf(true) }

    var listTimeDimension by remember { mutableStateOf(HomeStatsDimension.ALL_TIME) }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var customStartDateText by remember { mutableStateOf("") }
    var customEndDateText by remember { mutableStateOf("") }
    var pickingDateTarget by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var selectedTimeOfDaySlot by remember { mutableStateOf(TimeOfDaySlot.ALL) }
    var customStartTimeText by remember { mutableStateOf("") }
    var customEndTimeText by remember { mutableStateOf("") }
    var minAmountInput by remember { mutableStateOf("") }
    var maxAmountInput by remember { mutableStateOf("") }

    val statsSummary by remember(confirmedTransactions, statsDimension, exchangeRates.ratesToCny, baseCurrency) {
        derivedStateOf {
            val dimItems = LedgerViewModel.filterByHomeDimension(confirmedTransactions, statsDimension)
            fun toBase(tx: LedgerTransaction) = CurrencyExchangeManager.convertCents(
                tx.amountCents,
                tx.currencyCode,
                baseCurrency,
                exchangeRates.ratesToCny
            )
            val expense = dimItems.filter { it.txType == TransactionType.EXPENSE }.sumOf { toBase(it) }
            val income = dimItems.filter { it.txType == TransactionType.INCOME }.sumOf { toBase(it) }
            Triple(expense, income, dimItems.size)
        }
    }

    val minAmountCents = remember(minAmountInput) {
        minAmountInput.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { (it * 100.0).roundToLong() }
    }
    val maxAmountCents = remember(maxAmountInput) {
        maxAmountInput.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { (it * 100.0).roundToLong() }
    }
    val customStartMillis = remember(customStartDateText) {
        if (customStartDateText.isBlank()) null else parseDateTimeInputMillis(customStartDateText, "00:00")
    }
    val customEndMillis = remember(customEndDateText) {
        if (customEndDateText.isBlank()) null else parseDateTimeInputMillis(customEndDateText, "23:59")?.plus(59_999L)
    }
    val effectiveStartMinuteOfDay = remember(selectedTimeOfDaySlot, customStartTimeText) {
        parseTimeOfDayMinutes(customStartTimeText) ?: selectedTimeOfDaySlot.startMinute
    }
    val effectiveEndMinuteOfDay = remember(selectedTimeOfDaySlot, customEndTimeText) {
        parseTimeOfDayMinutes(customEndTimeText) ?: selectedTimeOfDaySlot.endMinute
    }
    val hasTimeOfDayFilter = selectedTimeOfDaySlot != TimeOfDaySlot.ALL ||
        customStartTimeText.isNotBlank() ||
        customEndTimeText.isNotBlank()
    val activeRangeFilterCount = remember(
        listTimeDimension,
        customStartDateText,
        customEndDateText,
        selectedTimeOfDaySlot,
        customStartTimeText,
        customEndTimeText,
        minAmountInput,
        maxAmountInput
    ) {
        var count = 0
        if (listTimeDimension != HomeStatsDimension.ALL_TIME) count++
        if (customStartDateText.isNotBlank() || customEndDateText.isNotBlank()) count++
        if (selectedTimeOfDaySlot != TimeOfDaySlot.ALL || customStartTimeText.isNotBlank() || customEndTimeText.isNotBlank()) count++
        if (minAmountInput.isNotBlank() || maxAmountInput.isNotBlank()) count++
        count
    }

    val filteredTransactions by remember(
        confirmedTransactions,
        searchQuery,
        selectedFilter,
        listTimeDimension,
        customStartMillis,
        customEndMillis,
        effectiveStartMinuteOfDay,
        effectiveEndMinuteOfDay,
        minAmountCents,
        maxAmountCents,
        exchangeRates.ratesToCny,
        baseCurrency
    ) {
        derivedStateOf {
            val timeScoped = LedgerViewModel.filterByHomeDimension(confirmedTransactions, listTimeDimension)
            timeScoped.filter { tx ->
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
                val matchesSearch = tx.matchesSearchQuery(q)
                val matchesStart = customStartMillis == null || tx.occurredAtMillis >= customStartMillis
                val matchesEnd = customEndMillis == null || tx.occurredAtMillis <= customEndMillis
                val matchesTimeSlot = matchesTimeOfDayRange(
                    occurredAtMillis = tx.occurredAtMillis,
                    startMinute = effectiveStartMinuteOfDay,
                    endMinute = effectiveEndMinuteOfDay
                )
                val matchesAmount = if (minAmountCents != null || maxAmountCents != null) {
                    val baseCents = CurrencyExchangeManager.convertCents(
                        tx.amountCents,
                        tx.currencyCode,
                        baseCurrency,
                        exchangeRates.ratesToCny
                    )
                    (minAmountCents == null || baseCents >= minAmountCents) &&
                        (maxAmountCents == null || baseCents <= maxAmountCents)
                } else {
                    true
                }
                matchesFilter && matchesSearch && matchesStart && matchesEnd && matchesTimeSlot && matchesAmount
            }
        }
    }

    val hasAnyListFilter = searchQuery.isNotBlank() ||
        selectedFilter != "全部" ||
        listTimeDimension != HomeStatsDimension.ALL_TIME ||
        customStartDateText.isNotBlank() ||
        customEndDateText.isNotBlank() ||
        hasTimeOfDayFilter ||
        minAmountInput.isNotBlank() ||
        maxAmountInput.isNotBlank()

    val pageBatchSize = appearance.homePageLoadBatchSize.coerceIn(20, 500)
    var visibleItemLimit by remember(
        pageBatchSize,
        searchQuery,
        selectedFilter,
        listTimeDimension,
        customStartDateText,
        customEndDateText,
        selectedTimeOfDaySlot,
        customStartTimeText,
        customEndTimeText,
        minAmountInput,
        maxAmountInput
    ) {
        mutableIntStateOf(pageBatchSize)
    }

    val displayedTransactions by remember(filteredTransactions, visibleItemLimit) {
        derivedStateOf {
            if (filteredTransactions.size > visibleItemLimit) {
                filteredTransactions.take(visibleItemLimit)
            } else {
                filteredTransactions
            }
        }
    }
    val remainingTransactionsCount = (filteredTransactions.size - displayedTransactions.size).coerceAtLeast(0)
    val hasMoreTransactions = remainingTransactionsCount > 0

    // Precompute date headers & per-day totals ONLY for currently loaded items (O(batchSize)),
    // avoiding synchronous O(N) recomputation inside LazyColumn DSL on every recomposition.
    val groupedSections by remember(displayedTransactions, currentYear, baseCurrency, exchangeRates.ratesToCny) {
        derivedStateOf {
            val map = LinkedHashMap<String, MutableList<LedgerTransaction>>()
            displayedTransactions.forEach { tx ->
                val header = formatDateHeader(tx.occurredAtMillis, currentYear)
                map.getOrPut(header) { ArrayList() }.add(tx)
            }
            map.entries.map { (header, dayItems) ->
                var dayExp = 0L
                var dayInc = 0L
                dayItems.forEach { tx ->
                    val base = CurrencyExchangeManager.convertCents(
                        tx.amountCents,
                        tx.currencyCode,
                        baseCurrency,
                        exchangeRates.ratesToCny
                    )
                    when (tx.txType) {
                        TransactionType.EXPENSE -> dayExp += base
                        TransactionType.INCOME -> dayInc += base
                        else -> Unit
                    }
                }
                HomeDateSection(
                    dateHeader = header,
                    items = dayItems,
                    dayExpense = dayExp,
                    dayIncome = dayInc
                )
            }
        }
    }

    val density = LocalDensity.current
    val loadMorePullTriggerPx = with(density) { 44.dp.toPx() }
    var bottomPullUpPx by remember { mutableFloatStateOf(0f) }
    val animatedBottomPullPx by animateFloatAsState(
        targetValue = bottomPullUpPx,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 480f),
        label = "home_bottom_pull_load"
    )
    val loadNextPage: () -> Unit = {
        if (hasMoreTransactions) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            visibleItemLimit = (visibleItemLimit + pageBatchSize).coerceAtMost(filteredTransactions.size)
        }
    }
    val bottomPullLoadConnection = remember(hasMoreTransactions, pageBatchSize, filteredTransactions.size, loadMorePullTriggerPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && bottomPullUpPx > 0f && available.y > 0f) {
                    val next = (bottomPullUpPx - available.y).coerceAtLeast(0f)
                    val delta = bottomPullUpPx - next
                    bottomPullUpPx = next
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!hasMoreTransactions || source != NestedScrollSource.UserInput) return Offset.Zero
                if (!listState.canScrollForward && available.y < 0f) {
                    bottomPullUpPx = (bottomPullUpPx + (-available.y) * 0.65f)
                        .coerceIn(0f, loadMorePullTriggerPx * 1.4f)
                    if (bottomPullUpPx >= loadMorePullTriggerPx) {
                        bottomPullUpPx = 0f
                        loadNextPage()
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (hasMoreTransactions && bottomPullUpPx >= loadMorePullTriggerPx * 0.5f) {
                    bottomPullUpPx = 0f
                    loadNextPage()
                } else {
                    bottomPullUpPx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    // Multi-select ALWAYS operates on ALL filteredTransactions (including unloaded ones),
    // never truncated to displayedTransactions!
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

    val listBottomPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSelectionMode) 248.dp else 112.dp,
        label = "home_list_bottom_padding"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(bottomPullLoadConnection),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .graphicsLayer {
                    if (animatedBottomPullPx > 0.5f) {
                        translationY = -(animatedBottomPullPx * 0.35f)
                    }
                },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = listBottomPadding),
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
                        onDismiss = { viewModel.dismissInquiryAsNonBill(it) }
                    )
                }
            }

            if (pendingTransactions.isNotEmpty()) {
                item(key = "pending_confirm_section") {
                    PendingConfirmationCard(
                        pendingList = pendingTransactions,
                        onInspectOne = { selectedTransaction = it },
                        onConfirmOne = { viewModel.confirmTransaction(it.id) },
                        onMarkNonBillOne = { viewModel.markTransactionAsNonBill(it) },
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
                val periodHeader = remember(statsDimension, currentYear, currentMonth) {
                    when (statsDimension) {
                        HomeStatsDimension.TODAY -> "${nowCal.get(Calendar.MONTH) + 1}月${nowCal.get(Calendar.DAY_OF_MONTH)}日"
                        HomeStatsDimension.THIS_WEEK -> "本周统计"
                        HomeStatsDimension.THIS_MONTH -> "${currentYear}年${currentMonth + 1}月"
                        HomeStatsDimension.LAST_MONTH -> {
                            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                            "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月"
                        }
                        HomeStatsDimension.THIS_YEAR -> "${currentYear}年全年"
                        HomeStatsDimension.ALL_TIME -> "全部历史"
                    }
                }
                HomeStatsSummaryCard(
                    dimension = statsDimension,
                    periodTitle = periodHeader,
                    baseCurrencyCode = baseCurrency,
                    expenseCents = statsSummary.first,
                    incomeCents = statsSummary.second,
                    txCount = statsSummary.third,
                    monthlyBudgetCents = settings.monthlyBudgetCents,
                    density = appearance.cardDensity,
                    onClick = { showStatsDimensionDialog = true }
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

            stickyHeader(key = "search_and_filters") {
                val isSearchPinned by remember(listState) {
                    derivedStateOf {
                        val firstVisibleKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key
                        firstVisibleKey == "search_and_filters" && listState.firstVisibleItemScrollOffset > 0
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = if (isSearchPinned) 2.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_sticky_search_header")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索账单") },
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = showAdvancedFilters || activeRangeFilterCount > 0,
                                onClick = { showAdvancedFilters = !showAdvancedFilters },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Tune,
                                        contentDescription = "时间与金额范围筛选",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        if (activeRangeFilterCount > 0) "范围($activeRangeFilterCount)" else "日期/时段/金额"
                                    )
                                },
                                modifier = Modifier.testTag("home_range_filter_toggle")
                            )
                            filterOptions.forEach { option ->
                                FilterChip(
                                    selected = selectedFilter == option,
                                    onClick = { selectedFilter = option },
                                    label = { Text(option) },
                                    modifier = Modifier.testTag("filter_chip_$option")
                                )
                            }
                        }

                        AnimatedVisibility(visible = showAdvancedFilters) {
                            HomeRangeFilterPanel(
                                baseCurrencyCode = baseCurrency,
                                listTimeDimension = listTimeDimension,
                                customStartDateText = customStartDateText,
                                customEndDateText = customEndDateText,
                                selectedTimeOfDaySlot = selectedTimeOfDaySlot,
                                customStartTimeText = customStartTimeText,
                                customEndTimeText = customEndTimeText,
                                minAmountInput = minAmountInput,
                                maxAmountInput = maxAmountInput,
                                onSelectTimeDimension = { dim ->
                                    listTimeDimension = dim
                                    customStartDateText = ""
                                    customEndDateText = ""
                                },
                                onStartDateChange = {
                                    customStartDateText = it
                                    if (it.isNotBlank()) listTimeDimension = HomeStatsDimension.ALL_TIME
                                },
                                onEndDateChange = {
                                    customEndDateText = it
                                    if (it.isNotBlank()) listTimeDimension = HomeStatsDimension.ALL_TIME
                                },
                                onPickStartDate = { pickingDateTarget = "start" },
                                onPickEndDate = { pickingDateTarget = "end" },
                                onSelectTimeOfDaySlot = { slot ->
                                    selectedTimeOfDaySlot = slot
                                    customStartTimeText = ""
                                    customEndTimeText = ""
                                },
                                onStartTimeChange = {
                                    customStartTimeText = it
                                    if (it.isNotBlank()) selectedTimeOfDaySlot = TimeOfDaySlot.ALL
                                },
                                onEndTimeChange = {
                                    customEndTimeText = it
                                    if (it.isNotBlank()) selectedTimeOfDaySlot = TimeOfDaySlot.ALL
                                },
                                onMinAmountChange = { minAmountInput = it },
                                onMaxAmountChange = { maxAmountInput = it },
                                onResetAll = {
                                    listTimeDimension = HomeStatsDimension.ALL_TIME
                                    customStartDateText = ""
                                    customEndDateText = ""
                                    selectedTimeOfDaySlot = TimeOfDaySlot.ALL
                                    customStartTimeText = ""
                                    customEndTimeText = ""
                                    minAmountInput = ""
                                    maxAmountInput = ""
                                },
                                appearance = appearance,
                                onDismissTip = { viewModel.dismissFeatureTip(it) }
                            )
                        }

                        if (hasAnyListFilter && filteredTransactions.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "筛选 ${filteredTransactions.size} 笔",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    TextButton(
                                        onClick = {
                                            val selectedInFiltered = filteredTransactions.filter { it.id in selectedIds }
                                            val targetList = if (selectedInFiltered.isNotEmpty()) selectedInFiltered else filteredTransactions
                                            val title = if (selectedInFiltered.isNotEmpty()) {
                                                "已选搜索结果快速分析 (${targetList.size} 笔)"
                                            } else {
                                                "当前筛选结果快速分析 (${targetList.size} 笔)"
                                            }
                                            val report = viewModel.buildQuickSelectionReport(targetList, title)
                                            quickAnalysisDialogContent = "快速分析" to report
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("search_quick_analyze_button")
                                    ) {
                                        Text("快速分析")
                                    }
                                    TextButton(
                                        onClick = {
                                            val selectedInFiltered = filteredTransactions.filter { it.id in selectedIds }
                                            val targetList = if (selectedInFiltered.isNotEmpty()) selectedInFiltered else filteredTransactions
                                            val title = if (selectedInFiltered.isNotEmpty()) {
                                                "已选筛选账单 (${targetList.size} 笔)"
                                            } else {
                                                "当前筛选账单 (${targetList.size} 笔)"
                                            }
                                            exportDialogPayload = title to targetList
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("search_export_button")
                                    ) {
                                        Text("导出")
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedIds = (selectedIds - visibleFilteredIds) + (visibleFilteredIds - selectedIds)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("search_invert_select_button")
                                    ) {
                                        Text("反选")
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedIds = if (allVisibleSelected) {
                                                selectedIds - visibleFilteredIds
                                            } else {
                                                selectedIds + visibleFilteredIds
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("search_batch_select_button")
                                    ) {
                                        Text(if (allVisibleSelected) "取消全选" else "全选")
                                    }
                                }
                            }
                        }

                        if (isSearchPinned) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
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
                                hasSearch = hasAnyListFilter,
                                appearance = appearance,
                                onImportFile = onNavigateToManualImport,
                                onOpenParser = onNavigateToParser,
                                onAddManual = { showCreateDialog = true }
                            )
                        }
                    } else {
                        if (!isSelectionMode) {
                            item(key = "home_contextual_tip", contentType = "feature_tip") {
                                FeatureTipBanner(
                                    tipId = FeatureTipsCatalog.HOME_MULTI_SELECT,
                                    text = "点击统计卡片可切换维度；长按账单进入多选，支持批量修改、快速分析或加入账本。",
                                    appearance = appearance,
                                    onDismiss = { viewModel.dismissFeatureTip(it) }
                                )
                            }
                        }

                        groupedSections.forEach { section ->
                            item(key = "header_${section.dateHeader}", contentType = "date_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = section.dateHeader,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (section.dayExpense > 0L) {
                                            Text(
                                                text = "支 ${formatCurrency(section.dayExpense, baseCurrency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ExpenseCoral
                                            )
                                        }
                                        if (section.dayIncome > 0L) {
                                            Text(
                                                text = "收 ${formatCurrency(section.dayIncome, baseCurrency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IncomeJade
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = section.items,
                                key = { it.id.ifBlank { "${it.occurredAtMillis}_${it.merchant}" } },
                                contentType = { "tx_card" }
                            ) { tx ->
                                val (recordedBase, recordedRate, recordedCents) = remember(
                                    tx.id,
                                    tx.amountCents,
                                    tx.currencyCode,
                                    tx.recordedRateToBase,
                                    tx.recordedBaseCurrencyCode,
                                    baseCurrency,
                                    exchangeRates.ratesToCny
                                ) {
                                    viewModel.recordedSnapshotDisplay(tx)
                                }
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
                                    iconVisualStyle = appearance.iconVisualStyle,
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
                                            fabExpanded = false
                                            selectedIds = setOf(tx.id)
                                        }
                                    }
                                )
                            }
                        }

                        if (hasMoreTransactions) {
                            item(key = "home_load_more_footer", contentType = "load_more_footer") {
                                val nextBatchCount = minOf(pageBatchSize, remainingTransactionsCount)
                                val pullRatio = (bottomPullUpPx / loadMorePullTriggerPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                Surface(
                                    onClick = loadNextPage,
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (pullRatio > 0.2f) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (pullRatio > 0.2f) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                        .testTag("home_load_more_button")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = if (pullRatio >= 0.8f) {
                                                    "松手立即加载后续 $nextBatchCount 条"
                                                } else {
                                                    "已加载 ${displayedTransactions.size} / ${filteredTransactions.size} 条 · 触底上划或点击加载 $nextBatchCount 条"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        if (pullRatio > 0.05f) {
                                            LinearProgressIndicator(
                                                progress = { pullRatio },
                                                modifier = Modifier
                                                    .fillMaxWidth(0.55f)
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (fabExpanded && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { fabExpanded = false }
            )
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
                onInvertSelection = {
                    selectedIds = (selectedIds - visibleFilteredIds) + (visibleFilteredIds - selectedIds)
                },
                onClearSelection = { selectedIds = emptySet() },
                onBatchEdit = { showBatchEditDialog = true },
                onBatchDelete = { showBatchDeleteConfirm = true },
                onQuickAnalyze = {
                    val selectedItems = confirmedTransactions.filter { it.id in selectedIds }
                    val report = viewModel.buildQuickSelectionReport(
                        transactions = selectedItems,
                        scopeTitle = "已选账单快速分析 (${selectedItems.size} 笔)"
                    )
                    quickAnalysisDialogContent = "快速分析" to report
                },
                onAddToBooks = { showAddToBooksDialog = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            val fabHorizontalAlign = if (appearance.fabPlacement == FabPlacement.BOTTOM_START) {
                Alignment.Start
            } else {
                Alignment.End
            }
            Column(
                modifier = Modifier
                    .align(fabAlignment)
                    .padding(20.dp),
                horizontalAlignment = fabHorizontalAlign,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = fabHorizontalAlign,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (appearance.fabPlacement != FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "导出账单",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    val target = if (hasAnyListFilter) filteredTransactions else confirmedTransactions
                                    val scopeTitle = if (hasAnyListFilter) {
                                        "筛选账单 (${target.size} 笔)"
                                    } else {
                                        "全部账单 (${target.size} 笔)"
                                    }
                                    exportDialogPayload = scopeTitle to target
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fab_action_export_bills")
                            ) {
                                Icon(AppIcons.Download, contentDescription = "导出账单")
                            }
                            if (appearance.fabPlacement == FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "导出账单",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (appearance.fabPlacement != FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "导入账单",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    onNavigateToManualImport()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fab_action_import_bills")
                            ) {
                                Icon(AppIcons.FileOpen, contentDescription = "导入账单")
                            }
                            if (appearance.fabPlacement == FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "导入账单",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (appearance.fabPlacement != FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "识别规则",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    onNavigateToParser()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fab_action_parser_rules")
                            ) {
                                Icon(AppIcons.Rule, contentDescription = "识别规则")
                            }
                            if (appearance.fabPlacement == FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "识别规则",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (appearance.fabPlacement != FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "记一笔",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    showCreateDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("fab_action_add_tx")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "记一笔")
                            }
                            if (appearance.fabPlacement == FabPlacement.BOTTOM_START) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(
                                        text = "记一笔",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_transaction_fab")
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Rounded.Close else Icons.Rounded.Add,
                        contentDescription = if (fabExpanded) "收起快捷菜单" else stringResource(R.string.add_transaction_cd)
                    )
                }
            }
        }
    }

    selectedTransaction?.let { tx ->
        val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
        val txAssignedBookIds = remember(bookLinks, tx.id) {
            bookLinks.asSequence()
                .filter { it.transactionId == tx.id }
                .map { it.bookId }
                .toSet()
        }
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
            onMarkNonBill = if (!tx.confirmed || !tx.rawNotification.isNullOrBlank()) {
                {
                    viewModel.markTransactionAsNonBill(tx)
                    selectedTransaction = null
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
                selectedTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                selectedTransaction = null
            }
        )
    }

    if (showCreateDialog) {
        var extTradeType: String? = null
        var extProductName: String? = null
        var extTradeStatus: String? = null
        var extTxNo: String? = null
        var extMerchantOrderNo: String? = null
        TransactionEditorDialog(
            initialTransaction = null,
            defaultCurrencyCode = baseCurrency,
            enabledCurrencyCodes = settings.enabledCurrencyCodes,
            expenseCategories = CategoryCatalog.activeExpenseCategories(settings),
            incomeCategories = CategoryCatalog.activeIncomeCategories(settings),
            transferCategories = CategoryCatalog.activeTransferCategories(settings),
            channelOptions = com.example.model.ChannelCatalog.activeChannels(settings),
            onDismiss = { showCreateDialog = false },
            onSaveExtended = { tt, pn, ts, tno, mno ->
                extTradeType = tt
                extProductName = pn
                extTradeStatus = ts
                extTxNo = tno
                extMerchantOrderNo = mno
            },
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
                    occurredAtMillis = occurredAtMillis,
                    tradeType = extTradeType,
                    productName = extProductName,
                    tradeStatus = extTradeStatus,
                    transactionNo = extTxNo,
                    merchantOrderNo = extMerchantOrderNo
                )
                showCreateDialog = false
            }
        )
    }

    editingTransaction?.let { tx ->
        var extTradeType: String? = tx.tradeType
        var extProductName: String? = tx.productName
        var extTradeStatus: String? = tx.tradeStatus
        var extTxNo: String? = tx.transactionNo
        var extMerchantOrderNo: String? = tx.merchantOrderNo
        TransactionEditorDialog(
            initialTransaction = tx,
            defaultCurrencyCode = baseCurrency,
            enabledCurrencyCodes = settings.enabledCurrencyCodes,
            expenseCategories = CategoryCatalog.activeExpenseCategories(settings),
            incomeCategories = CategoryCatalog.activeIncomeCategories(settings),
            transferCategories = CategoryCatalog.activeTransferCategories(settings),
            channelOptions = com.example.model.ChannelCatalog.activeChannels(settings),
            allTransactions = confirmedTransactions,
            onBatchApplySimilar = { matchedIds, type, category, channel, merchant, currencyCode, note ->
                viewModel.batchUpdateTransactions(
                    txIds = matchedIds,
                    type = type,
                    category = category,
                    channel = channel,
                    merchant = merchant,
                    currencyCode = currencyCode,
                    note = note
                )
            },
            onDismiss = { editingTransaction = null },
            onMarkAsNonBill = if (!tx.confirmed || !tx.rawNotification.isNullOrBlank()) {
                {
                    viewModel.markTransactionAsNonBill(tx)
                    editingTransaction = null
                }
            } else null,
            onSaveExtended = { tt, pn, ts, tno, mno ->
                extTradeType = tt
                extProductName = pn
                extTradeStatus = ts
                extTxNo = tno
                extMerchantOrderNo = mno
            },
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
                    confirmed = true,
                    tradeType = extTradeType,
                    productName = extProductName,
                    tradeStatus = extTradeStatus,
                    transactionNo = extTxNo,
                    merchantOrderNo = extMerchantOrderNo,
                    updateExtendedFields = true
                )
                editingTransaction = null
            }
        )
    }
    resolvingInquiry?.let { inquiry ->
        ResolveInquiryDialog(
            inquiry = inquiry,
            defaultCurrencyCode = baseCurrency,
            settings = settings,
            onDismiss = { resolvingInquiry = null },
            onIgnore = {
                viewModel.dismissInquiryAsNonBill(inquiry)
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

    exportDialogPayload?.let { (scopeTitle, targetList) ->
        com.example.ui.components.LedgerExportDialog(
            scopeTitle = scopeTitle,
            transactions = targetList,
            baseCurrencyCode = baseCurrency,
            ratesToCny = exchangeRates.ratesToCny,
            onDismiss = { exportDialogPayload = null },
            onExportFeedback = { msg -> viewModel.postActionMessage(msg) }
        )
    }

    if (showBatchEditDialog && selectedIds.isNotEmpty()) {
        BatchEditTransactionsDialog(
            selectedCount = selectedIds.size,
            expenseCategories = CategoryCatalog.activeExpenseCategories(settings),
            incomeCategories = CategoryCatalog.activeIncomeCategories(settings),
            transferCategories = CategoryCatalog.activeTransferCategories(settings),
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

    quickAnalysisDialogContent?.let { (dlgTitle, report) ->
        QuickAnalysisTextDialog(
            title = dlgTitle,
            summaryText = report.plainText,
            report = report,
            onDismiss = { quickAnalysisDialogContent = null }
        )
    }

    if (showAddToBooksDialog && selectedIds.isNotEmpty()) {
        val commonBookIds = remember(selectedIds, bookLinks) {
            val byBook = bookLinks.groupBy({ it.bookId }, { it.transactionId }).mapValues { it.value.toSet() }
            byBook.filterValues { ids -> selectedIds.all { it in ids } }.keys
        }
        AddToBooksMultiSelectDialog(
            selectedTxCount = selectedIds.size,
            books = books,
            preselectedBookIds = commonBookIds,
            onCreateBookQuick = { newName, onCreated ->
                viewModel.saveBook(name = newName, onSaved = onCreated)
            },
            onDismiss = { showAddToBooksDialog = false },
            onConfirm = { chosenBooks ->
                viewModel.addTransactionsToBooks(selectedIds.toList(), chosenBooks)
                showAddToBooksDialog = false
                selectedIds = emptySet()
            }
        )
    }

    if (showBatchDeleteConfirm && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("确认批量删除") },
            text = { Text("确定要删除所选的 ${selectedIds.size} 笔账单记录吗？删除后可在底部提示栏点击撤销。") },
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
            title = null,
            text = {
                Text(
                    text = "老实赚钱，很枯燥吧？！",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("easter_egg_message_text")
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEasterEggDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseCoral),
                    modifier = Modifier.testTag("easter_egg_exit_button")
                ) {
                    Text("残忍退出")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showEasterEggDialog = false },
                    modifier = Modifier.testTag("easter_egg_close_button")
                ) {
                    Text("开心收下")
                }
            }
        )
    }

    if (showStatsDimensionDialog) {
        HomeStatsDimensionPickerDialog(
            currentDimension = statsDimension,
            syncWithList = syncStatsSelectionToList,
            confirmedTransactions = confirmedTransactions,
            baseCurrencyCode = baseCurrency,
            ratesToCny = exchangeRates.ratesToCny,
            onToggleSyncWithList = { syncStatsSelectionToList = it },
            onSelectDimension = { picked ->
                statsDimension = picked
                if (syncStatsSelectionToList) {
                    listTimeDimension = picked
                    customStartDateText = ""
                    customEndDateText = ""
                }
                showStatsDimensionDialog = false
            },
            onDismiss = { showStatsDimensionDialog = false }
        )
    }

    if (pickingDateTarget != null) {
        HomeFilterDatePickerDialog(
            initialDateText = if (pickingDateTarget == "start") customStartDateText else customEndDateText,
            onConfirmDate = { formatted ->
                if (pickingDateTarget == "start") {
                    customStartDateText = formatted
                } else {
                    customEndDateText = formatted
                }
                listTimeDimension = HomeStatsDimension.ALL_TIME
                pickingDateTarget = null
            },
            onDismiss = { pickingDateTarget = null }
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
                                    Text("非账单")
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
    settings: BudgetSettings,
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

    val availableCategories = remember(txType, settings) {
        when (txType) {
            TransactionType.INCOME -> CategoryCatalog.activeIncomeCategories(settings)
            TransactionType.TRANSFER -> CategoryCatalog.activeTransferCategories(settings)
            TransactionType.EXPENSE -> CategoryCatalog.activeExpenseCategories(settings)
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
    onInspectOne: (LedgerTransaction) -> Unit,
    onConfirmOne: (LedgerTransaction) -> Unit,
    onMarkNonBillOne: (LedgerTransaction) -> Unit,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onInspectOne(tx) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                        }
                        if (!tx.rawNotification.isNullOrBlank()) {
                            Text(
                                text = tx.rawNotification.replace('\n', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onMarkNonBillOne(tx) },
                                colors = ButtonDefaults.textButtonColors(contentColor = ExpenseCoral),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("pending_mark_non_bill_${tx.id}")
                            ) {
                                Text("非账单", style = MaterialTheme.typography.labelMedium)
                            }
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
private fun HomeStatsSummaryCard(
    dimension: HomeStatsDimension,
    periodTitle: String,
    baseCurrencyCode: String,
    expenseCents: Long,
    incomeCents: Long,
    txCount: Int,
    monthlyBudgetCents: Long,
    density: CardDensity,
    onClick: () -> Unit
) {
    val numberFont = LocalNumberFontFamily.current
    val scaledBudgetCents = remember(monthlyBudgetCents, dimension) {
        val m = monthlyBudgetCents.coerceAtLeast(0L)
        when (dimension) {
            HomeStatsDimension.TODAY -> (m / 30L).coerceAtLeast(if (m > 0L) 1L else 0L)
            HomeStatsDimension.THIS_WEEK -> ((m * 7L) / 30L).coerceAtLeast(if (m > 0L) 1L else 0L)
            HomeStatsDimension.THIS_MONTH, HomeStatsDimension.LAST_MONTH -> m
            HomeStatsDimension.THIS_YEAR -> m * 12L
            HomeStatsDimension.ALL_TIME -> 0L
        }
    }
    val progress = if (scaledBudgetCents > 0L) (expenseCents.toFloat() / scaledBudgetCents.toFloat()).coerceIn(0f, 1f) else 0f
    val isOverBudget = scaledBudgetCents > 0L && expenseCents > scaledBudgetCents
    val cardPadding = when (density) {
        CardDensity.COMPACT -> 14.dp
        CardDensity.STANDARD -> 20.dp
        CardDensity.COMFORTABLE -> 24.dp
    }
    val budgetLabel = when (dimension) {
        HomeStatsDimension.TODAY -> "日预算进度"
        HomeStatsDimension.THIS_WEEK -> "周预算进度"
        HomeStatsDimension.THIS_MONTH, HomeStatsDimension.LAST_MONTH -> "月预算进度"
        HomeStatsDimension.THIS_YEAR -> "年预算进度"
        HomeStatsDimension.ALL_TIME -> "预算进度"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("home_stats_summary_card")
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
                        text = "$periodTitle · ${dimension.label} ($baseCurrencyCode)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        Surface(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${dimension.shortLabel} ▾",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
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
                            text = "${dimension.shortLabel}收入",
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
                            text = "${dimension.shortLabel}结余",
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

                if (scaledBudgetCents > 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isOverBudget) "已超出$budgetLabel" else budgetLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% / ${formatCurrency(scaledBudgetCents, baseCurrencyCode)}",
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

@Composable
private fun HomeStatsDimensionPickerDialog(
    currentDimension: HomeStatsDimension,
    syncWithList: Boolean,
    confirmedTransactions: List<LedgerTransaction>,
    baseCurrencyCode: String,
    ratesToCny: Map<String, Double>,
    onToggleSyncWithList: (Boolean) -> Unit,
    onSelectDimension: (HomeStatsDimension) -> Unit,
    onDismiss: () -> Unit
) {
    val numberFont = LocalNumberFontFamily.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择统计卡片时间维度") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "同步筛选下方账单列表",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = syncWithList,
                        onCheckedChange = onToggleSyncWithList,
                        modifier = Modifier.testTag("stats_dimension_sync_list_switch")
                    )
                }
                HorizontalDivider()
                HomeStatsDimension.entries.forEach { dim ->
                    val items = remember(confirmedTransactions, dim) {
                        LedgerViewModel.filterByHomeDimension(confirmedTransactions, dim)
                    }
                    val expense = remember(items, ratesToCny, baseCurrencyCode) {
                        items.filter { it.txType == TransactionType.EXPENSE }.sumOf { tx ->
                            CurrencyExchangeManager.convertCents(tx.amountCents, tx.currencyCode, baseCurrencyCode, ratesToCny)
                        }
                    }
                    val income = remember(items, ratesToCny, baseCurrencyCode) {
                        items.filter { it.txType == TransactionType.INCOME }.sumOf { tx ->
                            CurrencyExchangeManager.convertCents(tx.amountCents, tx.currencyCode, baseCurrencyCode, ratesToCny)
                        }
                    }
                    val selected = dim == currentDimension
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectDimension(dim) }
                            .testTag("home_stats_dimension_option_${dim.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = dim.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "${items.size} 笔记录",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "支 ${formatCurrency(expense, baseCurrencyCode)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                    color = ExpenseCoral,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "收 ${formatCurrency(income, baseCurrencyCode)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                                    color = IncomeJade
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

enum class TimeOfDaySlot(
    val label: String,
    val startMinute: Int?,
    val endMinute: Int?
) {
    ALL("全天", null, null),
    EARLY_MORNING("凌晨 00-06", 0, 359),
    MORNING("上午 06-12", 360, 719),
    NOON("午间 11-14", 660, 839),
    AFTERNOON("下午 12-18", 720, 1079),
    EVENING("晚间 18-24", 1080, 1439)
}

internal fun autoFormatDateInput(previous: String, rawInput: String): String {
    val normalized = rawInput.replace('/', '-').replace('.', '-')
    val cleaned = normalized.filter { it.isDigit() || it == '-' }
    val isDeleting = rawInput.length < previous.length

    if (!isDeleting && cleaned.count { it == '-' } >= 2) {
        val parts = cleaned.split('-')
        val year = parts.getOrNull(0).orEmpty().filter { it.isDigit() }.take(4)
        val monthRaw = parts.getOrNull(1).orEmpty().filter { it.isDigit() }.take(2)
        val dayRaw = parts.getOrNull(2).orEmpty().filter { it.isDigit() }.take(2)
        if (year.length == 4 && monthRaw.isNotEmpty()) {
            val month = if (monthRaw.length == 1) "0$monthRaw" else monthRaw
            return if (dayRaw.isEmpty()) "$year-$month-" else "$year-$month-$dayRaw"
        }
    }

    val digits = cleaned.filter { it.isDigit() }.take(8)
    if (digits.isEmpty()) return ""
    return when {
        digits.length < 4 -> digits
        digits.length == 4 -> if (isDeleting) digits else "$digits-"
        digits.length == 5 -> "${digits.substring(0, 4)}-${digits.substring(4)}"
        digits.length == 6 -> {
            val base = "${digits.substring(0, 4)}-${digits.substring(4, 6)}"
            if (isDeleting) base else "$base-"
        }
        else -> "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6)}"
    }
}

internal fun autoFormatTimeOfDayInput(previous: String, rawInput: String): String {
    val normalized = rawInput.replace('：', ':')
    val cleaned = normalized.filter { it.isDigit() || it == ':' }
    val isDeleting = rawInput.length < previous.length
    val digits = cleaned.filter { it.isDigit() }.take(4)
    if (digits.isEmpty()) return ""
    return when {
        digits.length < 2 -> digits
        digits.length == 2 -> if (isDeleting) digits else "$digits:"
        else -> "${digits.substring(0, 2)}:${digits.substring(2)}"
    }
}

internal fun parseTimeOfDayMinutes(input: String): Int? {
    val trimmed = input.trim().replace('：', ':')
    if (trimmed.isEmpty()) return null
    val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(trimmed) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour == 24 && minute == 0) return 1439
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun matchesTimeOfDayRange(
    occurredAtMillis: Long,
    startMinute: Int?,
    endMinute: Int?
): Boolean {
    if (startMinute == null && endMinute == null) return true
    val cal = Calendar.getInstance().apply { timeInMillis = occurredAtMillis }
    val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return when {
        startMinute != null && endMinute != null -> {
            if (startMinute <= endMinute) {
                minuteOfDay in startMinute..endMinute
            } else {
                // Support overnight window e.g. 22:00 ~ 02:00
                minuteOfDay >= startMinute || minuteOfDay <= endMinute
            }
        }
        startMinute != null -> minuteOfDay >= startMinute
        else -> minuteOfDay <= endMinute!!
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeRangeFilterPanel(
    baseCurrencyCode: String,
    listTimeDimension: HomeStatsDimension,
    customStartDateText: String,
    customEndDateText: String,
    selectedTimeOfDaySlot: TimeOfDaySlot,
    customStartTimeText: String,
    customEndTimeText: String,
    minAmountInput: String,
    maxAmountInput: String,
    onSelectTimeDimension: (HomeStatsDimension) -> Unit,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    onSelectTimeOfDaySlot: (TimeOfDaySlot) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    onResetAll: () -> Unit,
    appearance: AppearanceSettings? = null,
    onDismissTip: (String) -> Unit = {}
) {
    var startDateField by remember {
        mutableStateOf(TextFieldValue(customStartDateText, TextRange(customStartDateText.length)))
    }
    var endDateField by remember {
        mutableStateOf(TextFieldValue(customEndDateText, TextRange(customEndDateText.length)))
    }
    var startTimeField by remember {
        mutableStateOf(TextFieldValue(customStartTimeText, TextRange(customStartTimeText.length)))
    }
    var endTimeField by remember {
        mutableStateOf(TextFieldValue(customEndTimeText, TextRange(customEndTimeText.length)))
    }

    LaunchedEffect(customStartDateText) {
        if (startDateField.text != customStartDateText) {
            startDateField = TextFieldValue(customStartDateText, TextRange(customStartDateText.length))
        }
    }
    LaunchedEffect(customEndDateText) {
        if (endDateField.text != customEndDateText) {
            endDateField = TextFieldValue(customEndDateText, TextRange(customEndDateText.length))
        }
    }
    LaunchedEffect(customStartTimeText) {
        if (startTimeField.text != customStartTimeText) {
            startTimeField = TextFieldValue(customStartTimeText, TextRange(customStartTimeText.length))
        }
    }
    LaunchedEffect(customEndTimeText) {
        if (endTimeField.text != customEndTimeText) {
            endTimeField = TextFieldValue(customEndTimeText, TextRange(customEndTimeText.length))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_range_filter_panel")
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
                Text("时间、时段与金额筛选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = onResetAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.testTag("home_range_filter_reset")
                ) {
                    Text("重置条件", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (appearance != null) {
                FeatureTipBanner(
                    tipId = FeatureTipsCatalog.HOME_SEARCH_SYNTAX,
                    text = "搜索框支持空格分隔多关键词（匹配对方、商品、分类、币种、单号、备注）；日期框输入数字自动补全连字符，时段筛选按每日发生时间过滤。",
                    appearance = appearance,
                    onDismiss = onDismissTip
                )
            }

            Text("快捷日期", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeStatsDimension.entries.forEach { dim ->
                    val isPresetSelected = listTimeDimension == dim &&
                        customStartDateText.isBlank() &&
                        customEndDateText.isBlank()
                    FilterChip(
                        selected = isPresetSelected,
                        onClick = { onSelectTimeDimension(dim) },
                        label = { Text(dim.shortLabel) },
                        modifier = Modifier.testTag("list_time_preset_${dim.name}")
                    )
                }
            }

            Text("自定义日期区间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startDateField,
                    onValueChange = { incoming ->
                        val formatted = autoFormatDateInput(startDateField.text, incoming.text)
                        startDateField = TextFieldValue(formatted, TextRange(formatted.length))
                        onStartDateChange(formatted)
                    },
                    label = { Text("起始日期") },
                    placeholder = { Text("yyyy-MM-dd") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = onPickStartDate) {
                            Icon(AppIcons.CalendarMonth, contentDescription = "选择起始日期", modifier = Modifier.size(18.dp))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_start_date_input")
                )
                OutlinedTextField(
                    value = endDateField,
                    onValueChange = { incoming ->
                        val formatted = autoFormatDateInput(endDateField.text, incoming.text)
                        endDateField = TextFieldValue(formatted, TextRange(formatted.length))
                        onEndDateChange(formatted)
                    },
                    label = { Text("结束日期") },
                    placeholder = { Text("yyyy-MM-dd") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = onPickEndDate) {
                            Icon(AppIcons.CalendarMonth, contentDescription = "选择结束日期", modifier = Modifier.size(18.dp))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_end_date_input")
                )
            }

            Text("时段", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeOfDaySlot.entries.forEach { slot ->
                    val isSlotSelected = selectedTimeOfDaySlot == slot &&
                        customStartTimeText.isBlank() &&
                        customEndTimeText.isBlank()
                    FilterChip(
                        selected = isSlotSelected,
                        onClick = { onSelectTimeOfDaySlot(slot) },
                        label = { Text(slot.label) },
                        modifier = Modifier.testTag("filter_time_slot_${slot.name}")
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startTimeField,
                    onValueChange = { incoming ->
                        val formatted = autoFormatTimeOfDayInput(startTimeField.text, incoming.text)
                        startTimeField = TextFieldValue(formatted, TextRange(formatted.length))
                        onStartTimeChange(formatted)
                    },
                    label = { Text("起始时段") },
                    placeholder = { Text("HH:mm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_start_time_input")
                )
                OutlinedTextField(
                    value = endTimeField,
                    onValueChange = { incoming ->
                        val formatted = autoFormatTimeOfDayInput(endTimeField.text, incoming.text)
                        endTimeField = TextFieldValue(formatted, TextRange(formatted.length))
                        onEndTimeChange(formatted)
                    },
                    label = { Text("结束时段") },
                    placeholder = { Text("HH:mm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_end_time_input")
                )
            }

            Text("金额区间 (折合 $baseCurrencyCode)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minAmountInput,
                    onValueChange = { next ->
                        onMinAmountChange(next.filter { it.isDigit() || it == '.' }.take(10))
                    },
                    label = { Text("最低金额") },
                    placeholder = { Text("不限") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_min_amount_input")
                )
                OutlinedTextField(
                    value = maxAmountInput,
                    onValueChange = { next ->
                        onMaxAmountChange(next.filter { it.isDigit() || it == '.' }.take(10))
                    },
                    label = { Text("最高金额") },
                    placeholder = { Text("不限") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_max_amount_input")
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple("不限", "", ""),
                    Triple("< 50", "", "50"),
                    Triple("50 ~ 200", "50", "200"),
                    Triple("200 ~ 1000", "200", "1000"),
                    Triple("≥ 1000", "1000", "")
                ).forEach { (label, minVal, maxVal) ->
                    AssistChip(
                        onClick = {
                            onMinAmountChange(minVal)
                            onMaxAmountChange(maxVal)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFilterDatePickerDialog(
    initialDateText: String,
    onConfirmDate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialUtc = remember(initialDateText) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = parseDateTimeInputMillis(initialDateText, "12:00") ?: System.currentTimeMillis()
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = millis
                    }
                    onConfirmDate(
                        "%04d-%02d-%02d".format(
                            Locale.US,
                            utcCal.get(Calendar.YEAR),
                            utcCal.get(Calendar.MONTH) + 1,
                            utcCal.get(Calendar.DAY_OF_MONTH)
                        )
                    )
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(state = pickerState)
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
    iconVisualStyle: IconVisualStyle,
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

            CategoryIconAvatar(
                category = transaction.category,
                tintColor = typeColor,
                visualStyle = iconVisualStyle,
                size = 42.dp
            )

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
                val productPreview = transaction.productName?.takeIf { it.isNotBlank() && it != transaction.merchant }
                if (productPreview != null) {
                    Text(
                        text = productPreview,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                        text = "${transaction.category} · ${formatItemTimeCompact(transaction.occurredAtMillis)}",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyLedgerCard(
    hasSearch: Boolean,
    appearance: AppearanceSettings,
    onImportFile: () -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🤗",
                fontSize = 76.sp,
                lineHeight = 84.sp,
                modifier = Modifier.testTag("empty_state_emoji")
            )
            Text(
                text = if (hasSearch) "没有匹配的账单记录" else "还没有账单记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (appearance.isFeatureTipVisible(FeatureTipsCatalog.HOME_MULTI_SELECT)) {
                Text(
                    text = if (hasSearch) {
                        "可调整筛选条件或清空关键词。"
                    } else {
                        "可从文件或文本导入历史账单、手动记一笔，或开启通知自动记录。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (!hasSearch) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onImportFile,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_import_bills_button")
                    ) {
                        Icon(AppIcons.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("导入账单")
                    }
                    FilledTonalButton(
                        onClick = onAddManual,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_add_manual_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("记一笔")
                    }
                    OutlinedButton(
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
