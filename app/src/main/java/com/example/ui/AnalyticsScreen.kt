package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.example.data.CurrencyExchangeManager
import com.example.model.AnalyticsChartStyle
import com.example.model.AnalyticsGranularity
import com.example.model.AppearanceSettings
import com.example.model.BudgetSettings
import com.example.model.ExchangeRateState
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.ui.components.AnalyticsCalendarSection
import com.example.ui.components.ChannelBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.channelBrandColor
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import kotlin.math.roundToInt

private data class ChartDatum(
    val label: String,
    val cents: Long,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
    transactionsState: UiState<List<LedgerTransaction>>,
    settings: BudgetSettings,
    appearance: AppearanceSettings = AppearanceSettings(),
    exchangeRates: ExchangeRateState,
    viewModel: LedgerViewModel,
    isCalendarFullScreen: Boolean = false,
    onCalendarFullScreenChange: (Boolean) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    var selectedGranularity by remember { mutableStateOf(AnalyticsGranularity.MONTH) }
    var anchorMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPullThresholdAdjuster by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    var livePieSizeDp by remember(appearance.analyticsPieSizeDp) {
        mutableFloatStateOf(appearance.analyticsPieSizeDp.toFloat().coerceIn(140f, 300f))
    }
    val pieSizeDp by animateDpAsState(
        targetValue = livePieSizeDp.roundToInt().coerceIn(140, 300).dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "analytics_pie_size"
    )

    BackHandler(enabled = isCalendarFullScreen) {
        onCalendarFullScreenChange(false)
    }

    var topPullDistancePx by remember { mutableFloatStateOf(0f) }
    val pullTriggerDp = appearance.analyticsPullCalendarDistanceDp.coerceIn(72, 220)
    val pullTriggerPx = with(density) { pullTriggerDp.dp.toPx() }
    val pullDownToCalendarConnection = remember(isCalendarFullScreen, pullTriggerPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (isCalendarFullScreen || source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (atTop && available.y > 0f) {
                    topPullDistancePx = (topPullDistancePx + available.y).coerceIn(0f, pullTriggerPx * 1.4f)
                    if (topPullDistancePx >= pullTriggerPx) {
                        topPullDistancePx = 0f
                        onCalendarFullScreenChange(true)
                    }
                    return available
                } else if (available.y < 0f && topPullDistancePx > 0f) {
                    topPullDistancePx = (topPullDistancePx + available.y).coerceAtLeast(0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isCalendarFullScreen && topPullDistancePx >= pullTriggerPx * 0.85f) {
                    topPullDistancePx = 0f
                    onCalendarFullScreenChange(true)
                } else {
                    topPullDistancePx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    var categoryChartStyle by remember { mutableStateOf(AnalyticsChartStyle.PIE) }
    var channelChartStyle by remember { mutableStateOf(AnalyticsChartStyle.BAR) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedChannelFilter by remember { mutableStateOf<String?>(null) }
    var inspectingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }

    val numberFont = LocalNumberFontFamily.current
    val baseCurrency = settings.baseCurrencyCode
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()

    val periodTitle = remember(selectedGranularity, anchorMillis) {
        LedgerViewModel.formatPeriodTitle(selectedGranularity, anchorMillis)
    }

    val periodTransactions by remember(allTransactions, selectedGranularity, anchorMillis) {
        derivedStateOf {
            LedgerViewModel.filterByGranularity(allTransactions, selectedGranularity, anchorMillis)
                .filter { it.confirmed }
        }
    }

    fun toBase(tx: LedgerTransaction): Long = CurrencyExchangeManager.convertCents(
        tx.amountCents,
        tx.currencyCode,
        baseCurrency,
        exchangeRates.ratesToCny
    )

    val periodExpenseTxs = remember(periodTransactions) {
        periodTransactions.filter { it.txType == TransactionType.EXPENSE }
    }

    val totalExpense = remember(periodExpenseTxs, exchangeRates.ratesToCny, baseCurrency) {
        periodExpenseTxs.sumOf { toBase(it) }
    }
    val totalIncome = remember(periodTransactions, exchangeRates.ratesToCny, baseCurrency) {
        periodTransactions.filter { it.txType == TransactionType.INCOME }.sumOf { toBase(it) }
    }

    val categoryData = remember(periodExpenseTxs, exchangeRates.ratesToCny, baseCurrency) {
        periodExpenseTxs
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { toBase(it) } }
            .entries
            .sortedByDescending { it.value }
            .mapIndexed { index, entry ->
                ChartDatum(entry.key, entry.value, ChartPalette[index % ChartPalette.size])
            }
    }

    val channelData = remember(periodExpenseTxs, exchangeRates.ratesToCny, baseCurrency) {
        periodExpenseTxs
            .groupBy { it.channel }
            .mapValues { (_, list) -> list.sumOf { toBase(it) } }
            .entries
            .sortedByDescending { it.value }
            .map { entry ->
                ChartDatum(entry.key, entry.value, channelBrandColor(entry.key))
            }
    }

    val filteredCategoryTxs = remember(periodExpenseTxs, selectedCategoryFilter) {
        selectedCategoryFilter?.let { target ->
            periodExpenseTxs.filter { it.category == target }.sortedByDescending { it.occurredAtMillis }
        } ?: emptyList()
    }

    val filteredChannelTxs = remember(periodExpenseTxs, selectedChannelFilter) {
        selectedChannelFilter?.let { target ->
            periodExpenseTxs.filter { it.channel == target }.sortedByDescending { it.occurredAtMillis }
        } ?: emptyList()
    }

    val summaryText = remember(periodTransactions, selectedGranularity, anchorMillis, exchangeRates.ratesToCny, baseCurrency) {
        viewModel.buildPeriodInsight(selectedGranularity, anchorMillis)
    }

    val summaryCardTitle = when (selectedGranularity) {
        AnalyticsGranularity.YEAR -> "年度收支分析"
        AnalyticsGranularity.MONTH -> "月度收支分析"
        AnalyticsGranularity.WEEK -> "周度收支分析"
        AnalyticsGranularity.DAY -> "日度收支分析"
    }

    val emptyPeriodMessage = when (selectedGranularity) {
        AnalyticsGranularity.YEAR -> "本年度暂无支出记录。"
        AnalyticsGranularity.MONTH -> "本月暂无支出记录。"
        AnalyticsGranularity.WEEK -> "本周暂无支出记录。"
        AnalyticsGranularity.DAY -> "当日暂无支出记录。"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullDownToCalendarConnection),
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
            if (topPullDistancePx > 4f) {
                item(key = "pull_calendar_hint") {
                    val progressRatio = (topPullDistancePx / pullTriggerPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val ready = topPullDistancePx >= pullTriggerPx * 0.85f
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (ready) {
                                        "松手进入全屏收支日历"
                                    } else {
                                        "继续下拉打开收支日历 (${(progressRatio * 100).roundToInt()}% · ${pullTriggerDp}dp)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            item(key = "period_switcher") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AnalyticsGranularity.entries.forEachIndexed { index, granularity ->
                                SegmentedButton(
                                    selected = selectedGranularity == granularity,
                                    onClick = {
                                        selectedGranularity = granularity
                                        selectedCategoryFilter = null
                                        selectedChannelFilter = null
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = AnalyticsGranularity.entries.size
                                    ),
                                    modifier = Modifier.testTag("analytics_granularity_${granularity.name}")
                                ) {
                                    Text(granularity.label, maxLines = 1)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    selectedCategoryFilter = null
                                    selectedChannelFilter = null
                                    anchorMillis = LedgerViewModel.shiftPeriodAnchor(selectedGranularity, anchorMillis, -1)
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "上一${selectedGranularity.label}"
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                CompactFullText(
                                    text = "$periodTitle 收支分析 ($baseCurrency)",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            selectedCategoryFilter = null
                                            selectedChannelFilter = null
                                            anchorMillis = System.currentTimeMillis()
                                        },
                                        label = { Text("回到今天") },
                                        modifier = Modifier.testTag("analytics_today_chip")
                                    )
                                    AssistChip(
                                        onClick = { onCalendarFullScreenChange(true) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.CalendarMonth,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("全屏日历") },
                                        modifier = Modifier.testTag("analytics_scroll_calendar_chip")
                                    )
                                    AssistChip(
                                        onClick = { showPullThresholdAdjuster = !showPullThresholdAdjuster },
                                        label = { Text("下拉 ${pullTriggerDp}dp") },
                                        modifier = Modifier.testTag("analytics_pull_distance_chip")
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    selectedCategoryFilter = null
                                    selectedChannelFilter = null
                                    anchorMillis = LedgerViewModel.shiftPeriodAnchor(selectedGranularity, anchorMillis, +1)
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "下一${selectedGranularity.label}"
                                )
                            }
                        }

                        AnimatedVisibility(visible = showPullThresholdAdjuster) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "下拉打开日历触发距离（越长越防误触）",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${pullTriggerDp} dp",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = pullTriggerDp.toFloat(),
                                    onValueChange = { raw ->
                                        val nextDp = raw.roundToInt().coerceIn(72, 220)
                                        viewModel.saveAppearanceSettings(
                                            appearance.copy(analyticsPullCalendarDistanceDp = nextDp)
                                        )
                                    },
                                    valueRange = 72f..220f,
                                    modifier = Modifier.testTag("analytics_inline_pull_distance_slider")
                                )
                            }
                        }
                    }
                }
            }

            item(key = "overview_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("总支出", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CompactFullText(
                                text = formatCurrency(totalExpense, baseCurrency),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = numberFont,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = ExpenseCoral,
                                maxLines = 2
                            )
                        }
                    }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("总收入", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CompactFullText(
                                text = formatCurrency(totalIncome, baseCurrency),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = numberFont,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = IncomeJade,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            // 1. Category Chart Card
            item(key = "category_chart") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("支出分类占比", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "点击图例或分类行可筛选条目（完整展示全部数据）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ChartStyleToggleButton(
                                style = categoryChartStyle,
                                testTag = "category_chart_style_toggle",
                                onSelect = { categoryChartStyle = it }
                            )
                        }

                        if (categoryData.isEmpty() || totalExpense <= 0L) {
                            Text(
                                text = emptyPeriodMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            if (categoryChartStyle == AnalyticsChartStyle.PIE) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(pieSizeDp)
                                            .twoFingerPinchZoom(
                                                key = "category_pie",
                                                onZoomDelta = { zoom ->
                                                    livePieSizeDp = (livePieSizeDp * zoom).coerceIn(140f, 300f)
                                                },
                                                onZoomEnd = {
                                                    val finalDp = livePieSizeDp.roundToInt().coerceIn(140, 300)
                                                    if (finalDp != appearance.analyticsPieSizeDp) {
                                                        viewModel.saveAppearanceSettings(
                                                            appearance.copy(analyticsPieSizeDp = finalDp)
                                                        )
                                                    }
                                                }
                                            )
                                            .testTag("category_pie_chart_box"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DonutPieChart(
                                            slices = categoryData,
                                            totalCents = totalExpense,
                                            selectedLabel = selectedCategoryFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 28.dp)
                                        ) {
                                            CompactFullText(
                                                text = selectedCategoryFilter ?: "共支出",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                            val centerCents = selectedCategoryFilter?.let { sel ->
                                                categoryData.firstOrNull { it.label == sel }?.cents ?: totalExpense
                                            } ?: totalExpense
                                            CompactFullText(
                                                text = formatCurrency(centerCents, baseCurrency),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = numberFont,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                        }
                                    }

                                    Text(
                                        text = "双指捏合饼图可无级调节大小 (${livePieSizeDp.roundToInt()}dp)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                        modifier = Modifier.testTag("analytics_pie_pinch_hint")
                                    )

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        categoryData.forEach { datum ->
                                            val pct = (datum.cents * 100.0 / totalExpense)
                                            val pctText = if (pct > 0.0 && pct < 1.0) "%.1f%%".format(pct) else "${pct.toInt()}%"
                                            val isSelected = selectedCategoryFilter == datum.label
                                            ClickableLegendRow(
                                                label = datum.label,
                                                color = datum.color,
                                                valueText = "$pctText · ${formatCurrency(datum.cents, baseCurrency)}",
                                                selected = isSelected,
                                                testTag = "category_legend_${datum.label}",
                                                onClick = {
                                                    selectedCategoryFilter = if (isSelected) null else datum.label
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                VerticalBarChart(
                                    items = categoryData,
                                    baseCurrencyCode = baseCurrency,
                                    selectedLabel = selectedCategoryFilter,
                                    onSelect = { label ->
                                        selectedCategoryFilter = if (selectedCategoryFilter == label) null else label
                                    }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    categoryData.forEach { datum ->
                                        val ratio = (datum.cents.toFloat() / totalExpense.toFloat()).coerceIn(0f, 1f)
                                        val pct = datum.cents * 100.0 / totalExpense
                                        val pctText = if (pct > 0.0 && pct < 1.0) "%.1f%%".format(pct) else "${pct.toInt()}%"
                                        val isSelected = selectedCategoryFilter == datum.label
                                        ClickableBreakdownRow(
                                            label = datum.label,
                                            color = datum.color,
                                            amountText = "${formatCurrency(datum.cents, baseCurrency)} ($pctText)",
                                            ratio = ratio,
                                            selected = isSelected,
                                            testTag = "category_row_${datum.label}",
                                            onClick = {
                                                selectedCategoryFilter = if (isSelected) null else datum.label
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = selectedCategoryFilter != null) {
                                FilteredTransactionsPanel(
                                    title = "分类「${selectedCategoryFilter.orEmpty()}」明细 (${filteredCategoryTxs.size} 笔)",
                                    transactions = filteredCategoryTxs,
                                    baseCurrencyCode = baseCurrency,
                                    ratesToCny = exchangeRates.ratesToCny,
                                    onClearFilter = { selectedCategoryFilter = null },
                                    onTransactionClick = { inspectingTransaction = it }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Channel Distribution Card
            item(key = "channel_breakdown") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("渠道分布", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "点击图例或渠道行可筛选条目（完整展示全部渠道）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ChartStyleToggleButton(
                                style = channelChartStyle,
                                testTag = "channel_chart_style_toggle",
                                onSelect = { channelChartStyle = it }
                            )
                        }

                        if (channelData.isEmpty() || totalExpense <= 0L) {
                            Text(
                                text = "暂无渠道数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            if (channelChartStyle == AnalyticsChartStyle.PIE) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(pieSizeDp)
                                            .twoFingerPinchZoom(
                                                key = "channel_pie",
                                                onZoomDelta = { zoom ->
                                                    livePieSizeDp = (livePieSizeDp * zoom).coerceIn(140f, 300f)
                                                },
                                                onZoomEnd = {
                                                    val finalDp = livePieSizeDp.roundToInt().coerceIn(140, 300)
                                                    if (finalDp != appearance.analyticsPieSizeDp) {
                                                        viewModel.saveAppearanceSettings(
                                                            appearance.copy(analyticsPieSizeDp = finalDp)
                                                        )
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DonutPieChart(
                                            slices = channelData,
                                            totalCents = totalExpense,
                                            selectedLabel = selectedChannelFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(horizontal = 28.dp)
                                        ) {
                                            CompactFullText(
                                                text = selectedChannelFilter ?: "渠道汇总",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                            val centerCents = selectedChannelFilter?.let { sel ->
                                                channelData.firstOrNull { it.label == sel }?.cents ?: totalExpense
                                            } ?: totalExpense
                                            CompactFullText(
                                                text = formatCurrency(centerCents, baseCurrency),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = numberFont,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        channelData.forEach { datum ->
                                            val pct = datum.cents * 100.0 / totalExpense
                                            val pctText = if (pct > 0.0 && pct < 1.0) "%.1f%%".format(pct) else "${pct.toInt()}%"
                                            val isSelected = selectedChannelFilter == datum.label
                                            ClickableLegendRow(
                                                label = datum.label,
                                                color = datum.color,
                                                valueText = "$pctText · ${formatCurrency(datum.cents, baseCurrency)}",
                                                selected = isSelected,
                                                testTag = "channel_legend_${datum.label}",
                                                onClick = {
                                                    selectedChannelFilter = if (isSelected) null else datum.label
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                VerticalBarChart(
                                    items = channelData,
                                    baseCurrencyCode = baseCurrency,
                                    selectedLabel = selectedChannelFilter,
                                    onSelect = { label ->
                                        selectedChannelFilter = if (selectedChannelFilter == label) null else label
                                    }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    channelData.forEach { datum ->
                                        val ratio = (datum.cents.toFloat() / totalExpense.toFloat()).coerceIn(0f, 1f)
                                        val pct = datum.cents * 100.0 / totalExpense
                                        val pctText = if (pct > 0.0 && pct < 1.0) "%.1f%%".format(pct) else "${pct.toInt()}%"
                                        val isSelected = selectedChannelFilter == datum.label
                                        ClickableBreakdownRow(
                                            label = datum.label,
                                            color = datum.color,
                                            amountText = "${formatCurrency(datum.cents, baseCurrency)} ($pctText)",
                                            ratio = ratio,
                                            selected = isSelected,
                                            testTag = "channel_row_${datum.label}",
                                            onClick = {
                                                selectedChannelFilter = if (isSelected) null else datum.label
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = selectedChannelFilter != null) {
                                FilteredTransactionsPanel(
                                    title = "渠道「${selectedChannelFilter.orEmpty()}」明细 (${filteredChannelTxs.size} 笔)",
                                    transactions = filteredChannelTxs,
                                    baseCurrencyCode = baseCurrency,
                                    ratesToCny = exchangeRates.ratesToCny,
                                    onClearFilter = { selectedChannelFilter = null },
                                    onTransactionClick = { inspectingTransaction = it }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "period_summary") {
                val netCents = totalIncome - totalExpense
                val topCategory = categoryData.firstOrNull()
                val topChannel = channelData.firstOrNull()
                val budgetRef = remember(settings.monthlyBudgetCents, selectedGranularity) {
                    val m = settings.monthlyBudgetCents.coerceAtLeast(0L)
                    when (selectedGranularity) {
                        AnalyticsGranularity.YEAR -> m * 12L
                        AnalyticsGranularity.MONTH -> m
                        AnalyticsGranularity.WEEK -> (m * 7L) / 30L
                        AnalyticsGranularity.DAY -> m / 30L
                    }
                }
                val budgetRatio = if (budgetRef > 0L) (totalExpense.toFloat() / budgetRef.toFloat()).coerceIn(0f, 1.5f) else 0f

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analytics_dimension_summary_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Tinted gradient header strip
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                                        )
                                    )
                                )
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = AppIcons.Insights,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = summaryCardTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "多维度收支结构与预算健康评估",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (netCents >= 0L) IncomeJade.copy(alpha = 0.14f) else ExpenseCoral.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (netCents >= 0L) "净盈余" else "净支出",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (netCents >= 0L) IncomeJade else ExpenseCoral,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 3-KPI Dimension Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("期间结余", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        CompactFullText(
                                            text = "${if (netCents >= 0L) "+" else "-"}${formatCurrency(kotlin.math.abs(netCents), baseCurrency)}",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = numberFont,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (netCents >= 0L) IncomeJade else ExpenseCoral,
                                            maxLines = 2
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("高频分类", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val catPct = if (topCategory != null && totalExpense > 0L) {
                                            "${(topCategory.cents * 100L / totalExpense)}%"
                                        } else ""
                                        CompactFullText(
                                            text = topCategory?.let { "${it.label} $catPct" } ?: "暂无",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text("主力渠道", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        CompactFullText(
                                            text = topChannel?.label ?: "暂无",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = topChannel?.color ?: MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }

                            if (budgetRef > 0L) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
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
                                                text = "预算执行进度 (${(budgetRatio * 100f).roundToInt()}%)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${formatCurrency(totalExpense, baseCurrency)} / ${formatCurrency(budgetRef, baseCurrency)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { budgetRatio.coerceIn(0f, 1f) },
                                            color = if (budgetRatio > 1f) ExpenseCoral else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }

                            // Accent Quote Insight Callout
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = summaryText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "calendar_pull_entry_hint") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onCalendarFullScreenChange(true) }
                        .testTag("analytics_open_fullscreen_calendar_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = AppIcons.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "进入全屏收支日历",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "在顶部向下划动或点击此处展开 年/月/周/日 日历视图",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isCalendarFullScreen,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f),
                initialOffsetY = { -it }
            ) + fadeIn(tween(220)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.90f, stiffness = 420f),
                targetOffsetY = { -it }
            ) + fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            AnalyticsCalendarSection(
                transactions = allTransactions,
                granularity = selectedGranularity,
                anchorMillis = anchorMillis,
                baseCurrencyCode = baseCurrency,
                ratesToCny = exchangeRates.ratesToCny,
                onGranularityAndAnchorChange = { nextGran, nextAnchor ->
                    selectedGranularity = nextGran
                    anchorMillis = nextAnchor
                    selectedCategoryFilter = null
                    selectedChannelFilter = null
                },
                onTransactionClick = { inspectingTransaction = it },
                isFullScreen = true,
                onRequestExitFullScreen = { onCalendarFullScreenChange(false) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    inspectingTransaction?.let { tx ->
        val (recordedBase, recordedRate, recordedCents) = viewModel.recordedSnapshotDisplay(tx)
        TransactionDetailSheet(
            transaction = tx,
            baseCurrencyCode = baseCurrency,
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
                inspectingTransaction = null
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
}

/**
 * Displays complete labels/amounts without ellipsis truncation:
 * allows wrapping up to [maxLines] and automatically scales font size / condenses width when overflowing.
 */
@Composable
private fun CompactFullText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 2
) {
    var fontScale by remember(text, maxLines) { mutableFloatStateOf(1.0f) }
    var horizontalScale by remember(text, maxLines) { mutableFloatStateOf(1.0f) }

    val baseFontSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val baseLineHeight = if (style.lineHeight.isSpecified) style.lineHeight else (baseFontSize * 1.25f)

    val adaptedStyle = style.copy(
        fontSize = (baseFontSize.value * fontScale).sp,
        lineHeight = (baseLineHeight.value * fontScale).sp,
        textGeometricTransform = if (horizontalScale < 0.99f) {
            TextGeometricTransform(scaleX = horizontalScale)
        } else {
            style.textGeometricTransform
        }
    )

    Text(
        text = text,
        style = adaptedStyle,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = true,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.hasVisualOverflow) {
                when {
                    fontScale > 0.72f -> fontScale = (fontScale - 0.08f).coerceAtLeast(0.72f)
                    horizontalScale > 0.80f -> horizontalScale = (horizontalScale - 0.06f).coerceAtLeast(0.80f)
                }
            }
        }
    )
}

@Composable
private fun ChartStyleToggleButton(
    style: AnalyticsChartStyle,
    testTag: String,
    onSelect: (AnalyticsChartStyle) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AnalyticsChartStyle.entries.forEach { mode ->
                val selected = mode == style
                val icon = if (mode == AnalyticsChartStyle.PIE) AppIcons.PieChart else AppIcons.BarChart
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode.label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutPieChart(
    slices: List<ChartDatum>,
    totalCents: Long,
    selectedLabel: String?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (totalCents <= 0L) return@Canvas
        val strokeWidth = size.minDimension * 0.19f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        var startAngle = -90f
        val gapAngle = if (slices.size > 1) 3f else 0f

        slices.forEach { slice ->
            val sweep = (slice.cents.toFloat() / totalCents.toFloat()) * 360f
            val drawSweep = (sweep - gapAngle).coerceAtLeast(1.5f)
            val isMuted = selectedLabel != null && selectedLabel != slice.label
            val activeStroke = if (selectedLabel == slice.label) strokeWidth * 1.18f else strokeWidth
            drawArc(
                color = if (isMuted) slice.color.copy(alpha = 0.28f) else slice.color,
                startAngle = startAngle,
                sweepAngle = drawSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = activeStroke, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun VerticalBarChart(
    items: List<ChartDatum>,
    baseCurrencyCode: String,
    selectedLabel: String?,
    onSelect: (String) -> Unit
) {
    val maxCents = remember(items) { items.maxOfOrNull { it.cents }?.coerceAtLeast(1L) ?: 1L }
    val numberFont = LocalNumberFontFamily.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            val useHorizontalScroll = items.size > 4
            val contentWidth = if (useHorizontalScroll) {
                (84.dp * items.size).coerceAtLeast(maxWidth)
            } else {
                maxWidth
            }
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (useHorizontalScroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(contentWidth)
                        .height(140.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val lineYs = listOf(0.2f, 0.5f, 0.8f, 1.0f)
                        lineYs.forEach { frac ->
                            val y = size.height * frac
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        val count = items.size.coerceAtLeast(1)
                        val slotW = size.width / count
                        val barW = (slotW * 0.46f).coerceIn(16.dp.toPx(), 42.dp.toPx())
                        items.forEachIndexed { index, datum ->
                            val ratio = (datum.cents.toFloat() / maxCents.toFloat()).coerceIn(0.06f, 1f)
                            val barH = (size.height - 12.dp.toPx()) * ratio
                            val left = index * slotW + (slotW - barW) / 2f
                            val top = size.height - barH
                            val isMuted = selectedLabel != null && selectedLabel != datum.label
                            drawRoundRect(
                                color = if (isMuted) datum.color.copy(alpha = 0.28f) else datum.color,
                                topLeft = Offset(left, top),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.width(contentWidth),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items.forEach { datum ->
                        val isSelected = selectedLabel == datum.label
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(datum.label) }
                                .padding(vertical = 4.dp, horizontal = 3.dp)
                        ) {
                            CompactFullText(
                                text = datum.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                            CompactFullText(
                                text = formatCurrency(datum.cents, baseCurrencyCode),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClickableLegendRow(
    label: String,
    color: Color,
    valueText: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                CompactFullText(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
            CompactFullText(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClickableBreakdownRow(
    label: String,
    color: Color,
    amountText: String,
    ratio: Float,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                CompactFullText(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                CompactFullText(
                    text = amountText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2
                )
            }
            LinearProgressIndicator(
                progress = { ratio },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun FilteredTransactionsPanel(
    title: String,
    transactions: List<LedgerTransaction>,
    baseCurrencyCode: String,
    ratesToCny: Map<String, Double>,
    onClearFilter: () -> Unit,
    onTransactionClick: (LedgerTransaction) -> Unit
) {
    val numberFont = LocalNumberFontFamily.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_filtered_panel")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactFullText(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onClearFilter,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("收起", style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider()
            if (transactions.isEmpty()) {
                Text(
                    text = "暂无匹配条目",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                transactions.forEach { tx ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTransactionClick(tx) }
                            .testTag("filtered_tx_row_${tx.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                CompactFullText(
                                    text = tx.merchant,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 2
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ChannelBadge(channel = tx.channel)
                                    Text(
                                        text = "${formatDateHeader(tx.occurredAtMillis)} ${formatTimeShort(tx.occurredAtMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                CompactFullText(
                                    text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = numberFont,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = ExpenseCoral,
                                    textAlign = TextAlign.End,
                                    maxLines = 2
                                )
                                if (!tx.currencyCode.equals(baseCurrencyCode, ignoreCase = true)) {
                                    val baseCents = CurrencyExchangeManager.convertCents(
                                        tx.amountCents,
                                        tx.currencyCode,
                                        baseCurrencyCode,
                                        ratesToCny
                                    )
                                    CompactFullText(
                                        text = "≈ ${formatCurrency(baseCents, baseCurrencyCode)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End,
                                        maxLines = 2
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

private fun Modifier.twoFingerPinchZoom(
    key: Any?,
    onZoomDelta: (Float) -> Unit,
    onZoomEnd: () -> Unit = {}
): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pinching = false
        while (true) {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount == 0) {
                if (pinching) onZoomEnd()
                break
            }
            if (pressedCount >= 2) {
                pinching = true
                val zoom = event.calculateZoom()
                if (zoom != 1f && !zoom.isNaN()) {
                    onZoomDelta(zoom)
                }
                event.changes.forEach { it.consume() }
            }
        }
    }
}

