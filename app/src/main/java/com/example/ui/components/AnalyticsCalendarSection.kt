package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CurrencyExchangeManager
import com.example.model.AnalyticsGranularity
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

private val weekDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun AnalyticsCalendarSection(
    transactions: List<LedgerTransaction>,
    granularity: AnalyticsGranularity,
    anchorMillis: Long,
    baseCurrencyCode: String,
    ratesToCny: Map<String, Double>,
    onGranularityAndAnchorChange: (AnalyticsGranularity, Long) -> Unit,
    onTransactionClick: (LedgerTransaction) -> Unit,
    isFullScreen: Boolean = false,
    onRequestExitFullScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val confirmedTxs = remember(transactions) { transactions.filter { it.confirmed } }
    var pinchAccumulator by remember { mutableFloatStateOf(1f) }
    val livePinchVisualScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val numberFont = LocalNumberFontFamily.current

    fun toBaseCents(tx: LedgerTransaction): Long = CurrencyExchangeManager.convertCents(
        amountCents = tx.amountCents,
        fromCurrency = tx.currencyCode,
        toCurrency = baseCurrencyCode,
        ratesToCny = ratesToCny
    )

    fun zoomIn() {
        val next = when (granularity) {
            AnalyticsGranularity.YEAR -> AnalyticsGranularity.MONTH
            AnalyticsGranularity.MONTH -> AnalyticsGranularity.WEEK
            AnalyticsGranularity.WEEK -> AnalyticsGranularity.DAY
            AnalyticsGranularity.DAY -> AnalyticsGranularity.DAY
        }
        if (next != granularity) onGranularityAndAnchorChange(next, anchorMillis)
    }

    fun zoomOut() {
        val prev = when (granularity) {
            AnalyticsGranularity.DAY -> AnalyticsGranularity.WEEK
            AnalyticsGranularity.WEEK -> AnalyticsGranularity.MONTH
            AnalyticsGranularity.MONTH -> AnalyticsGranularity.YEAR
            AnalyticsGranularity.YEAR -> AnalyticsGranularity.YEAR
        }
        if (prev != granularity) onGranularityAndAnchorChange(prev, anchorMillis)
    }

    val calendarTitle = remember(granularity, anchorMillis) {
        when (granularity) {
            AnalyticsGranularity.YEAR -> "${Calendar.getInstance().apply { timeInMillis = anchorMillis }.get(Calendar.YEAR)} 年"
            AnalyticsGranularity.MONTH -> {
                val c = Calendar.getInstance().apply { timeInMillis = anchorMillis }
                "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月"
            }
            AnalyticsGranularity.WEEK -> LedgerViewModel.formatPeriodTitle(AnalyticsGranularity.WEEK, anchorMillis)
            AnalyticsGranularity.DAY -> LedgerViewModel.formatPeriodTitle(AnalyticsGranularity.DAY, anchorMillis)
        }
    }

    val scrollState = rememberScrollState()
    var swipeUpOverscrollPx by remember { mutableFloatStateOf(0f) }
    val swipeExitThresholdPx = with(density) { 64.dp.toPx() }

    val exitNestedScrollConnection = remember(isFullScreen, onRequestExitFullScreen) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isFullScreen || onRequestExitFullScreen == null || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                if (available.y < 0f && scrollState.value >= scrollState.maxValue) {
                    swipeUpOverscrollPx += -available.y
                    if (swipeUpOverscrollPx >= swipeExitThresholdPx) {
                        swipeUpOverscrollPx = 0f
                        onRequestExitFullScreen()
                    }
                    return available
                } else if (available.y > 0f && swipeUpOverscrollPx > 0f) {
                    swipeUpOverscrollPx = (swipeUpOverscrollPx - available.y).coerceAtLeast(0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isFullScreen && onRequestExitFullScreen != null && swipeUpOverscrollPx > swipeExitThresholdPx * 0.65f) {
                    swipeUpOverscrollPx = 0f
                    onRequestExitFullScreen()
                } else {
                    swipeUpOverscrollPx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    // Non-blocking multi-touch pinch zoom: single-finger scrolls and horizontal swipes are untouched!
    val twoFingerPinchModifier = Modifier.pointerInput(granularity, anchorMillis) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var isPinching = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) {
                    if (isPinching) {
                        pinchAccumulator = 1f
                        scope.launch { livePinchVisualScale.animateTo(1f, spring()) }
                    }
                    break
                }
                if (pressed >= 2) {
                    isPinching = true
                    val zoom = event.calculateZoom()
                    if (zoom != 1f && !zoom.isNaN()) {
                        pinchAccumulator = (pinchAccumulator * zoom).coerceIn(0.55f, 1.85f)
                        val clampedVisual = pinchAccumulator.coerceIn(0.88f, 1.14f)
                        scope.launch { livePinchVisualScale.snapTo(clampedVisual) }
                        when {
                            pinchAccumulator >= 1.24f -> {
                                pinchAccumulator = 1f
                                scope.launch { livePinchVisualScale.animateTo(1f, spring()) }
                                zoomIn()
                            }
                            pinchAccumulator <= 0.80f -> {
                                pinchAccumulator = 1f
                                scope.launch { livePinchVisualScale.animateTo(1f, spring()) }
                                zoomOut()
                            }
                        }
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

    val innerBody: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Zoom +/- + Optional Exit FullScreen Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = AppIcons.CalendarMonth,
                        contentDescription = "收支日历",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text("收支日历", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "双指缩放或点击切换 年 / 月 / 周 / 日",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { zoomOut() },
                        enabled = granularity != AnalyticsGranularity.YEAR,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("calendar_zoom_out_button")
                    ) {
                        Text("缩小", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { zoomIn() },
                        enabled = granularity != AnalyticsGranularity.DAY,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("calendar_zoom_in_button")
                    ) {
                        Text("放大", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Granularity Filter Tabs: 年 / 月 / 周 / 日
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val labels = mapOf(
                    AnalyticsGranularity.YEAR to "年",
                    AnalyticsGranularity.MONTH to "月",
                    AnalyticsGranularity.WEEK to "周",
                    AnalyticsGranularity.DAY to "日"
                )
                AnalyticsGranularity.entries.forEach { g ->
                    FilterChip(
                        selected = granularity == g,
                        onClick = { onGranularityAndAnchorChange(g, anchorMillis) },
                        label = {
                            Text(
                                text = labels[g] ?: g.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("calendar_tab_${g.name}")
                    )
                }
            }

            // Period Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        onGranularityAndAnchorChange(
                            granularity,
                            LedgerViewModel.shiftPeriodAnchor(granularity, anchorMillis, -1)
                        )
                    },
                    modifier = Modifier.testTag("calendar_prev_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周期")
                }
                Text(
                    text = calendarTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        onGranularityAndAnchorChange(
                            granularity,
                            LedgerViewModel.shiftPeriodAnchor(granularity, anchorMillis, +1)
                        )
                    },
                    modifier = Modifier.testTag("calendar_next_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周期")
                }
            }

            HorizontalDivider()

            AnimatedContent(
                targetState = granularity,
                transitionSpec = {
                    val zoomInAnim = targetState.ordinal > initialState.ordinal
                    val enterSpec = (
                        scaleIn(
                            initialScale = if (zoomInAnim) 0.88f else 1.09f,
                            animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f)
                        ) + fadeIn(tween(210))
                    )
                    val exitSpec = (
                        scaleOut(
                            targetScale = if (zoomInAnim) 1.09f else 0.88f,
                            animationSpec = tween(170)
                        ) + fadeOut(tween(150))
                    )
                    enterSpec togetherWith exitSpec
                },
                label = "calendar_granularity_zoom_anim",
                modifier = Modifier.graphicsLayer {
                    scaleX = livePinchVisualScale.value
                    scaleY = livePinchVisualScale.value
                }
            ) { activeGranularity ->
            when (activeGranularity) {
                AnalyticsGranularity.YEAR -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = anchorMillis }
                    val targetYear = cal.get(Calendar.YEAR)
                    val todayCal = Calendar.getInstance()
                    val currentYear = todayCal.get(Calendar.YEAR)
                    val currentMonth0 = todayCal.get(Calendar.MONTH)

                    val monthlyTotals = remember(confirmedTxs, targetYear, baseCurrencyCode, ratesToCny) {
                        val map = Array(12) { Triple(0L, 0L, 0) } // expense, income, count
                        val iterCal = Calendar.getInstance()
                        confirmedTxs.forEach { tx ->
                            iterCal.timeInMillis = tx.occurredAtMillis
                            if (iterCal.get(Calendar.YEAR) == targetYear) {
                                val m = iterCal.get(Calendar.MONTH)
                                val baseAmt = toBaseCents(tx)
                                val prev = map[m]
                                map[m] = when (tx.txType) {
                                    TransactionType.EXPENSE -> Triple(prev.first + baseAmt, prev.second, prev.third + 1)
                                    TransactionType.INCOME -> Triple(prev.first, prev.second + baseAmt, prev.third + 1)
                                    TransactionType.TRANSFER -> Triple(prev.first, prev.second, prev.third + 1)
                                }
                            }
                        }
                        map
                    }
                    val maxMonthExpense = remember(monthlyTotals) {
                        monthlyTotals.maxOfOrNull { it.first }?.coerceAtLeast(1L) ?: 1L
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val month0 = row * 3 + col
                                    val (exp, inc, cnt) = monthlyTotals[month0]
                                    val isNowMonth = targetYear == currentYear && month0 == currentMonth0
                                    val heatAlpha = (exp.toFloat() / maxMonthExpense.toFloat()).coerceIn(0f, 1f) * 0.16f
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (exp > 0L) ExpenseCoral.copy(alpha = 0.05f + heatAlpha)
                                        else MaterialTheme.colorScheme.surface,
                                        border = if (isNowMonth) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                val jump = Calendar.getInstance().apply {
                                                    set(targetYear, month0, 1, 12, 0, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                onGranularityAndAnchorChange(AnalyticsGranularity.MONTH, jump.timeInMillis)
                                            }
                                            .testTag("calendar_year_month_${month0 + 1}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${month0 + 1}月",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isNowMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (cnt > 0) {
                                                    Text(
                                                        text = "${cnt}笔",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (exp > 0L) "-${compactCalendarMoney(exp, baseCurrencyCode)}" else "支 ¥0",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                                                color = if (exp > 0L) ExpenseCoral else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (inc > 0L) {
                                                Text(
                                                    text = "+${compactCalendarMoney(inc, baseCurrencyCode)}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                                                    color = IncomeJade,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnalyticsGranularity.MONTH -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = anchorMillis
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val targetYear = cal.get(Calendar.YEAR)
                    val targetMonth0 = cal.get(Calendar.MONTH)
                    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val firstDayOfWeekMonBased = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) // 0=Mon..6=Sun

                    val todayCal = Calendar.getInstance()
                    val isThisMonth = todayCal.get(Calendar.YEAR) == targetYear && todayCal.get(Calendar.MONTH) == targetMonth0
                    val todayDom = todayCal.get(Calendar.DAY_OF_MONTH)

                    val dayTotals = remember(confirmedTxs, targetYear, targetMonth0, baseCurrencyCode, ratesToCny) {
                        val map = Array(daysInMonth + 1) { Triple(0L, 0L, 0) }
                        val iterCal = Calendar.getInstance()
                        confirmedTxs.forEach { tx ->
                            iterCal.timeInMillis = tx.occurredAtMillis
                            if (iterCal.get(Calendar.YEAR) == targetYear && iterCal.get(Calendar.MONTH) == targetMonth0) {
                                val dom = iterCal.get(Calendar.DAY_OF_MONTH)
                                if (dom in 1..daysInMonth) {
                                    val amt = toBaseCents(tx)
                                    val prev = map[dom]
                                    map[dom] = when (tx.txType) {
                                        TransactionType.EXPENSE -> Triple(prev.first + amt, prev.second, prev.third + 1)
                                        TransactionType.INCOME -> Triple(prev.first, prev.second + amt, prev.third + 1)
                                        TransactionType.TRANSFER -> Triple(prev.first, prev.second, prev.third + 1)
                                    }
                                }
                            }
                        }
                        map
                    }
                    val maxDayExpense = remember(dayTotals) {
                        dayTotals.maxOfOrNull { it.first }?.coerceAtLeast(1L) ?: 1L
                    }
                    val monthExpenseTotal = remember(dayTotals) { dayTotals.sumOf { it.first } }
                    val monthIncomeTotal = remember(dayTotals) { dayTotals.sumOf { it.second } }
                    val monthActiveDays = remember(dayTotals) { dayTotals.count { it.third > 0 } }

                    val totalCells = firstDayOfWeekMonBased + daysInMonth
                    val numRows = (totalCells + 6) / 7

                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "本月记账 $monthActiveDays 天",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "支 -${compactCalendarMoney(monthExpenseTotal, baseCurrencyCode)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                        color = ExpenseCoral,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "收 +${compactCalendarMoney(monthIncomeTotal, baseCurrencyCode)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                        color = IncomeJade,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Weekday Headers + Week Jump Column
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "周",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(24.dp)
                            )
                            weekDayLabels.forEach { dayLbl ->
                                Text(
                                    text = dayLbl,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        for (r in 0 until numRows) {
                            val firstValidDomInRow = (0..6)
                                .map { c -> r * 7 + c - firstDayOfWeekMonBased + 1 }
                                .firstOrNull { it in 1..daysInMonth } ?: 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Clickable Week shortcut to zoom cleanly into Week view for this row
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val jump = Calendar.getInstance().apply {
                                                set(targetYear, targetMonth0, firstValidDomInRow, 12, 0, 0)
                                            }
                                            onGranularityAndAnchorChange(AnalyticsGranularity.WEEK, jump.timeInMillis)
                                        }
                                        .testTag("calendar_month_week_${r + 1}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "W${r + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                for (c in 0 until 7) {
                                    val dom = r * 7 + c - firstDayOfWeekMonBased + 1
                                    if (dom !in 1..daysInMonth) {
                                        Spacer(modifier = Modifier.weight(1f).height(64.dp))
                                    } else {
                                        val (exp, inc, cnt) = dayTotals[dom]
                                        val isToday = isThisMonth && dom == todayDom
                                        val heat = (exp.toFloat() / maxDayExpense.toFloat()).coerceIn(0f, 1f) * 0.22f
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = when {
                                                exp > 0L -> ExpenseCoral.copy(alpha = 0.06f + heat)
                                                inc > 0L -> IncomeJade.copy(alpha = 0.12f)
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                            border = if (isToday) {
                                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                            } else null,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(64.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    val jump = Calendar.getInstance().apply {
                                                        set(targetYear, targetMonth0, dom, 12, 0, 0)
                                                    }
                                                    onGranularityAndAnchorChange(AnalyticsGranularity.DAY, jump.timeInMillis)
                                                }
                                                .testTag("calendar_month_day_$dom")
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 2.dp, vertical = 3.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = dom.toString(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isToday || cnt > 0) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                                ) {
                                                    if (exp > 0L) {
                                                        Text(
                                                            text = "-${compactCalendarShort(exp)}",
                                                            fontSize = 8.5.sp,
                                                            lineHeight = 9.5.sp,
                                                            fontFamily = numberFont,
                                                            color = ExpenseCoral,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Clip
                                                        )
                                                    }
                                                    if (inc > 0L) {
                                                        Text(
                                                            text = "+${compactCalendarShort(inc)}",
                                                            fontSize = 8.5.sp,
                                                            lineHeight = 9.5.sp,
                                                            fontFamily = numberFont,
                                                            color = IncomeJade,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Clip
                                                        )
                                                    }
                                                    if (exp == 0L && inc == 0L && cnt > 0) {
                                                        Text(
                                                            text = "${cnt}笔",
                                                            fontSize = 8.sp,
                                                            lineHeight = 9.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1
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
                }

                AnalyticsGranularity.WEEK -> {
                    val (weekStart, _) = remember(anchorMillis) { LedgerViewModel.weekRangeMillis(anchorMillis) }
                    val daysList = remember(confirmedTxs, weekStart, baseCurrencyCode, ratesToCny) {
                        val dayCal = Calendar.getInstance().apply { timeInMillis = weekStart }
                        (0 until 7).map { offset ->
                            val start = dayCal.timeInMillis
                            val labelDate = "${dayCal.get(Calendar.MONTH) + 1}月${dayCal.get(Calendar.DAY_OF_MONTH)}日"
                            val weekLabel = "周${weekDayLabels[offset]}"
                            val y = dayCal.get(Calendar.YEAR)
                            val doy = dayCal.get(Calendar.DAY_OF_YEAR)
                            val iterCal = Calendar.getInstance()
                            val dayTxs = confirmedTxs.filter { tx ->
                                iterCal.timeInMillis = tx.occurredAtMillis
                                iterCal.get(Calendar.YEAR) == y && iterCal.get(Calendar.DAY_OF_YEAR) == doy
                            }
                            val exp = dayTxs.filter { it.txType == TransactionType.EXPENSE }.sumOf { toBaseCents(it) }
                            val inc = dayTxs.filter { it.txType == TransactionType.INCOME }.sumOf { toBaseCents(it) }
                            dayCal.add(Calendar.DAY_OF_YEAR, 1)
                            WeekDaySummary(
                                dayAnchorMillis = start + 12L * 3_600_000L,
                                weekLabel = weekLabel,
                                dateLabel = labelDate,
                                expenseCents = exp,
                                incomeCents = inc,
                                count = dayTxs.size
                            )
                        }
                    }
                    val maxWeekDayExp = remember(daysList) {
                        daysList.maxOfOrNull { it.expenseCents }?.coerceAtLeast(1L) ?: 1L
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        daysList.forEachIndexed { idx, day ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onGranularityAndAnchorChange(AnalyticsGranularity.DAY, day.dayAnchorMillis)
                                    }
                                    .testTag("calendar_week_day_$idx")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${day.weekLabel} · ${day.dateLabel} (${day.count} 笔)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (day.incomeCents > 0L) {
                                                Text(
                                                    text = "+${formatCurrency(day.incomeCents, baseCurrencyCode)}",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                                    color = IncomeJade,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Text(
                                                text = "-${formatCurrency(day.expenseCents, baseCurrencyCode)}",
                                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                                color = if (day.expenseCents > 0L) ExpenseCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { (day.expenseCents.toFloat() / maxWeekDayExp.toFloat()).coerceIn(0f, 1f) },
                                        color = ExpenseCoral,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                AnalyticsGranularity.DAY -> {
                    val dayTxs = remember(confirmedTxs, anchorMillis) {
                        LedgerViewModel.filterByDay(confirmedTxs, anchorMillis)
                            .sortedByDescending { it.occurredAtMillis }
                    }
                    val dayExpense = remember(dayTxs, baseCurrencyCode, ratesToCny) {
                        dayTxs.filter { it.txType == TransactionType.EXPENSE }.sumOf { toBaseCents(it) }
                    }
                    val dayIncome = remember(dayTxs, baseCurrencyCode, ratesToCny) {
                        dayTxs.filter { it.txType == TransactionType.INCOME }.sumOf { toBaseCents(it) }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "共 ${dayTxs.size} 笔",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "支出 ${formatCurrency(dayExpense, baseCurrencyCode)} · 收入 ${formatCurrency(dayIncome, baseCurrencyCode)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = numberFont),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (dayTxs.isEmpty()) {
                            Text(
                                text = "当日暂无账单记录，可点击左上角「缩小」回到周历或月历查看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            dayTxs.forEach { tx ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onTransactionClick(tx) }
                                        .testTag("calendar_day_tx_${tx.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = formatTimeShort(tx.occurredAtMillis),
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = numberFont),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tx.merchant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${tx.category} · ${tx.channel}",
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
                                }
                            }
                        }
                    }
                }
            }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFullScreen && onRequestExitFullScreen != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onRequestExitFullScreen() }
                            .pointerInput(Unit) {
                                var dragUp = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { dragUp = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        if (dragAmount < 0f) dragUp += -dragAmount
                                    },
                                    onDragEnd = {
                                        if (dragUp > 24.dp.toPx()) onRequestExitFullScreen()
                                    }
                                )
                            }
                            .testTag("calendar_exit_fullscreen_pill")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "退出全屏",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                TextButton(
                    onClick = { onGranularityAndAnchorChange(granularity, System.currentTimeMillis()) }
                ) {
                    Text("定位到今天")
                }
            }
        }
    }

    if (isFullScreen) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(exitNestedScrollConnection)
                .then(twoFingerPinchModifier)
                .testTag("analytics_calendar_section")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                innerBody()
            }
        }
    } else {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = modifier
                .fillMaxWidth()
                .then(twoFingerPinchModifier)
                .testTag("analytics_calendar_section")
        ) {
            innerBody()
        }
    }
}

private data class WeekDaySummary(
    val dayAnchorMillis: Long,
    val weekLabel: String,
    val dateLabel: String,
    val expenseCents: Long,
    val incomeCents: Long,
    val count: Int
)

private fun compactCalendarMoney(cents: Long, currencyCode: String): String {
    val sym = CurrencyCatalog.resolve(currencyCode).symbol
    val valNum = cents / 100.0
    return if (valNum >= 10_000.0) {
        "$sym%.1f万".format(Locale.US, valNum / 10_000.0)
    } else if (cents % 100L == 0L) {
        "$sym${(cents / 100L)}"
    } else {
        "$sym%.1f".format(Locale.US, valNum)
    }
}

private fun compactCalendarShort(cents: Long): String {
    val valNum = cents / 100.0
    return when {
        valNum >= 10_000.0 -> "%.1fw".format(Locale.US, valNum / 10_000.0)
        valNum >= 100.0 || cents % 100L == 0L -> "${(cents / 100L)}"
        else -> "%.1f".format(Locale.US, valNum)
    }
}
