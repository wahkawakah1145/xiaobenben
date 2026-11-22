package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Close
import com.example.ui.theme.AppIcons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CurrencyExchangeManager
import com.example.model.AnalyticsGranularity
import com.example.model.ChannelSplitter
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class TrendChartStyle(val label: String) {
    BAR("柱状图"),
    LINE("折线图"),
    AREA("面积图")
}

enum class TrendMetricMode(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    NET("结余"),
    COMPARE("收支对比")
}

data class TrendBucketPoint(
    val shortLabel: String,
    val fullLabel: String,
    val expenseCents: Long,
    val incomeCents: Long,
    val txCount: Int
) {
    val netCents: Long get() = incomeCents - expenseCents

    fun metricValueCents(mode: TrendMetricMode): Long = when (mode) {
        TrendMetricMode.EXPENSE -> expenseCents
        TrendMetricMode.INCOME -> incomeCents
        TrendMetricMode.NET -> netCents
        TrendMetricMode.COMPARE -> max(expenseCents, incomeCents)
    }
}

object SpendingTrendCalculator {
    fun buildSeries(
        transactions: List<LedgerTransaction>,
        granularity: AnalyticsGranularity,
        periodOffset: Int,
        baseCurrencyCode: String = "CNY",
        ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny,
        categoryFilter: String? = null,
        channelFilter: String? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): Pair<String, List<TrendBucketPoint>> {
        val filtered = transactions.filter { tx ->
            val catOk = categoryFilter.isNullOrBlank() || tx.category.equals(categoryFilter, ignoreCase = true)
            val chOk = channelFilter.isNullOrBlank() || ChannelSplitter.matchesChannel(tx.channel, channelFilter)
            catOk && chOk
        }

        fun toBase(tx: LedgerTransaction): Long =
            CurrencyExchangeManager.convertCents(tx.amountCents, tx.currencyCode, baseCurrencyCode, ratesToCny)

        val baseCal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        val buckets = mutableListOf<Triple<String, String, Pair<Long, Long>>>()
        val periodTitle: String

        when (granularity) {
            AnalyticsGranularity.YEAR -> {
                baseCal.add(Calendar.YEAR, periodOffset)
                val targetYear = baseCal.get(Calendar.YEAR)
                periodTitle = "${targetYear}年（按月）"
                for (m in 0..11) {
                    val start = Calendar.getInstance().apply {
                        set(targetYear, m, 1, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val end = Calendar.getInstance().apply {
                        set(targetYear, m, 1, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.MONTH, 1)
                    }.timeInMillis
                    buckets.add(Triple("${m + 1}月", "${targetYear}年${m + 1}月", start to end))
                }
            }
            AnalyticsGranularity.MONTH -> {
                baseCal.set(Calendar.DAY_OF_MONTH, 1)
                baseCal.add(Calendar.MONTH, periodOffset)
                val year = baseCal.get(Calendar.YEAR)
                val month = baseCal.get(Calendar.MONTH)
                val daysInMonth = baseCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                periodTitle = "${year}年${month + 1}月（按日）"
                for (d in 1..daysInMonth) {
                    val start = Calendar.getInstance().apply {
                        set(year, month, d, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val end = start + 86_400_000L
                    buckets.add(Triple("${d}日", "${year}-${"%02d".format(month + 1)}-${"%02d".format(d)}", start to end))
                }
            }
            AnalyticsGranularity.WEEK -> {
                baseCal.firstDayOfWeek = Calendar.MONDAY
                baseCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                baseCal.set(Calendar.HOUR_OF_DAY, 0)
                baseCal.set(Calendar.MINUTE, 0)
                baseCal.set(Calendar.SECOND, 0)
                baseCal.set(Calendar.MILLISECOND, 0)
                baseCal.add(Calendar.WEEK_OF_YEAR, periodOffset)
                val startOfWeek = baseCal.timeInMillis
                val endLabelCal = (baseCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
                val mdFmt = SimpleDateFormat("MM-dd", Locale.US)
                val fullFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                periodTitle = "${mdFmt.format(Date(startOfWeek))} 至 ${mdFmt.format(Date(endLabelCal.timeInMillis))}（按日）"
                val weekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                for (i in 0..6) {
                    val dayStart = startOfWeek + i * 86_400_000L
                    val dayEnd = dayStart + 86_400_000L
                    buckets.add(Triple(weekNames[i], "${fullFmt.format(Date(dayStart))} ${weekNames[i]}", dayStart to dayEnd))
                }
            }
            AnalyticsGranularity.DAY -> {
                baseCal.set(Calendar.HOUR_OF_DAY, 0)
                baseCal.set(Calendar.MINUTE, 0)
                baseCal.set(Calendar.SECOND, 0)
                baseCal.set(Calendar.MILLISECOND, 0)
                baseCal.add(Calendar.DAY_OF_YEAR, periodOffset)
                val dayStart = baseCal.timeInMillis
                val fullDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayStart))
                periodTitle = "$fullDate（按时段）"
                for (h in 0..23) {
                    val hStart = dayStart + h * 3_600_000L
                    val hEnd = hStart + 3_600_000L
                    buckets.add(Triple("${h}时", "$fullDate ${"%02d".format(h)}:00-${"%02d".format(h)}:59", hStart to hEnd))
                }
            }
        }

        val points = buckets.map { (shortLabel, fullLabel, range) ->
            var exp = 0L
            var inc = 0L
            var count = 0
            filtered.forEach { tx ->
                if (tx.occurredAtMillis in range.first until range.second) {
                    count++
                    when (tx.txType) {
                        TransactionType.EXPENSE -> exp += toBase(tx)
                        TransactionType.INCOME -> inc += toBase(tx)
                        TransactionType.TRANSFER -> Unit
                    }
                }
            }
            TrendBucketPoint(
                shortLabel = shortLabel,
                fullLabel = fullLabel,
                expenseCents = exp,
                incomeCents = inc,
                txCount = count
            )
        }
        return periodTitle to points
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingTrendChartDialog(
    transactions: List<LedgerTransaction>,
    baseCurrencyCode: String,
    ratesToCny: Map<String, Double>,
    initialYearMonth: Pair<Int, Int>? = null,
    scopeTitle: String = "全部账单",
    onDismiss: () -> Unit
) {
    val currencySymbol = remember(baseCurrencyCode) { CurrencyCatalog.resolve(baseCurrencyCode).symbol }

    val initialMonthOffset = remember(initialYearMonth) {
        if (initialYearMonth == null) {
            0
        } else {
            val now = Calendar.getInstance()
            val curY = now.get(Calendar.YEAR)
            val curM = now.get(Calendar.MONTH) + 1
            (initialYearMonth.first - curY) * 12 + (initialYearMonth.second - curM)
        }
    }

    var granularity by remember { mutableStateOf(AnalyticsGranularity.MONTH) }
    var periodOffset by remember { mutableIntStateOf(initialMonthOffset) }
    var chartStyle by remember { mutableStateOf(TrendChartStyle.LINE) }
    var metricMode by remember { mutableStateOf(TrendMetricMode.EXPENSE) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var channelMenuOpen by remember { mutableStateOf(false) }

    val availableCategories = remember(transactions) {
        transactions.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val availableChannels = remember(transactions) {
        transactions.flatMap { ChannelSplitter.splitChannels(it.channel) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val (periodTitle, points) = remember(
        transactions,
        granularity,
        periodOffset,
        baseCurrencyCode,
        ratesToCny,
        selectedCategory,
        selectedChannel
    ) {
        SpendingTrendCalculator.buildSeries(
            transactions = transactions,
            granularity = granularity,
            periodOffset = periodOffset,
            baseCurrencyCode = baseCurrencyCode,
            ratesToCny = ratesToCny,
            categoryFilter = selectedCategory,
            channelFilter = selectedChannel
        )
    }

    var activeIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(periodTitle, granularity, selectedCategory, selectedChannel, metricMode) {
        activeIndex = points.indices.maxByOrNull { points[it].metricValueCents(metricMode) }
            ?.takeIf { points[it].txCount > 0 }
    }

    val totalExpenseCents = remember(points) { points.sumOf { it.expenseCents } }
    val totalIncomeCents = remember(points) { points.sumOf { it.incomeCents } }
    val totalTxCount = remember(points) { points.sumOf { it.txCount } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .testTag("spending_trend_chart_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
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
                            AppIcons.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "收支趋势图",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$scopeTitle · 按住图表左右滑动查看坐标",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("trend_chart_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭趋势图")
                    }
                }

                // Granularity selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AnalyticsGranularity.entries.forEachIndexed { idx, gran ->
                        SegmentedButton(
                            selected = granularity == gran,
                            onClick = {
                                if (granularity != gran) {
                                    granularity = gran
                                    periodOffset = 0
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(idx, AnalyticsGranularity.entries.size),
                            modifier = Modifier.testTag("trend_gran_${gran.name}")
                        ) {
                            Text(gran.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Period navigation row
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { periodOffset -= 1 }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周期")
                        }
                        Text(
                            text = periodTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = { if (periodOffset < 0) periodOffset += 1 },
                            enabled = periodOffset < 0
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周期")
                        }
                    }
                }

                // Chart style selector: 柱状图 / 折线图 / 面积图
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TrendChartStyle.entries.forEachIndexed { idx, style ->
                        SegmentedButton(
                            selected = chartStyle == style,
                            onClick = { chartStyle = style },
                            shape = SegmentedButtonDefaults.itemShape(idx, TrendChartStyle.entries.size),
                            modifier = Modifier.testTag("trend_style_${style.name}")
                        ) {
                            Text(style.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Metric & Category / Channel filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrendMetricMode.entries.forEach { mode ->
                        FilterChip(
                            selected = metricMode == mode,
                            onClick = { metricMode = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("trend_metric_${mode.name}")
                        )
                    }

                    Box {
                        FilterChip(
                            selected = selectedCategory != null,
                            onClick = { categoryMenuOpen = true },
                            label = { Text(selectedCategory?.let { "分类:$it" } ?: "全部分类") },
                            modifier = Modifier.testTag("trend_filter_category")
                        )
                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部分类") },
                                onClick = {
                                    selectedCategory = null
                                    categoryMenuOpen = false
                                }
                            )
                            availableCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        FilterChip(
                            selected = selectedChannel != null,
                            onClick = { channelMenuOpen = true },
                            label = { Text(selectedChannel?.let { "渠道:$it" } ?: "全部渠道") },
                            modifier = Modifier.testTag("trend_filter_channel")
                        )
                        DropdownMenu(
                            expanded = channelMenuOpen,
                            onDismissRequest = { channelMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部渠道") },
                                onClick = {
                                    selectedChannel = null
                                    channelMenuOpen = false
                                }
                            )
                            availableChannels.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text(ch) },
                                    onClick = {
                                        selectedChannel = ch
                                        channelMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Live coordinate readout card
                val inspectedPoint = activeIndex?.let { points.getOrNull(it) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trend_coordinate_card")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (inspectedPoint != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "坐标点 · ${inspectedPoint.fullLabel}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${inspectedPoint.txCount} 笔账单",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "支出 $currencySymbol${formatCompactYuan(inspectedPoint.expenseCents)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseCoral
                                )
                                Text(
                                    text = "收入 $currencySymbol${formatCompactYuan(inspectedPoint.incomeCents)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IncomeJade
                                )
                                val netPrefix = if (inspectedPoint.netCents >= 0L) "+" else "-"
                                Text(
                                    text = "结余 $netPrefix$currencySymbol${formatCompactYuan(abs(inspectedPoint.netCents))}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Text(
                                text = "当前周期共 $totalTxCount 笔 · 按住下方图表滑动查看任意时间点坐标",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Interactive Chart Canvas with hold-and-slide scrubbing
                InteractiveTrendCanvas(
                    points = points,
                    chartStyle = chartStyle,
                    metricMode = metricMode,
                    currencySymbol = currencySymbol,
                    activeIndex = activeIndex,
                    onActiveIndexChange = { activeIndex = it }
                )

                // Period summary footer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("周期总支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$currencySymbol${formatCompactYuan(totalExpenseCents)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseCoral
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("周期总收入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$currencySymbol${formatCompactYuan(totalIncomeCents)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = IncomeJade
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("周期笔数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$totalTxCount 笔",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveTrendCanvas(
    points: List<TrendBucketPoint>,
    chartStyle: TrendChartStyle,
    metricMode: TrendMetricMode,
    currencySymbol: String,
    activeIndex: Int?,
    onActiveIndexChange: (Int?) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    val primarySeriesColor = when (metricMode) {
        TrendMetricMode.EXPENSE -> ExpenseCoral
        TrendMetricMode.INCOME -> IncomeJade
        TrendMetricMode.NET -> primaryColor
        TrendMetricMode.COMPARE -> ExpenseCoral
    }

    val minValCents: Long
    val maxValCents: Long
    if (metricMode == TrendMetricMode.NET) {
        val minN = points.minOfOrNull { it.netCents } ?: 0L
        val maxN = points.maxOfOrNull { it.netCents } ?: 0L
        minValCents = minOf(0L, minN)
        maxValCents = maxOf(100L, maxN, abs(minN))
    } else {
        minValCents = 0L
        val peak = points.maxOfOrNull { it.metricValueCents(metricMode) } ?: 0L
        maxValCents = maxOf(100L, peak)
    }

    val spanCents = (maxValCents - minValCents).coerceAtLeast(100L)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Top & Mid Y-axis legend strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "峰值 $currencySymbol${formatCompactYuan(maxValCents)}",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor
            )
            if (metricMode == TrendMetricMode.COMPARE) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("● 支出", style = MaterialTheme.typography.labelSmall, color = ExpenseCoral)
                    Text("● 收入", style = MaterialTheme.typography.labelSmall, color = IncomeJade)
                }
            } else {
                Text(
                    text = if (minValCents < 0L) "谷值 -$currencySymbol${formatCompactYuan(abs(minValCents))}" else "基准 0",
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp)
        ) {
            if (points.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无趋势数据", style = MaterialTheme.typography.bodyMedium, color = labelColor)
                }
            } else {
                fun indexForX(x: Float, width: Float): Int {
                    val leftPad = 24f
                    val rightPad = 24f
                    val usable = (width - leftPad - rightPad).coerceAtLeast(1f)
                    val ratio = ((x - leftPad) / usable).coerceIn(0f, 1f)
                    return if (points.size <= 1) 0 else (ratio * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                        .testTag("trend_chart_canvas")
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                onActiveIndexChange(indexForX(offset.x, size.width.toFloat()))
                            }
                        }
                        .pointerInput(points) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    onActiveIndexChange(indexForX(offset.x, size.width.toFloat()))
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    onActiveIndexChange(indexForX(change.position.x, size.width.toFloat()))
                                }
                            )
                        }
                ) {
                    val leftPad = 20f
                    val rightPad = 20f
                    val topPad = 18f
                    val bottomPad = 18f
                    val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    fun xFor(index: Int): Float {
                        if (points.size <= 1) return leftPad + chartW / 2f
                        return leftPad + (index.toFloat() / points.lastIndex.toFloat()) * chartW
                    }

                    fun yFor(cents: Long): Float {
                        val norm = ((cents - minValCents).toDouble() / spanCents.toDouble()).toFloat().coerceIn(0f, 1f)
                        return topPad + chartH * (1f - norm)
                    }

                    val zeroY = yFor(0L)

                    // Horizontal dashed grid lines
                    val gridEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (step in 0..3) {
                        val gy = topPad + chartH * (step / 3f)
                        drawLine(
                            color = outlineColor.copy(alpha = 0.45f),
                            start = Offset(leftPad, gy),
                            end = Offset(leftPad + chartW, gy),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = gridEffect
                        )
                    }

                    // Baseline at y = 0
                    drawLine(
                        color = outlineColor.copy(alpha = 0.85f),
                        start = Offset(leftPad, zeroY),
                        end = Offset(leftPad + chartW, zeroY),
                        strokeWidth = 1.2.dp.toPx()
                    )

                    fun drawLineOrAreaSeries(values: List<Long>, color: Color, fillArea: Boolean) {
                        if (values.isEmpty()) return
                        val strokePath = Path()
                        val areaPath = Path()
                        values.forEachIndexed { idx, v ->
                            val px = xFor(idx)
                            val py = yFor(v)
                            if (idx == 0) {
                                strokePath.moveTo(px, py)
                                areaPath.moveTo(px, zeroY)
                                areaPath.lineTo(px, py)
                            } else {
                                val prevX = xFor(idx - 1)
                                val prevY = yFor(values[idx - 1])
                                val midX = (prevX + px) / 2f
                                strokePath.cubicTo(midX, prevY, midX, py, px, py)
                                areaPath.cubicTo(midX, prevY, midX, py, px, py)
                            }
                            if (idx == values.lastIndex) {
                                areaPath.lineTo(px, zeroY)
                                areaPath.close()
                            }
                        }
                        if (fillArea) {
                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(color.copy(alpha = 0.34f), color.copy(alpha = 0.02f)),
                                    startY = topPad,
                                    endY = topPad + chartH
                                )
                            )
                        }
                        drawPath(
                            path = strokePath,
                            color = color,
                            style = Stroke(
                                width = 2.6.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        if (values.size <= 16) {
                            values.forEachIndexed { idx, v ->
                                val px = xFor(idx)
                                val py = yFor(v)
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(px, py)
                                )
                                drawCircle(
                                    color = color,
                                    radius = 2.2.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }
                        }
                    }

                    when (chartStyle) {
                        TrendChartStyle.BAR -> {
                            val slotW = chartW / points.size.coerceAtLeast(1)
                            if (metricMode == TrendMetricMode.COMPARE) {
                                val barW = (slotW * 0.34f).coerceIn(3f, 22f)
                                points.forEachIndexed { idx, pt ->
                                    val cx = xFor(idx)
                                    val expY = yFor(pt.expenseCents)
                                    val incY = yFor(pt.incomeCents)
                                    val alpha = if (activeIndex == null || activeIndex == idx) 0.95f else 0.55f
                                    drawRoundRect(
                                        color = ExpenseCoral.copy(alpha = alpha),
                                        topLeft = Offset(cx - barW - 1.5f, expY),
                                        size = Size(barW, (zeroY - expY).coerceAtLeast(2f)),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                    drawRoundRect(
                                        color = IncomeJade.copy(alpha = alpha),
                                        topLeft = Offset(cx + 1.5f, incY),
                                        size = Size(barW, (zeroY - incY).coerceAtLeast(2f)),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                }
                            } else {
                                val barW = (slotW * 0.56f).coerceIn(4f, 28f)
                                points.forEachIndexed { idx, pt ->
                                    val valCents = pt.metricValueCents(metricMode)
                                    val cx = xFor(idx)
                                    val py = yFor(valCents)
                                    val topY = minOf(py, zeroY)
                                    val h = abs(zeroY - py).coerceAtLeast(2f)
                                    val barColor = if (metricMode == TrendMetricMode.NET) {
                                        if (valCents >= 0L) IncomeJade else ExpenseCoral
                                    } else {
                                        primarySeriesColor
                                    }
                                    val alpha = if (activeIndex == null || activeIndex == idx) 0.95f else 0.55f
                                    drawRoundRect(
                                        color = barColor.copy(alpha = alpha),
                                        topLeft = Offset(cx - barW / 2f, topY),
                                        size = Size(barW, h),
                                        cornerRadius = CornerRadius(5f, 5f)
                                    )
                                }
                            }
                        }
                        TrendChartStyle.LINE, TrendChartStyle.AREA -> {
                            val fillArea = chartStyle == TrendChartStyle.AREA
                            if (metricMode == TrendMetricMode.COMPARE) {
                                drawLineOrAreaSeries(points.map { it.incomeCents }, IncomeJade, fillArea)
                                drawLineOrAreaSeries(points.map { it.expenseCents }, ExpenseCoral, fillArea)
                            } else {
                                drawLineOrAreaSeries(
                                    points.map { it.metricValueCents(metricMode) },
                                    primarySeriesColor,
                                    fillArea
                                )
                            }
                        }
                    }

                    // Active scrubbing crosshair & highlight marker
                    activeIndex?.takeIf { it in points.indices }?.let { selIdx ->
                        val sx = xFor(selIdx)
                        drawLine(
                            color = primaryColor.copy(alpha = 0.85f),
                            start = Offset(sx, topPad - 6f),
                            end = Offset(sx, topPad + chartH + 6f),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                        val pt = points[selIdx]
                        if (metricMode == TrendMetricMode.COMPARE) {
                            listOf(
                                pt.expenseCents to ExpenseCoral,
                                pt.incomeCents to IncomeJade
                            ).forEach { (v, c) ->
                                val sy = yFor(v)
                                drawCircle(color = c.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = Offset(sx, sy))
                                drawCircle(color = surfaceColor, radius = 5.5.dp.toPx(), center = Offset(sx, sy))
                                drawCircle(color = c, radius = 4.dp.toPx(), center = Offset(sx, sy))
                            }
                        } else {
                            val sy = yFor(pt.metricValueCents(metricMode))
                            drawCircle(color = primarySeriesColor.copy(alpha = 0.25f), radius = 10.dp.toPx(), center = Offset(sx, sy))
                            drawCircle(color = surfaceColor, radius = 5.5.dp.toPx(), center = Offset(sx, sy))
                            drawCircle(color = primarySeriesColor, radius = 4.dp.toPx(), center = Offset(sx, sy))
                        }
                    }
                }
            }
        }

        // X-axis tick labels (first, middle, last)
        if (points.isNotEmpty()) {
            val midIdx = points.size / 2
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(points.first().shortLabel, style = MaterialTheme.typography.labelSmall, color = labelColor)
                if (points.size >= 3) {
                    Text(points[midIdx].shortLabel, style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
                if (points.size >= 2) {
                    Text(points.last().shortLabel, style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
        }
    }
}

private fun formatCompactYuan(cents: Long): String {
    val yuan = cents / 100.0
    return if (yuan >= 100_000.0) {
        String.format(Locale.US, "%.1f万", yuan / 10_000.0)
    } else {
        String.format(Locale.US, "%.2f", yuan)
    }
}
