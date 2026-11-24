package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CurrencyExchangeManager
import com.example.model.BudgetSettings
import com.example.model.CategoryCatalog
import com.example.model.ChannelCatalog
import com.example.model.ExchangeRateState
import com.example.model.FeatureTipsCatalog
import com.example.model.HomeStatsDimension
import com.example.model.IconVisualStyle
import com.example.model.LedgerBook
import com.example.model.LedgerTransaction
import com.example.model.TransactionBookLink
import com.example.model.TransactionType
import com.example.parser.QuickAnalysisBreakdownItem
import com.example.parser.QuickAnalysisReport
import com.example.ui.components.BatchEditTransactionsDialog
import com.example.ui.components.BatchSelectionActionBar
import com.example.ui.components.CategoryIconAvatar
import com.example.ui.components.ChannelBadge
import com.example.ui.components.FeatureTipBanner
import com.example.ui.components.LedgerExportDialog
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatItemTimeCompact
import com.example.ui.components.parseDateTimeInputMillis
import com.example.ui.components.transactionTypeColor
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val bookColorPresets = listOf(
    0xFF0F6B54L,
    0xFF1677FFL,
    0xFFE07A2EL,
    0xFF7B4EC9L,
    0xFFC6382EL,
    0xFF008B8BL,
    0xFFB87300L,
    0xFF334155L
)

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun LedgerBooksScreen(
    books: List<LedgerBook>,
    bookLinks: List<TransactionBookLink>,
    transactionsState: UiState<List<LedgerTransaction>>,
    settings: BudgetSettings,
    exchangeRates: ExchangeRateState,
    viewModel: LedgerViewModel,
    onAnalyzeBook: (String) -> Unit,
    openBookId: String? = null,
    onOpenBook: (String) -> Unit = {},
    onCloseBook: () -> Unit = {},
    canSwipeToAnalytics: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    detailListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()
    val txById = remember(allTransactions) { allTransactions.associateBy { it.id } }
    val appearance by viewModel.appearanceState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val baseCurrency = settings.baseCurrencyCode
    val numberFont = LocalNumberFontFamily.current

    val txIdsByBook = remember(bookLinks) {
        bookLinks.groupBy({ it.bookId }, { it.transactionId }).mapValues { it.value.toSet() }
    }
    val bookIdsByTx = remember(bookLinks) {
        bookLinks.groupBy({ it.transactionId }, { it.bookId }).mapValues { it.value.toSet() }
    }

    var showCreateBookDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var deletingBook by remember { mutableStateOf<LedgerBook?>(null) }
    var quickSummaryBook by remember { mutableStateOf<Pair<String, QuickAnalysisReport>?>(null) }
    var exportBookPayload by remember { mutableStateOf<Triple<String, List<LedgerTransaction>, String?>?>(null) }
    var inspectingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTx by remember { mutableStateOf<LedgerTransaction?>(null) }
    val activeOpenBook = remember(books, openBookId) {
        openBookId?.let { id -> books.firstOrNull { it.id == id } }
    }

    fun toBase(tx: LedgerTransaction): Long = CurrencyExchangeManager.convertCents(
        tx.amountCents,
        tx.currencyCode,
        baseCurrency,
        exchangeRates.ratesToCny
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = activeOpenBook?.id,
                transitionSpec = {
                    fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                },
                label = "books_detail_switch",
                modifier = Modifier.fillMaxSize()
            ) { currentBookId ->
                val openBook = currentBookId?.let { id -> books.firstOrNull { it.id == id } }
                if (openBook != null) {
                    val density = LocalDensity.current
                    val viewConfig = LocalViewConfiguration.current
                    val touchSlop = viewConfig.touchSlop
                    val swipeRightTriggerPx = with(density) { 76.dp.toPx() }
                    val pullDownTriggerPx = with(density) { 84.dp.toPx() }
                    val maxPullPx = with(density) { 148.dp.toPx() }

                    var swipeRightOffsetPx by remember(openBook.id) { mutableFloatStateOf(0f) }
                    var pullDownOffsetPx by remember(openBook.id) { mutableFloatStateOf(0f) }
                    val animatedSwipeX by animateFloatAsState(
                        targetValue = swipeRightOffsetPx,
                        animationSpec = tween(durationMillis = if (swipeRightOffsetPx == 0f) 180 else 16),
                        label = "book_detail_swipe_x"
                    )
                    val animatedPullY by animateFloatAsState(
                        targetValue = pullDownOffsetPx,
                        animationSpec = tween(durationMillis = if (pullDownOffsetPx == 0f) 180 else 16),
                        label = "book_detail_pull_y"
                    )

                    val pullDownConnection = remember(openBook.id, detailListState) {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput && pullDownOffsetPx > 0f && available.y < 0f) {
                                    val next = (pullDownOffsetPx + available.y).coerceAtLeast(0f)
                                    val consumedY = next - pullDownOffsetPx
                                    pullDownOffsetPx = next
                                    return Offset(0f, consumedY)
                                }
                                return Offset.Zero
                            }

                            override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource
                            ): Offset {
                                val atTop = detailListState.firstVisibleItemIndex == 0 &&
                                    detailListState.firstVisibleItemScrollOffset == 0
                                if (source == NestedScrollSource.UserInput && atTop && available.y > 0f) {
                                    pullDownOffsetPx = (pullDownOffsetPx + available.y * 0.55f).coerceIn(0f, maxPullPx)
                                    return Offset(0f, available.y)
                                }
                                return Offset.Zero
                            }

                            override suspend fun onPreFling(available: Velocity): Velocity {
                                if (pullDownOffsetPx >= pullDownTriggerPx) {
                                    pullDownOffsetPx = 0f
                                    onCloseBook()
                                    return available
                                }
                                pullDownOffsetPx = 0f
                                return Velocity.Zero
                            }
                        }
                    }

                    // Book-detail search & filter state (parity with LedgerHomeScreen)
                    var detailSearchQuery by remember(openBook.id) { mutableStateOf("") }
                    var detailSelectedFilter by remember(openBook.id) { mutableStateOf("全部") }
                    var detailShowAdvancedFilters by remember(openBook.id) { mutableStateOf(false) }
                    var detailTimeDimension by remember(openBook.id) { mutableStateOf(HomeStatsDimension.ALL_TIME) }
                    var detailStartDateText by remember(openBook.id) { mutableStateOf("") }
                    var detailEndDateText by remember(openBook.id) { mutableStateOf("") }
                    var detailPickingDateTarget by remember(openBook.id) { mutableStateOf<String?>(null) }
                    var detailTimeOfDaySlot by remember(openBook.id) { mutableStateOf(TimeOfDaySlot.ALL) }
                    var detailStartTimeText by remember(openBook.id) { mutableStateOf("") }
                    var detailEndTimeText by remember(openBook.id) { mutableStateOf("") }
                    var detailMinAmountInput by remember(openBook.id) { mutableStateOf("") }
                    var detailMaxAmountInput by remember(openBook.id) { mutableStateOf("") }

                    val detailFilterOptions = remember {
                        listOf("全部", "支出", "收入", "外币", "支付宝", "微信支付", "银行卡", "云闪付")
                    }

                    val bookTxList = remember(txIdsByBook, openBook.id, txById) {
                        txIdsByBook[openBook.id].orEmpty()
                            .mapNotNull { txById[it] }
                            .sortedByDescending { it.occurredAtMillis }
                    }
                    val detailMinAmountCents = remember(detailMinAmountInput) {
                        detailMinAmountInput.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { (it * 100.0).roundToLong() }
                    }
                    val detailMaxAmountCents = remember(detailMaxAmountInput) {
                        detailMaxAmountInput.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { (it * 100.0).roundToLong() }
                    }
                    val detailStartMillis = remember(detailStartDateText) {
                        if (detailStartDateText.isBlank()) null else parseDateTimeInputMillis(detailStartDateText, "00:00")
                    }
                    val detailEndMillis = remember(detailEndDateText) {
                        if (detailEndDateText.isBlank()) null else parseDateTimeInputMillis(detailEndDateText, "23:59")?.plus(59_999L)
                    }
                    val detailStartMinOfDay = remember(detailTimeOfDaySlot, detailStartTimeText) {
                        parseTimeOfDayMinutes(detailStartTimeText) ?: detailTimeOfDaySlot.startMinute
                    }
                    val detailEndMinOfDay = remember(detailTimeOfDaySlot, detailEndTimeText) {
                        parseTimeOfDayMinutes(detailEndTimeText) ?: detailTimeOfDaySlot.endMinute
                    }
                    val detailActiveRangeFilterCount = remember(
                        detailTimeDimension,
                        detailStartDateText,
                        detailEndDateText,
                        detailTimeOfDaySlot,
                        detailStartTimeText,
                        detailEndTimeText,
                        detailMinAmountInput,
                        detailMaxAmountInput
                    ) {
                        var count = 0
                        if (detailTimeDimension != HomeStatsDimension.ALL_TIME) count++
                        if (detailStartDateText.isNotBlank() || detailEndDateText.isNotBlank()) count++
                        if (detailTimeOfDaySlot != TimeOfDaySlot.ALL || detailStartTimeText.isNotBlank() || detailEndTimeText.isNotBlank()) count++
                        if (detailMinAmountInput.isNotBlank() || detailMaxAmountInput.isNotBlank()) count++
                        count
                    }
                    val hasAnyDetailFilter = detailSearchQuery.isNotBlank() ||
                        detailSelectedFilter != "全部" ||
                        detailActiveRangeFilterCount > 0

                    val filteredBookTxs by remember(
                        bookTxList,
                        detailSearchQuery,
                        detailSelectedFilter,
                        detailTimeDimension,
                        detailStartMillis,
                        detailEndMillis,
                        detailStartMinOfDay,
                        detailEndMinOfDay,
                        detailMinAmountCents,
                        detailMaxAmountCents,
                        exchangeRates.ratesToCny,
                        baseCurrency
                    ) {
                        derivedStateOf {
                            val timeScoped = LedgerViewModel.filterByHomeDimension(bookTxList, detailTimeDimension)
                            timeScoped.filter { tx ->
                                val resolvedParentChannel = ChannelCatalog.resolveDisplayChannel(
                                    tx.channel,
                                    settings.channelParentMap,
                                    showChildren = false
                                )
                                val matchesFilter = when (detailSelectedFilter) {
                                    "全部" -> true
                                    "支出" -> tx.txType == TransactionType.EXPENSE
                                    "收入" -> tx.txType == TransactionType.INCOME
                                    "外币" -> !tx.currencyCode.equals(baseCurrency, ignoreCase = true)
                                    "支付宝" -> tx.channel.contains("支付宝") || resolvedParentChannel.contains("支付宝")
                                    "微信支付" -> tx.channel.contains("微信") || resolvedParentChannel.contains("微信")
                                    "云闪付" -> tx.channel.contains("云闪付") || tx.channel.contains("银联") || resolvedParentChannel.contains("云闪付")
                                    "银行卡" -> tx.channel.contains("银行") || resolvedParentChannel.contains("银行")
                                    else -> true
                                }
                                val matchesSearch = tx.matchesSearchQuery(
                                    rawQuery = detailSearchQuery.trim(),
                                    categoryParentMap = settings.categoryParentMap,
                                    channelParentMap = settings.channelParentMap
                                )
                                val matchesStart = detailStartMillis == null || tx.occurredAtMillis >= detailStartMillis
                                val matchesEnd = detailEndMillis == null || tx.occurredAtMillis <= detailEndMillis
                                val matchesTimeSlot = matchesTimeOfDayRange(
                                    occurredAtMillis = tx.occurredAtMillis,
                                    startMinute = detailStartMinOfDay,
                                    endMinute = detailEndMinOfDay
                                )
                                val matchesAmount = if (detailMinAmountCents != null || detailMaxAmountCents != null) {
                                    val baseCents = toBase(tx)
                                    (detailMinAmountCents == null || baseCents >= detailMinAmountCents) &&
                                        (detailMaxAmountCents == null || baseCents <= detailMaxAmountCents)
                                } else {
                                    true
                                }
                                matchesFilter && matchesSearch && matchesStart && matchesEnd && matchesTimeSlot && matchesAmount
                            }
                        }
                    }

                    val confirmedBookTxs = remember(bookTxList) { bookTxList.filter { it.confirmed } }
                    val expenseCents = remember(confirmedBookTxs, baseCurrency, exchangeRates.ratesToCny) {
                        confirmedBookTxs.filter { it.txType == TransactionType.EXPENSE }.sumOf { toBase(it) }
                    }
                    val incomeCents = remember(confirmedBookTxs, baseCurrency, exchangeRates.ratesToCny) {
                        confirmedBookTxs.filter { it.txType == TransactionType.INCOME }.sumOf { toBase(it) }
                    }
                    val netCents = incomeCents - expenseCents
                    val accent = Color(openBook.colorArgb.toInt())

                    var detailSelectedIds by remember(openBook.id) { mutableStateOf(emptySet<String>()) }
                    val isDetailSelectionMode = detailSelectedIds.isNotEmpty()
                    var showDetailBatchEdit by remember(openBook.id) { mutableStateOf(false) }
                    var showDetailBatchDeleteConfirm by remember(openBook.id) { mutableStateOf(false) }
                    var showDetailAddToOtherBooks by remember(openBook.id) { mutableStateOf(false) }

                    LaunchedEffect(bookTxList) {
                        if (detailSelectedIds.isNotEmpty()) {
                            val validIds = bookTxList.map { it.id }.toSet()
                            val pruned = detailSelectedIds.intersect(validIds)
                            if (pruned.size != detailSelectedIds.size) {
                                detailSelectedIds = pruned
                            }
                        }
                    }

                    BackHandler(enabled = isDetailSelectionMode) {
                        detailSelectedIds = emptySet()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pullDownConnection)
                            .pointerInput(openBook.id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial
                                    )
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    var lockedRightSwipe = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            if (lockedRightSwipe) {
                                                val shouldReturn = swipeRightOffsetPx >= swipeRightTriggerPx
                                                swipeRightOffsetPx = 0f
                                                if (shouldReturn) {
                                                    onCloseBook()
                                                }
                                            }
                                            break
                                        }
                                        val delta = change.positionChange()
                                        totalDx += delta.x
                                        totalDy += delta.y

                                        if (!lockedRightSwipe) {
                                            if (totalDx > touchSlop && totalDx > abs(totalDy) * 1.25f) {
                                                // Right swipe inside book detail: intercept before HorizontalPager
                                                // so user returns to Books Home first; swiping right again goes to Ledger.
                                                lockedRightSwipe = true
                                                change.consume()
                                                swipeRightOffsetPx = totalDx.coerceAtLeast(0f)
                                            } else if (totalDx < -touchSlop && abs(totalDx) > abs(totalDy) * 1.15f) {
                                                // Left swipe: let HorizontalPager handle swiping to Analytics tab.
                                                break
                                            } else if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) {
                                                // Vertical scroll / top pull-down handled by LazyColumn + NestedScroll.
                                                break
                                            }
                                        } else {
                                            change.consume()
                                            swipeRightOffsetPx = (swipeRightOffsetPx + delta.x).coerceAtLeast(0f)
                                        }
                                    }
                                    if (!lockedRightSwipe && swipeRightOffsetPx != 0f) {
                                        swipeRightOffsetPx = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        LazyColumn(
                            state = detailListState,
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = (animatedSwipeX * 0.55f).coerceAtLeast(0f)
                                    translationY = (animatedPullY * 0.45f).coerceAtLeast(0f)
                                    val dragRatio = maxOf(
                                        (animatedSwipeX / (swipeRightTriggerPx * 2.5f)).coerceIn(0f, 0.25f),
                                        (animatedPullY / (pullDownTriggerPx * 2.5f)).coerceIn(0f, 0.2f)
                                    )
                                    alpha = 1f - dragRatio
                                }
                                .testTag("book_detail_list_${openBook.id}"),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "book_detail_hero_${openBook.id}") {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.34f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sharedBounds(
                                            sharedContentState = rememberSharedContentState(key = "book_card_${openBook.id}"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(13.dp))
                                                    .background(accent.copy(alpha = 0.16f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.MenuBook,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = openBook.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = buildString {
                                                        append("共 ${bookTxList.size} 笔账单")
                                                        if (openBook.description.isNotBlank()) {
                                                            append(" · ${openBook.description}")
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = { editingBook = openBook },
                                                modifier = Modifier.testTag("detail_edit_book_${openBook.id}")
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "编辑账本", modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        text = formatCurrency(expenseCents, baseCurrency),
                                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                        color = ExpenseCoral,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("收入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        text = formatCurrency(incomeCents, baseCurrency),
                                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                        color = IncomeJade,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text("结余", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        text = (if (netCents < 0L) "-" else "") + formatCurrency(kotlin.math.abs(netCents), baseCurrency),
                                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                        color = if (netCents >= 0L) IncomeJade else ExpenseCoral,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                enabled = bookTxList.isNotEmpty(),
                                                onClick = {
                                                    val target = if (hasAnyDetailFilter) filteredBookTxs else bookTxList
                                                    val report = viewModel.buildQuickSelectionReport(
                                                        transactions = target,
                                                        scopeTitle = "账本「${openBook.name}」统计摘要"
                                                    )
                                                    quickSummaryBook = "账本「${openBook.name}」" to report
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("detail_quick_summary_book_${openBook.id}")
                                            ) {
                                                Text("文本统计", maxLines = 1)
                                            }
                                            OutlinedButton(
                                                enabled = bookTxList.isNotEmpty(),
                                                onClick = {
                                                    val target = if (hasAnyDetailFilter) filteredBookTxs else bookTxList
                                                    exportBookPayload = Triple(
                                                        "账本「${openBook.name}」",
                                                        target,
                                                        openBook.name
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .weight(0.85f)
                                                    .testTag("detail_export_book_${openBook.id}")
                                            ) {
                                                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("导出", maxLines = 1)
                                            }
                                            FilledTonalButton(
                                                onClick = { onAnalyzeBook(openBook.id) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .weight(1.1f)
                                                    .testTag("detail_analyze_book_${openBook.id}")
                                            ) {
                                                Icon(AppIcons.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("收支分析", maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }

                            // Dismissible contextual tip below the hero card
                            item(key = "book_detail_hint_below_card") {
                                FeatureTipBanner(
                                    tipId = FeatureTipsCatalog.BOOK_DETAIL_GESTURE,
                                    text = if (canSwipeToAnalytics) {
                                        "右滑或顶部下拉返回账本主页，左滑分析本账本；长按账单可批量移出或修改。"
                                    } else {
                                        "右滑或顶部下拉返回账本主页；长按账单可多选批量移出、修改或删除。"
                                    },
                                    appearance = appearance,
                                    onDismiss = { viewModel.dismissFeatureTip(it) },
                                    accentColor = accent,
                                    modifier = Modifier.testTag("book_detail_gesture_hint")
                                )
                            }

                            if (bookTxList.isNotEmpty()) {
                                stickyHeader(key = "book_detail_search_and_filters") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.background,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("book_detail_sticky_search")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 2.dp, bottom = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = detailSearchQuery,
                                                onValueChange = { detailSearchQuery = it },
                                                placeholder = { Text("搜索本账本") },
                                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                                trailingIcon = {
                                                    if (detailSearchQuery.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = { detailSearchQuery = "" },
                                                            modifier = Modifier.testTag("book_detail_search_clear")
                                                        ) {
                                                            Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                                                        }
                                                    }
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("book_detail_search_input")
                                            )

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilterChip(
                                                    selected = detailShowAdvancedFilters || detailActiveRangeFilterCount > 0,
                                                    onClick = { detailShowAdvancedFilters = !detailShowAdvancedFilters },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = AppIcons.Tune,
                                                            contentDescription = "时间与金额范围筛选",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            if (detailActiveRangeFilterCount > 0) "范围($detailActiveRangeFilterCount)" else "日期/时段/金额"
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("book_detail_range_filter_toggle")
                                                )
                                                detailFilterOptions.forEach { option ->
                                                    FilterChip(
                                                        selected = detailSelectedFilter == option,
                                                        onClick = { detailSelectedFilter = option },
                                                        label = { Text(option) },
                                                        modifier = Modifier.testTag("book_detail_filter_$option")
                                                    )
                                                }
                                            }

                                            AnimatedVisibility(visible = detailShowAdvancedFilters) {
                                                HomeRangeFilterPanel(
                                                    baseCurrencyCode = baseCurrency,
                                                    listTimeDimension = detailTimeDimension,
                                                    customStartDateText = detailStartDateText,
                                                    customEndDateText = detailEndDateText,
                                                    selectedTimeOfDaySlot = detailTimeOfDaySlot,
                                                    customStartTimeText = detailStartTimeText,
                                                    customEndTimeText = detailEndTimeText,
                                                    minAmountInput = detailMinAmountInput,
                                                    maxAmountInput = detailMaxAmountInput,
                                                    onSelectTimeDimension = { dim ->
                                                        detailTimeDimension = dim
                                                        detailStartDateText = ""
                                                        detailEndDateText = ""
                                                    },
                                                    onStartDateChange = {
                                                        detailStartDateText = it
                                                        if (it.isNotBlank()) detailTimeDimension = HomeStatsDimension.ALL_TIME
                                                    },
                                                    onEndDateChange = {
                                                        detailEndDateText = it
                                                        if (it.isNotBlank()) detailTimeDimension = HomeStatsDimension.ALL_TIME
                                                    },
                                                    onPickStartDate = { detailPickingDateTarget = "start" },
                                                    onPickEndDate = { detailPickingDateTarget = "end" },
                                                    onSelectTimeOfDaySlot = { slot ->
                                                        detailTimeOfDaySlot = slot
                                                        detailStartTimeText = ""
                                                        detailEndTimeText = ""
                                                    },
                                                    onStartTimeChange = {
                                                        detailStartTimeText = it
                                                        if (it.isNotBlank()) detailTimeOfDaySlot = TimeOfDaySlot.ALL
                                                    },
                                                    onEndTimeChange = {
                                                        detailEndTimeText = it
                                                        if (it.isNotBlank()) detailTimeOfDaySlot = TimeOfDaySlot.ALL
                                                    },
                                                    onMinAmountChange = { detailMinAmountInput = it },
                                                    onMaxAmountChange = { detailMaxAmountInput = it },
                                                    onResetAll = {
                                                        detailTimeDimension = HomeStatsDimension.ALL_TIME
                                                        detailStartDateText = ""
                                                        detailEndDateText = ""
                                                        detailTimeOfDaySlot = TimeOfDaySlot.ALL
                                                        detailStartTimeText = ""
                                                        detailEndTimeText = ""
                                                        detailMinAmountInput = ""
                                                        detailMaxAmountInput = ""
                                                    },
                                                    appearance = appearance,
                                                    onDismissTip = { viewModel.dismissFeatureTip(it) }
                                                )
                                            }

                                            if (hasAnyDetailFilter) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "命中 ${filteredBookTxs.size} / ${bookTxList.size} 笔",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        if (filteredBookTxs.isNotEmpty()) {
                                                            TextButton(
                                                                onClick = {
                                                                    val report = viewModel.buildQuickSelectionReport(
                                                                        transactions = filteredBookTxs,
                                                                        scopeTitle = "账本「${openBook.name}」筛选结果 (${filteredBookTxs.size} 笔)"
                                                                    )
                                                                    quickSummaryBook = "账本筛选分析" to report
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.testTag("book_detail_filtered_analyze")
                                                            ) {
                                                                Text("快速分析")
                                                            }
                                                            TextButton(
                                                                onClick = {
                                                                    exportBookPayload = Triple(
                                                                        "账本「${openBook.name}」筛选结果",
                                                                        filteredBookTxs,
                                                                        openBook.name
                                                                    )
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                modifier = Modifier.testTag("book_detail_filtered_export")
                                                            ) {
                                                                Text("导出")
                                                            }
                                                        }
                                                        TextButton(
                                                            onClick = {
                                                                detailSearchQuery = ""
                                                                detailSelectedFilter = "全部"
                                                                detailTimeDimension = HomeStatsDimension.ALL_TIME
                                                                detailStartDateText = ""
                                                                detailEndDateText = ""
                                                                detailTimeOfDaySlot = TimeOfDaySlot.ALL
                                                                detailStartTimeText = ""
                                                                detailEndTimeText = ""
                                                                detailMinAmountInput = ""
                                                                detailMaxAmountInput = ""
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("重置")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (bookTxList.isEmpty()) {
                                item(key = "book_detail_empty") {
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "🤗",
                                                fontSize = 68.sp,
                                                lineHeight = 76.sp,
                                                modifier = Modifier.testTag("book_detail_empty_emoji")
                                            )
                                            Text("该账本暂无明细", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            if (appearance.isFeatureTipVisible(FeatureTipsCatalog.BOOK_DETAIL_GESTURE)) {
                                                Text(
                                                    text = "可在主页长按选中账单「加入账本」，或在账单详情页归入此账本。",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (filteredBookTxs.isEmpty()) {
                                item(key = "book_detail_filter_empty") {
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "🤗",
                                                fontSize = 60.sp,
                                                lineHeight = 68.sp,
                                                modifier = Modifier.testTag("book_detail_filter_empty_emoji")
                                            )
                                            Text("未匹配到符合条件的账单", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            if (appearance.isFeatureTipVisible(FeatureTipsCatalog.BOOK_DETAIL_GESTURE)) {
                                                Text(
                                                    text = "请调整搜索关键词、日期时段或金额范围",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = filteredBookTxs,
                                    key = { it.id },
                                    contentType = { "book_detail_tx" }
                                ) { tx ->
                                    val isSelected = tx.id in detailSelectedIds
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        border = if (isSelected) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (isDetailSelectionMode) {
                                                        detailSelectedIds = if (isSelected) {
                                                            detailSelectedIds - tx.id
                                                        } else {
                                                            detailSelectedIds + tx.id
                                                        }
                                                    } else {
                                                        inspectingTx = tx
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    detailSelectedIds = if (isSelected) {
                                                        detailSelectedIds - tx.id
                                                    } else {
                                                        detailSelectedIds + tx.id
                                                    }
                                                }
                                            )
                                            .testTag("book_detail_tx_${tx.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (isDetailSelectionMode) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = "已选中",
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                CategoryIconAvatar(
                                                    category = tx.category,
                                                    tintColor = transactionTypeColor(tx.txType),
                                                    visualStyle = IconVisualStyle.FILLED,
                                                    size = 38.dp
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(
                                                    text = tx.merchant,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    ChannelBadge(channel = tx.channel)
                                                    val bookTxDisplayCategory = CategoryCatalog.resolveDisplayCategory(
                                                        tx.category,
                                                        settings.categoryParentMap,
                                                        settings.categoryShowChildren
                                                    )
                                                    Text(
                                                        text = "$bookTxDisplayCategory · ${formatDateHeader(tx.occurredAtMillis)} ${formatItemTimeCompact(tx.occurredAtMillis)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                color = transactionTypeColor(tx.txType),
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (!isDetailSelectionMode) {
                                                IconButton(
                                                    onClick = { viewModel.removeTransactionsFromBook(listOf(tx.id), openBook.id) },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("remove_tx_from_book_${tx.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "从账本移出",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "book_detail_bottom_spacer") {
                                Spacer(modifier = Modifier.height(if (isDetailSelectionMode) 176.dp else 64.dp))
                            }
                        }

                        // Floating multi-select batch action bar inside book detail
                        AnimatedVisibility(
                            visible = isDetailSelectionMode,
                            enter = fadeIn(tween(160)) + slideInVertically { it / 2 },
                            exit = fadeOut(tween(140)) + slideOutVertically { it / 2 },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .widthIn(max = 680.dp)
                        ) {
                            val allDetailFilteredIds = remember(filteredBookTxs) {
                                filteredBookTxs.map { it.id }.toSet()
                            }
                            val allDetailSelected = allDetailFilteredIds.isNotEmpty() &&
                                allDetailFilteredIds.all { it in detailSelectedIds }
                            BatchSelectionActionBar(
                                selectedCount = detailSelectedIds.size,
                                allVisibleSelected = allDetailSelected,
                                onToggleSelectAll = {
                                    detailSelectedIds = if (allDetailSelected) {
                                        detailSelectedIds - allDetailFilteredIds
                                    } else {
                                        detailSelectedIds + allDetailFilteredIds
                                    }
                                },
                                onInvertSelection = {
                                    detailSelectedIds = (detailSelectedIds - allDetailFilteredIds) +
                                        (allDetailFilteredIds - detailSelectedIds)
                                },
                                onClearSelection = { detailSelectedIds = emptySet() },
                                onQuickAnalyze = {
                                    val picked = filteredBookTxs.filter { it.id in detailSelectedIds }
                                    if (picked.isNotEmpty()) {
                                        val report = viewModel.buildQuickSelectionReport(
                                            transactions = picked,
                                            scopeTitle = "账本「${openBook.name}」选中 ${picked.size} 笔快速分析"
                                        )
                                        quickSummaryBook = "选中账单快速分析" to report
                                    }
                                },
                                onRemoveFromBook = {
                                    val idsToRemove = detailSelectedIds
                                    detailSelectedIds = emptySet()
                                    viewModel.removeTransactionsFromBook(idsToRemove, openBook.id)
                                },
                                onAddToBooks = { showDetailAddToOtherBooks = true },
                                onBatchEdit = { showDetailBatchEdit = true },
                                onBatchDelete = { showDetailBatchDeleteConfirm = true }
                            )
                        }

                        // Pull-down visual feedback pill when pulling at top
                        AnimatedVisibility(
                            visible = pullDownOffsetPx > 12f,
                            enter = fadeIn(tween(120)),
                            exit = fadeOut(tween(120)),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                        ) {
                            val ready = pullDownOffsetPx >= pullDownTriggerPx
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    text = if (ready) "松手返回账本主页" else "继续下拉返回账本主页",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (detailPickingDateTarget != null) {
                            val datePickerState = rememberDatePickerState()
                            DatePickerDialog(
                                onDismissRequest = { detailPickingDateTarget = null },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val picked = datePickerState.selectedDateMillis
                                            if (picked != null) {
                                                val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(picked))
                                                if (detailPickingDateTarget == "start") {
                                                    detailStartDateText = formatted
                                                } else {
                                                    detailEndDateText = formatted
                                                }
                                                detailTimeDimension = HomeStatsDimension.ALL_TIME
                                            }
                                            detailPickingDateTarget = null
                                        }
                                    ) {
                                        Text("确定")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { detailPickingDateTarget = null }) { Text("取消") }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }

                        if (showDetailBatchEdit && detailSelectedIds.isNotEmpty()) {
                            BatchEditTransactionsDialog(
                                selectedCount = detailSelectedIds.size,
                                expenseCategories = CategoryCatalog.activeExpenseCategories(settings),
                                incomeCategories = CategoryCatalog.activeIncomeCategories(settings),
                                transferCategories = CategoryCatalog.activeTransferCategories(settings),
                                onDismiss = { showDetailBatchEdit = false },
                                onConfirm = { type, category, channel, merchant, currencyCode, note ->
                                    val ids = detailSelectedIds
                                    showDetailBatchEdit = false
                                    detailSelectedIds = emptySet()
                                    viewModel.batchUpdateTransactions(
                                        txIds = ids,
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

                        if (showDetailAddToOtherBooks && detailSelectedIds.isNotEmpty()) {
                            AddToBooksMultiSelectDialog(
                                selectedTxCount = detailSelectedIds.size,
                                books = books,
                                preselectedBookIds = setOf(openBook.id),
                                onCreateBookQuick = { name, onCreated ->
                                    viewModel.saveBook(name = name, onSaved = onCreated)
                                },
                                onDismiss = { showDetailAddToOtherBooks = false },
                                onConfirm = { chosenBookIds ->
                                    val ids = detailSelectedIds
                                    showDetailAddToOtherBooks = false
                                    detailSelectedIds = emptySet()
                                    viewModel.addTransactionsToBooks(ids, chosenBookIds)
                                }
                            )
                        }

                        if (showDetailBatchDeleteConfirm && detailSelectedIds.isNotEmpty()) {
                            val count = detailSelectedIds.size
                            AlertDialog(
                                onDismissRequest = { showDetailBatchDeleteConfirm = false },
                                title = { Text("删除选中的 $count 笔账单？") },
                                text = { Text("将从全部账单及所属账本中删除这 $count 笔记录，删除后可点击底部「撤回」恢复。") },
                                dismissButton = {
                                    TextButton(onClick = { showDetailBatchDeleteConfirm = false }) { Text("取消") }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val ids = detailSelectedIds
                                            showDetailBatchDeleteConfirm = false
                                            detailSelectedIds = emptySet()
                                            viewModel.deleteTransactions(ids)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral),
                                        modifier = Modifier.testTag("book_detail_confirm_batch_delete")
                                    ) {
                                        Text("确认删除")
                                    }
                                }
                            )
                        }
                    }
                } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "books_overview_header") {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "我的账本 (${books.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showCreateBookDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("create_book_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("新建账本")
                                }
                            }
                        }
                    }

                    item(key = "books_overview_tip") {
                        FeatureTipBanner(
                            tipId = FeatureTipsCatalog.BOOKS_OVERVIEW,
                            text = "点击任意账本进入明细列表，在明细页可直接横滑进入该账本的收支分析。",
                            appearance = appearance,
                            onDismiss = { viewModel.dismissFeatureTip(it) }
                        )
                    }

                    if (books.isEmpty()) {
                        item(key = "books_empty_state") {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("books_empty_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "🤗",
                                        fontSize = 72.sp,
                                        lineHeight = 80.sp,
                                        modifier = Modifier.testTag("books_empty_emoji")
                                    )
                                    Text("还没有创建账本", style = MaterialTheme.typography.titleMedium)
                                    if (appearance.isFeatureTipVisible(FeatureTipsCatalog.BOOKS_OVERVIEW)) {
                                        Text(
                                            text = "新建账本后，可在主页长按选中账单「加入账本」，或在账单详情页添加。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { showCreateBookDialog = true },
                                        modifier = Modifier.testTag("books_empty_create_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("创建第一个账本")
                                    }
                                }
                            }
                        }
                    } else {
                        items(books, key = { it.id }) { book ->
                            val bookTxList = remember(txIdsByBook, book.id, txById) {
                                txIdsByBook[book.id].orEmpty()
                                    .mapNotNull { txById[it] }
                                    .sortedByDescending { it.occurredAtMillis }
                            }
                            val confirmedBookTxs = remember(bookTxList) { bookTxList.filter { it.confirmed } }
                            val expenseCents = remember(confirmedBookTxs, baseCurrency, exchangeRates.ratesToCny) {
                                confirmedBookTxs.filter { it.txType == TransactionType.EXPENSE }.sumOf { toBase(it) }
                            }
                            val incomeCents = remember(confirmedBookTxs, baseCurrency, exchangeRates.ratesToCny) {
                                confirmedBookTxs.filter { it.txType == TransactionType.INCOME }.sumOf { toBase(it) }
                            }
                            val netCents = incomeCents - expenseCents
                            val accent = Color(book.colorArgb.toInt())

                            Card(
                                onClick = { onOpenBook(book.id) },
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "book_card_${book.id}"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                    )
                                    .testTag("book_card_${book.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(accent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.MenuBook,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = book.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = buildString {
                                                    append("${bookTxList.size} 笔账单")
                                                    if (book.description.isNotBlank()) {
                                                        append(" · ${book.description}")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { editingBook = book },
                                            modifier = Modifier.testTag("edit_book_${book.id}")
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "编辑账本", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { deletingBook = book },
                                            modifier = Modifier.testTag("delete_book_${book.id}")
                                        ) {
                                            Icon(
                                                AppIcons.DeleteOutline,
                                                contentDescription = "删除账本",
                                                tint = ExpenseCoral,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = formatCurrency(expenseCents, baseCurrency),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                    color = ExpenseCoral,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("收入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = formatCurrency(incomeCents, baseCurrency),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                    color = IncomeJade,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text("结余", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = (if (netCents < 0L) "-" else "") + formatCurrency(kotlin.math.abs(netCents), baseCurrency),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                                    color = if (netCents >= 0L) IncomeJade else ExpenseCoral,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            enabled = bookTxList.isNotEmpty(),
                                            onClick = {
                                                val report = viewModel.buildQuickSelectionReport(
                                                    transactions = bookTxList,
                                                    scopeTitle = "账本「${book.name}」统计摘要"
                                                )
                                                quickSummaryBook = "账本「${book.name}」" to report
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("quick_summary_book_${book.id}")
                                        ) {
                                            Text("文本统计", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            enabled = bookTxList.isNotEmpty(),
                                            onClick = {
                                                exportBookPayload = Triple(
                                                    "账本「${book.name}」",
                                                    bookTxList,
                                                    book.name
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(0.8f)
                                                .testTag("export_book_${book.id}")
                                        ) {
                                            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("导出", maxLines = 1)
                                        }
                                        FilledTonalButton(
                                            onClick = { onAnalyzeBook(book.id) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1.05f)
                                                .testTag("analyze_book_${book.id}")
                                        ) {
                                            Icon(AppIcons.Insights, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("图表分析", maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "books_list_bottom_spacer") {
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
            }
        }
        }
    }

    if (showCreateBookDialog) {
        BookEditorDialog(
            initialBook = null,
            onDismiss = { showCreateBookDialog = false },
            onSave = { name, desc, colorArgb ->
                viewModel.saveBook(name = name, description = desc, colorArgb = colorArgb)
                showCreateBookDialog = false
            }
        )
    }

    editingBook?.let { target ->
        BookEditorDialog(
            initialBook = target,
            onDismiss = { editingBook = null },
            onSave = { name, desc, colorArgb ->
                viewModel.saveBook(
                    name = name,
                    description = desc,
                    colorArgb = colorArgb,
                    existingBookId = target.id
                )
                editingBook = null
            }
        )
    }

    deletingBook?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingBook = null },
            title = { Text("删除账本「${target.name}」") },
            text = { Text("仅删除此账本分组与关联关系，原本的账单记录不会被删除。") },
            dismissButton = {
                TextButton(onClick = { deletingBook = null }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBook(target.id)
                        deletingBook = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseCoral)
                ) {
                    Text("确认删除")
                }
            }
        )
    }

    quickSummaryBook?.let { (title, report) ->
        QuickAnalysisTextDialog(
            title = title,
            summaryText = report.plainText,
            report = report,
            onDismiss = { quickSummaryBook = null }
        )
    }

    exportBookPayload?.let { (scopeTitle, targetList, bookName) ->
        LedgerExportDialog(
            scopeTitle = scopeTitle,
            transactions = targetList,
            baseCurrencyCode = baseCurrency,
            ratesToCny = exchangeRates.ratesToCny,
            bookName = bookName,
            onDismiss = { exportBookPayload = null },
            onExportFeedback = { msg -> viewModel.postActionMessage(msg) }
        )
    }

    inspectingTx?.let { tx ->
        val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
        TransactionDetailSheet(
            transaction = tx,
            baseCurrencyCode = baseCurrency,
            liveRateToBase = viewModel.liveRateToBase(tx),
            liveConvertedBaseCents = viewModel.convertToBaseCents(tx),
            recordedBaseCurrencyCode = recordedBase,
            recordedRateToBase = recordedRate,
            recordedConvertedBaseCents = recordedCents,
            onDismiss = { inspectingTx = null },
            onMarkNonBill = if (!tx.confirmed || !tx.rawNotification.isNullOrBlank()) {
                {
                    viewModel.markTransactionAsNonBill(tx)
                    inspectingTx = null
                }
            } else null,
            books = books,
            assignedBookIds = bookIdsByTx[tx.id].orEmpty(),
            onUpdateTransactionBooks = { chosen ->
                viewModel.setBooksForTransaction(tx.id, chosen)
            },
            onCreateBookQuick = { name, onCreated ->
                viewModel.saveBook(name = name, onSaved = onCreated)
            },
            onEdit = {
                editingTx = tx
                inspectingTx = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.id)
                inspectingTx = null
            }
        )
    }

    editingTx?.let { tx ->
        var extTradeType: String? = tx.tradeType
        var extProductName: String? = tx.productName
        var extTradeStatus: String? = tx.tradeStatus
        var extTxNo: String? = tx.transactionNo
        var extMerchantOrderNo: String? = tx.merchantOrderNo
        var extCustomFields: Map<String, String> = tx.customFields
        TransactionEditorDialog(
            initialTransaction = tx,
            defaultCurrencyCode = baseCurrency,
            enabledCurrencyCodes = settings.enabledCurrencyCodes,
            expenseCategories = CategoryCatalog.activeExpenseCategories(settings),
            incomeCategories = CategoryCatalog.activeIncomeCategories(settings),
            transferCategories = CategoryCatalog.activeTransferCategories(settings),
            channelOptions = ChannelCatalog.activeChannels(settings),
            allTransactions = allTransactions,
            onDismiss = { editingTx = null },
            onSaveExtended = { tt, pn, ts, tno, mno ->
                extTradeType = tt
                extProductName = pn
                extTradeStatus = ts
                extTxNo = tno
                extMerchantOrderNo = mno
            },
            onSaveCustomFields = { extCustomFields = it },
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
                    updateExtendedFields = true,
                    customFields = extCustomFields
                )
                editingTx = null
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookEditorDialog(
    initialBook: LedgerBook?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, colorArgb: Long) -> Unit
) {
    var name by remember(initialBook?.id) { mutableStateOf(initialBook?.name ?: "") }
    var description by remember(initialBook?.id) { mutableStateOf(initialBook?.description ?: "") }
    var colorArgb by remember(initialBook?.id) { mutableLongStateOf(initialBook?.colorArgb ?: bookColorPresets.first()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialBook == null) "新建账本" else "编辑账本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); errorText = null },
                    label = { Text("账本名称") },
                    placeholder = { Text("例如：日本旅行、家庭日常、公司报销") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_editor_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(120) },
                    label = { Text("备注说明（可选）") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("book_editor_desc_input")
                )
                Text("标识颜色", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bookColorPresets.forEach { preset ->
                        val selected = preset == colorArgb
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(preset.toInt()))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorArgb = preset }
                        )
                    }
                }
                if (errorText != null) {
                    Text(errorText!!, color = ExpenseCoral, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorText = "请输入账本名称"
                        return@Button
                    }
                    onSave(name.trim(), description.trim(), colorArgb)
                },
                modifier = Modifier.testTag("book_editor_save_button")
            ) {
                Text("保存")
            }
        }
    )
}

@Composable
fun AddToBooksMultiSelectDialog(
    selectedTxCount: Int,
    books: List<LedgerBook>,
    preselectedBookIds: Set<String>,
    onCreateBookQuick: (name: String, onCreated: (LedgerBook) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (selectedBookIds: Set<String>) -> Unit,
    allowEmptySelection: Boolean = false,
    titleText: String? = null,
    confirmButtonLabel: ((Int) -> String)? = null
) {
    var checkedBookIds by remember(preselectedBookIds) { mutableStateOf(preselectedBookIds) }
    var showQuickCreate by remember { mutableStateOf(false) }
    var newBookName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titleText ?: "加入账本（已选 $selectedTxCount 笔）")
                Text(
                    text = "可多选目标账本，支持单笔账单归属多个账本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (books.isEmpty()) {
                    Text(
                        text = "当前还没有账本，请在下方快速新建一个账本。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allIds = books.mapTo(LinkedHashSet()) { it.id }
                        val allSelected = checkedBookIds.containsAll(allIds)
                        TextButton(
                            onClick = {
                                checkedBookIds = if (allSelected) emptySet() else allIds
                            },
                            modifier = Modifier.testTag("add_to_book_select_all_button")
                        ) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                        TextButton(
                            onClick = {
                                checkedBookIds = (allIds - checkedBookIds).toSet()
                            },
                            modifier = Modifier.testTag("add_to_book_invert_button")
                        ) {
                            Text("反选")
                        }
                    }
                    books.forEach { book ->
                        val checked = book.id in checkedBookIds
                        val accent = Color(book.colorArgb.toInt())
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (checked) 1.5.dp else 1.dp,
                                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    checkedBookIds = if (checked) checkedBookIds - book.id else checkedBookIds + book.id
                                }
                                .testTag("add_to_book_option_${book.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        checkedBookIds = if (checked) checkedBookIds - book.id else checkedBookIds + book.id
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(book.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    if (book.description.isNotBlank()) {
                                        Text(
                                            text = book.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                if (!showQuickCreate && books.isNotEmpty()) {
                    TextButton(
                        onClick = { showQuickCreate = true },
                        modifier = Modifier.testTag("add_to_book_quick_create_toggle")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建账本")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newBookName,
                            onValueChange = { newBookName = it.take(40) },
                            label = { Text("新账本名称") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_to_book_quick_create_input")
                        )
                        FilledTonalButton(
                            enabled = newBookName.trim().isNotEmpty(),
                            onClick = {
                                val target = newBookName.trim()
                                newBookName = ""
                                onCreateBookQuick(target) { created ->
                                    checkedBookIds = checkedBookIds + created.id
                                    showQuickCreate = false
                                }
                            },
                            modifier = Modifier.testTag("add_to_book_quick_create_confirm")
                        ) {
                            Text("添加")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Button(
                enabled = allowEmptySelection || checkedBookIds.isNotEmpty(),
                onClick = { onConfirm(checkedBookIds) },
                modifier = Modifier.testTag("add_to_book_confirm_button")
            ) {
                Text(confirmButtonLabel?.invoke(checkedBookIds.size) ?: "加入 (${checkedBookIds.size})")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAnalysisTextDialog(
    title: String,
    summaryText: String,
    onDismiss: () -> Unit,
    report: QuickAnalysisReport? = null
) {
    val clipboard = LocalClipboardManager.current
    val copyPlainText = report?.plainText ?: summaryText
    var copied by remember(copyPlainText) { mutableStateOf(false) }
    var showPlainTextPreview by remember(copyPlainText) { mutableStateOf(report == null) }
    val numberFont = LocalNumberFontFamily.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .testTag("quick_analysis_dialog_root")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header banner with subtle theme-aligned gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
                                )
                            )
                        )
                        .padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Insights,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (report != null) {
                                    Text(
                                        text = report.scopeTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        if (report != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickAnalysisMetaPill(text = report.dateRange)
                                QuickAnalysisMetaPill(text = "共 ${report.totalCount} 笔")
                                if (report.expenseCount > 0) {
                                    QuickAnalysisMetaPill(text = "支出 ${report.expenseCount}")
                                }
                                if (report.incomeCount > 0) {
                                    QuickAnalysisMetaPill(text = "收入 ${report.incomeCount}")
                                }
                                if (report.transferCount > 0) {
                                    QuickAnalysisMetaPill(text = "转账 ${report.transferCount}")
                                }
                                if (report.foreignCount > 0) {
                                    QuickAnalysisMetaPill(text = "含 ${report.foreignCount} 笔外币")
                                }
                            }
                        }
                    }
                }

                // Scrollable structured analysis body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (report != null) {
                        // 3-column KPI cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickAnalysisKpiCard(
                                label = "支出合计",
                                value = formatCurrency(report.totalExpenseCents, report.baseCurrencyCode),
                                valueColor = ExpenseCoral,
                                modifier = Modifier.weight(1f)
                            )
                            QuickAnalysisKpiCard(
                                label = "收入合计",
                                value = formatCurrency(report.totalIncomeCents, report.baseCurrencyCode),
                                valueColor = IncomeJade,
                                modifier = Modifier.weight(1f)
                            )
                            QuickAnalysisKpiCard(
                                label = "净收支",
                                value = (if (report.netCents >= 0L) "+" else "-") +
                                    formatCurrency(kotlin.math.abs(report.netCents), report.baseCurrencyCode),
                                valueColor = if (report.netCents >= 0L) IncomeJade else ExpenseCoral,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Secondary metric strip
                        if (report.expenseCount > 0 || report.transferCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (report.expenseCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "笔均支出",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatCurrency(report.avgExpenseCents, report.baseCurrencyCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (report.transferCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "转账/不计收支",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatCurrency(report.totalTransferCents, report.baseCurrencyCode),
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "统计主币种",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = report.baseCurrencyCode,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Largest expense / income highlights
                        if (report.largestExpense != null || report.largestIncome != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "单笔极值",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    report.largestExpense?.let { peak ->
                                        QuickAnalysisHighlightRow(
                                            tag = "最高支出",
                                            accent = ExpenseCoral,
                                            merchant = peak.merchant,
                                            dateText = peak.dateText,
                                            amountText = formatCurrency(peak.amountCents, report.baseCurrencyCode)
                                        )
                                    }
                                    if (report.largestExpense != null && report.largestIncome != null) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    }
                                    report.largestIncome?.let { peak ->
                                        QuickAnalysisHighlightRow(
                                            tag = "最高收入",
                                            accent = IncomeJade,
                                            merchant = peak.merchant,
                                            dateText = peak.dateText,
                                            amountText = formatCurrency(peak.amountCents, report.baseCurrencyCode)
                                        )
                                    }
                                }
                            }
                        }

                        // Category breakdown bars
                        if (report.topCategories.isNotEmpty()) {
                            QuickAnalysisBreakdownCard(
                                sectionTitle = "分类支出分布",
                                subtitle = null,
                                items = report.topCategories,
                                baseCurrencyCode = report.baseCurrencyCode,
                                barColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Channel breakdown bars
                        if (report.topChannels.isNotEmpty()) {
                            QuickAnalysisBreakdownCard(
                                sectionTitle = "渠道支出分布",
                                subtitle = if (report.hasCompositeChannels) "组合支付渠道已按 & 等分隔符分别统计" else null,
                                items = report.topChannels,
                                baseCurrencyCode = report.baseCurrencyCode,
                                barColor = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        // Toggleable raw plain text preview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "复制后仍为纯文本摘要，便于直接粘贴分享",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showPlainTextPreview = !showPlainTextPreview },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("quick_analysis_toggle_plaintext")
                            ) {
                                Text(if (showPlainTextPreview) "收起纯文本" else "查看纯文本")
                            }
                        }
                    }

                    AnimatedVisibility(visible = showPlainTextPreview) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_analysis_dialog_text")
                        ) {
                            SelectionContainer {
                                Text(
                                    text = copyPlainText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Bottom actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(copyPlainText))
                            copied = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("quick_analysis_copy_button")
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else AppIcons.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "已复制纯文本" else "复制纯文本")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("quick_analysis_close_button")
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAnalysisMetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun QuickAnalysisKpiCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val numberFont = LocalNumberFontFamily.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickAnalysisHighlightRow(
    tag: String,
    accent: Color,
    merchant: String,
    dateText: String,
    amountText: String
) {
    val numberFont = LocalNumberFontFamily.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = 0.14f)
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = merchant,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickAnalysisBreakdownCard(
    sectionTitle: String,
    subtitle: String?,
    items: List<QuickAnalysisBreakdownItem>,
    baseCurrencyCode: String,
    barColor: Color
) {
    val numberFont = LocalNumberFontFamily.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.label} · ${item.count} 笔",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${formatCurrency(item.amountCents, baseCurrencyCode)} (${"%.1f".format(item.sharePercent)}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        val fraction = (item.sharePercent / 100.0).toFloat().coerceIn(0.03f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(barColor.copy(alpha = 0.85f))
                        )
                    }
                }
            }
        }
    }
}

