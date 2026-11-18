package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CurrencyExchangeManager
import com.example.model.AnalyticsChartStyle
import com.example.model.BudgetSettings
import com.example.model.ExchangeRateState
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.ui.components.ChannelBadge
import com.example.ui.components.TransactionDetailSheet
import com.example.ui.components.TransactionEditorDialog
import com.example.ui.components.channelBrandColor
import com.example.ui.components.formatCurrency
import com.example.ui.components.formatDateHeader
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeJade
import com.example.ui.theme.LocalNumberFontFamily
import com.example.viewmodel.LedgerViewModel
import com.example.viewmodel.UiState
import java.util.Calendar

private data class ChartDatum(
    val label: String,
    val cents: Long,
    val color: Color
)

@Composable
fun AnalyticsScreen(
    transactionsState: UiState<List<LedgerTransaction>>,
    settings: BudgetSettings,
    exchangeRates: ExchangeRateState,
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val nowCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(nowCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(nowCal.get(Calendar.MONTH)) }

    var categoryChartStyle by remember { mutableStateOf(AnalyticsChartStyle.PIE) }
    var channelChartStyle by remember { mutableStateOf(AnalyticsChartStyle.BAR) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedChannelFilter by remember { mutableStateOf<String?>(null) }
    var inspectingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }
    var editingTransaction by remember { mutableStateOf<LedgerTransaction?>(null) }

    val numberFont = LocalNumberFontFamily.current
    val baseCurrency = settings.baseCurrencyCode
    val allTransactions = (transactionsState as? UiState.Success)?.data ?: emptyList()

    val monthTransactions by remember(allTransactions, selectedYear, selectedMonth) {
        derivedStateOf {
            LedgerViewModel.filterByMonth(allTransactions, selectedYear, selectedMonth)
                .filter { it.confirmed }
        }
    }

    fun toBase(tx: LedgerTransaction): Long = CurrencyExchangeManager.convertCents(
        tx.amountCents,
        tx.currencyCode,
        baseCurrency,
        exchangeRates.ratesToCny
    )

    val monthExpenseTxs = remember(monthTransactions) {
        monthTransactions.filter { it.txType == TransactionType.EXPENSE }
    }

    val totalExpense = remember(monthExpenseTxs, exchangeRates.ratesToCny, baseCurrency) {
        monthExpenseTxs.sumOf { toBase(it) }
    }
    val totalIncome = remember(monthTransactions, exchangeRates.ratesToCny, baseCurrency) {
        monthTransactions.filter { it.txType == TransactionType.INCOME }.sumOf { toBase(it) }
    }

    val categoryData = remember(monthExpenseTxs, exchangeRates.ratesToCny, baseCurrency) {
        monthExpenseTxs
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { toBase(it) } }
            .entries
            .sortedByDescending { it.value }
            .mapIndexed { index, entry ->
                ChartDatum(entry.key, entry.value, ChartPalette[index % ChartPalette.size])
            }
    }

    val channelData = remember(monthExpenseTxs, exchangeRates.ratesToCny, baseCurrency) {
        monthExpenseTxs
            .groupBy { it.channel }
            .mapValues { (_, list) -> list.sumOf { toBase(it) } }
            .entries
            .sortedByDescending { it.value }
            .map { entry ->
                ChartDatum(entry.key, entry.value, channelBrandColor(entry.key))
            }
    }

    val filteredCategoryTxs = remember(monthExpenseTxs, selectedCategoryFilter) {
        selectedCategoryFilter?.let { target ->
            monthExpenseTxs.filter { it.category == target }.sortedByDescending { it.occurredAtMillis }
        } ?: emptyList()
    }

    val filteredChannelTxs = remember(monthExpenseTxs, selectedChannelFilter) {
        selectedChannelFilter?.let { target ->
            monthExpenseTxs.filter { it.channel == target }.sortedByDescending { it.occurredAtMillis }
        } ?: emptyList()
    }

    val summaryText = remember(monthTransactions, selectedYear, selectedMonth, exchangeRates.ratesToCny, baseCurrency) {
        viewModel.buildMonthlyInsight(selectedYear, selectedMonth)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "month_switcher") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                selectedCategoryFilter = null
                                selectedChannelFilter = null
                                if (selectedMonth == 0) {
                                    selectedMonth = 11
                                    selectedYear -= 1
                                } else {
                                    selectedMonth -= 1
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上个月")
                        }
                        Text(
                            text = "${selectedYear}年${selectedMonth + 1}月 收支分析 ($baseCurrency)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(
                            onClick = {
                                selectedCategoryFilter = null
                                selectedChannelFilter = null
                                if (selectedMonth == 11) {
                                    selectedMonth = 0
                                    selectedYear += 1
                                } else {
                                    selectedMonth += 1
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下个月")
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
                            Text(
                                text = formatCurrency(totalExpense, baseCurrency),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = numberFont),
                                color = ExpenseCoral,
                                fontWeight = FontWeight.Bold
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
                            Text(
                                text = formatCurrency(totalIncome, baseCurrency),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = numberFont),
                                color = IncomeJade,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 1. Category Chart Card with Top-Right Chart Switcher + Clickable Legend/List
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
                                    text = "点击图例或分类行可筛选条目",
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
                                text = "本月暂无支出记录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            if (categoryChartStyle == AnalyticsChartStyle.PIE) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(146.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DonutPieChart(
                                            slices = categoryData,
                                            totalCents = totalExpense,
                                            selectedLabel = selectedCategoryFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = selectedCategoryFilter ?: "共支出",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val centerCents = selectedCategoryFilter?.let { sel ->
                                                categoryData.firstOrNull { it.label == sel }?.cents ?: totalExpense
                                            } ?: totalExpense
                                            Text(
                                                text = formatCurrency(centerCents, baseCurrency),
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = numberFont),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        categoryData.forEach { datum ->
                                            val pct = (datum.cents * 100.0 / totalExpense).toInt()
                                            val isSelected = selectedCategoryFilter == datum.label
                                            ClickableLegendRow(
                                                label = datum.label,
                                                color = datum.color,
                                                valueText = "$pct% · ${formatCurrency(datum.cents, baseCurrency)}",
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
                                    items = categoryData.take(7),
                                    selectedLabel = selectedCategoryFilter,
                                    onSelect = { label ->
                                        selectedCategoryFilter = if (selectedCategoryFilter == label) null else label
                                    }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    categoryData.forEach { datum ->
                                        val ratio = (datum.cents.toFloat() / totalExpense.toFloat()).coerceIn(0f, 1f)
                                        val isSelected = selectedCategoryFilter == datum.label
                                        ClickableBreakdownRow(
                                            label = datum.label,
                                            color = datum.color,
                                            amountText = "${formatCurrency(datum.cents, baseCurrency)} (${(ratio * 100).toInt()}%)",
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

            // 2. Channel Distribution Card with Top-Right Chart Switcher + Clickable Legend/List
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
                                    text = "点击图例或渠道行可筛选条目",
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(146.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DonutPieChart(
                                            slices = channelData,
                                            totalCents = totalExpense,
                                            selectedLabel = selectedChannelFilter,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = selectedChannelFilter ?: "渠道汇总",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val centerCents = selectedChannelFilter?.let { sel ->
                                                channelData.firstOrNull { it.label == sel }?.cents ?: totalExpense
                                            } ?: totalExpense
                                            Text(
                                                text = formatCurrency(centerCents, baseCurrency),
                                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = numberFont),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        channelData.forEach { datum ->
                                            val pct = (datum.cents * 100.0 / totalExpense).toInt()
                                            val isSelected = selectedChannelFilter == datum.label
                                            ClickableLegendRow(
                                                label = datum.label,
                                                color = datum.color,
                                                valueText = "$pct% · ${formatCurrency(datum.cents, baseCurrency)}",
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
                                    items = channelData.take(7),
                                    selectedLabel = selectedChannelFilter,
                                    onSelect = { label ->
                                        selectedChannelFilter = if (selectedChannelFilter == label) null else label
                                    }
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    channelData.forEach { datum ->
                                        val ratio = (datum.cents.toFloat() / totalExpense.toFloat()).coerceIn(0f, 1f)
                                        val isSelected = selectedChannelFilter == datum.label
                                        ClickableBreakdownRow(
                                            label = datum.label,
                                            color = datum.color,
                                            amountText = "${formatCurrency(datum.cents, baseCurrency)} (${(ratio * 100).toInt()}%)",
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

            item(key = "monthly_summary") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Insights,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text("月度收支分析", style = MaterialTheme.typography.titleMedium)
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
            onSave = { amountCents, currencyCode, type, category, merchant, channel, tail, note ->
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
                    occurredAtMillis = tx.occurredAtMillis,
                    confirmed = true
                )
                editingTransaction = null
            }
        )
    }
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
                val icon = if (mode == AnalyticsChartStyle.PIE) Icons.Rounded.PieChart else Icons.Rounded.BarChart
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
                    val barW = (slotW * 0.48f).coerceIn(14.dp.toPx(), 42.dp.toPx())
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEach { datum ->
                    val isSelected = selectedLabel == datum.label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(datum.label) }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Text(
                            text = datum.label.take(4),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${(datum.cents / 100)}元",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = numberFont),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = tx.merchant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                Text(
                                    text = formatCurrency(tx.amountCents, tx.currencyCode, showSign = true, type = tx.txType),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = numberFont),
                                    color = ExpenseCoral,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!tx.currencyCode.equals(baseCurrencyCode, ignoreCase = true)) {
                                    val baseCents = CurrencyExchangeManager.convertCents(
                                        tx.amountCents,
                                        tx.currencyCode,
                                        baseCurrencyCode,
                                        ratesToCny
                                    )
                                    Text(
                                        text = "≈ ${formatCurrency(baseCents, baseCurrencyCode)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
