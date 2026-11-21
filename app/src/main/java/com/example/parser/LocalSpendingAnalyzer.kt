package com.example.parser

import com.example.data.CurrencyExchangeManager
import com.example.model.AnalyticsGranularity
import com.example.model.ChannelSplitter
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class QuickAnalysisBreakdownItem(
    val label: String,
    val amountCents: Long,
    val count: Int,
    val sharePercent: Double
)

data class QuickAnalysisHighlightTx(
    val merchant: String,
    val amountCents: Long,
    val dateText: String
)

data class QuickAnalysisReport(
    val scopeTitle: String,
    val baseCurrencyCode: String,
    val currencySymbol: String,
    val dateRange: String,
    val totalCount: Int,
    val expenseCount: Int,
    val incomeCount: Int,
    val transferCount: Int,
    val foreignCount: Int,
    val totalExpenseCents: Long,
    val totalIncomeCents: Long,
    val totalTransferCents: Long,
    val netCents: Long,
    val avgExpenseCents: Long,
    val largestExpense: QuickAnalysisHighlightTx?,
    val largestIncome: QuickAnalysisHighlightTx?,
    val topCategories: List<QuickAnalysisBreakdownItem>,
    val topChannels: List<QuickAnalysisBreakdownItem>,
    val hasCompositeChannels: Boolean,
    val plainText: String
)

object LocalSpendingAnalyzer {

    fun generateMonthlySummary(
        monthLabel: String,
        transactions: List<LedgerTransaction>,
        monthlyBudgetCents: Long,
        baseCurrencyCode: String = "CNY",
        ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny
    ): String = generatePeriodSummary(
        periodLabel = monthLabel,
        granularity = AnalyticsGranularity.MONTH,
        transactions = transactions,
        monthlyBudgetCents = monthlyBudgetCents,
        baseCurrencyCode = baseCurrencyCode,
        ratesToCny = ratesToCny
    )

    fun generatePeriodSummary(
        periodLabel: String,
        granularity: AnalyticsGranularity,
        transactions: List<LedgerTransaction>,
        monthlyBudgetCents: Long,
        baseCurrencyCode: String = "CNY",
        ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny
    ): String {
        val confirmedTx = transactions.filter { it.confirmed }
        if (confirmedTx.isEmpty()) {
            return "当前 $periodLabel 暂无已确认账单，记账后即可查看分析。"
        }

        val symbol = CurrencyCatalog.resolve(baseCurrencyCode).symbol
        fun toBase(tx: LedgerTransaction): Long =
            CurrencyExchangeManager.convertCents(tx.amountCents, tx.currencyCode, baseCurrencyCode, ratesToCny)

        val expenses = confirmedTx.filter { it.txType == TransactionType.EXPENSE }
        val incomes = confirmedTx.filter { it.txType == TransactionType.INCOME }
        val totalExpense = expenses.sumOf { toBase(it) }
        val totalIncome = incomes.sumOf { toBase(it) }

        val topCategory = expenses
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { toBase(it) } }
            .maxByOrNull { it.value }

        val channelSums = LinkedHashMap<String, Long>()
        expenses.forEach { tx ->
            val cents = toBase(tx)
            ChannelSplitter.splitChannels(tx.channel).forEach { subChannel ->
                channelSums[subChannel] = (channelSums[subChannel] ?: 0L) + cents
            }
        }
        val topChannel = channelSums.maxByOrNull { it.value }

        val largestTx = expenses.maxByOrNull { toBase(it) }

        val divisorDays = when (granularity) {
            AnalyticsGranularity.YEAR -> 365.0
            AnalyticsGranularity.MONTH -> Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtLeast(1).toDouble()
            AnalyticsGranularity.WEEK -> 7.0
            AnalyticsGranularity.DAY -> 1.0
        }
        val avgHint = when (granularity) {
            AnalyticsGranularity.YEAR -> "月均约 $symbol%.2f".format(totalExpense / 100.0 / 12.0)
            AnalyticsGranularity.MONTH -> "日均约 $symbol%.2f".format((totalExpense / 100.0) / divisorDays)
            AnalyticsGranularity.WEEK -> "日均约 $symbol%.2f".format((totalExpense / 100.0) / 7.0)
            AnalyticsGranularity.DAY -> "共计 ${expenses.size} 笔消费"
        }

        val referenceBudgetCents = when {
            monthlyBudgetCents <= 0L -> 0L
            granularity == AnalyticsGranularity.YEAR -> monthlyBudgetCents * 12L
            granularity == AnalyticsGranularity.MONTH -> monthlyBudgetCents
            granularity == AnalyticsGranularity.WEEK -> (monthlyBudgetCents * 7L) / 30L
            granularity == AnalyticsGranularity.DAY -> monthlyBudgetCents / 30L
            else -> 0L
        }.coerceAtLeast(if (monthlyBudgetCents > 0L) 1L else 0L)

        return buildString {
            appendLine(
                "1. 收支概况：${periodLabel}累计支出 $symbol%.2f（$avgHint），收入 $symbol%.2f，共 ${confirmedTx.size} 笔账单。".format(
                    totalExpense / 100.0,
                    totalIncome / 100.0
                )
            )
            if (referenceBudgetCents > 0L) {
                val pct = (totalExpense * 100.0 / referenceBudgetCents).toInt()
                val budgetScope = when (granularity) {
                    AnalyticsGranularity.YEAR -> "年度参考预算"
                    AnalyticsGranularity.MONTH -> "月度预算"
                    AnalyticsGranularity.WEEK -> "周度折算预算"
                    AnalyticsGranularity.DAY -> "日均折算预算"
                }
                val statusHint = when {
                    pct >= 100 -> "已超出${budgetScope} ${pct - 100}%，建议关注非必要开支。"
                    pct >= 80 -> "已使用${budgetScope} $pct%，接近上限。"
                    else -> "当前${budgetScope}使用率 $pct%，节奏平稳。"
                }
                appendLine("2. 预算执行：$statusHint")
            }
            if (topCategory != null && totalExpense > 0L) {
                val catPct = (topCategory.value * 100.0 / totalExpense).toInt()
                val catYuan = "%.2f".format(topCategory.value / 100.0)
                val chHint = topChannel?.let { "；主要渠道为「${it.key}」（$symbol%.2f）".format(it.value / 100.0) }.orEmpty()
                val maxHint = largestTx?.let { "；单笔最高为「${it.merchant}」$symbol%.2f".format(toBase(it) / 100.0) }.orEmpty()
                append("3. 结构重心：支出最多的是「${topCategory.key}」（占比 $catPct%，$symbol$catYuan）$chHint$maxHint。")
            }
        }.trim()
    }

    fun generateQuickSelectionReport(
        transactions: List<LedgerTransaction>,
        baseCurrencyCode: String = "CNY",
        ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny,
        scopeTitle: String = "选中账单快速分析"
    ): QuickAnalysisReport {
        val symbol = CurrencyCatalog.resolve(baseCurrencyCode).symbol
        if (transactions.isEmpty()) {
            return QuickAnalysisReport(
                scopeTitle = scopeTitle,
                baseCurrencyCode = baseCurrencyCode,
                currencySymbol = symbol,
                dateRange = "-",
                totalCount = 0,
                expenseCount = 0,
                incomeCount = 0,
                transferCount = 0,
                foreignCount = 0,
                totalExpenseCents = 0L,
                totalIncomeCents = 0L,
                totalTransferCents = 0L,
                netCents = 0L,
                avgExpenseCents = 0L,
                largestExpense = null,
                largestIncome = null,
                topCategories = emptyList(),
                topChannels = emptyList(),
                hasCompositeChannels = false,
                plainText = "当前未选中任何账单记录。"
            )
        }

        fun toBase(tx: LedgerTransaction): Long =
            CurrencyExchangeManager.convertCents(tx.amountCents, tx.currencyCode, baseCurrencyCode, ratesToCny)

        val expenses = transactions.filter { it.txType == TransactionType.EXPENSE }
        val incomes = transactions.filter { it.txType == TransactionType.INCOME }
        val transfers = transactions.filter { it.txType == TransactionType.TRANSFER }

        val totalExpense = expenses.sumOf { toBase(it) }
        val totalIncome = incomes.sumOf { toBase(it) }
        val totalTransfer = transfers.sumOf { toBase(it) }
        val net = totalIncome - totalExpense
        val avgExpenseCents = if (expenses.isNotEmpty()) totalExpense / expenses.size else 0L

        val sortedTimes = transactions.map { it.occurredAtMillis }.sorted()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val firstDate = dateFmt.format(Date(sortedTimes.first()))
        val lastDate = dateFmt.format(Date(sortedTimes.last()))
        val dateRange = if (firstDate == lastDate) firstDate else "$firstDate 至 $lastDate"

        val foreignCount = transactions.count { !it.currencyCode.equals(baseCurrencyCode, ignoreCase = true) }

        val topCategories = if (totalExpense > 0L) {
            expenses
                .groupBy { it.category }
                .map { (cat, list) ->
                    val cents = list.sumOf { toBase(it) }
                    QuickAnalysisBreakdownItem(
                        label = cat,
                        amountCents = cents,
                        count = list.size,
                        sharePercent = cents * 100.0 / totalExpense.toDouble()
                    )
                }
                .sortedByDescending { it.amountCents }
        } else {
            emptyList()
        }

        var hasCompositeChannels = false
        val channelMap = LinkedHashMap<String, Pair<Long, Int>>()
        expenses.forEach { tx ->
            val cents = toBase(tx)
            val subChannels = ChannelSplitter.splitChannels(tx.channel)
            if (subChannels.size > 1) hasCompositeChannels = true
            subChannels.forEach { sub ->
                val prev = channelMap[sub]
                channelMap[sub] = ((prev?.first ?: 0L) + cents) to ((prev?.second ?: 0) + 1)
            }
        }
        val channelSumTotal = channelMap.values.sumOf { it.first }
        val topChannels = if (channelSumTotal > 0L) {
            channelMap.entries
                .map { (ch, pair) ->
                    QuickAnalysisBreakdownItem(
                        label = ch,
                        amountCents = pair.first,
                        count = pair.second,
                        sharePercent = pair.first * 100.0 / channelSumTotal.toDouble()
                    )
                }
                .sortedByDescending { it.amountCents }
        } else {
            emptyList()
        }

        val largestExpense = expenses.maxByOrNull { toBase(it) }?.let { tx ->
            QuickAnalysisHighlightTx(
                merchant = tx.merchant,
                amountCents = toBase(tx),
                dateText = dateFmt.format(Date(tx.occurredAtMillis))
            )
        }
        val largestIncome = incomes.maxByOrNull { toBase(it) }?.let { tx ->
            QuickAnalysisHighlightTx(
                merchant = tx.merchant,
                amountCents = toBase(tx),
                dateText = dateFmt.format(Date(tx.occurredAtMillis))
            )
        }

        val plainText = buildString {
            appendLine("【$scopeTitle】")
            appendLine("统计范围：$dateRange · 共 ${transactions.size} 笔（支出 ${expenses.size} / 收入 ${incomes.size}${if (transfers.isNotEmpty()) " / 转账 ${transfers.size}" else ""}）")
            if (foreignCount > 0) {
                appendLine("币种折算：含 $foreignCount 笔外币记录，已统一折合 $baseCurrencyCode")
            }
            appendLine()
            appendLine("• 支出合计：$symbol%.2f".format(totalExpense / 100.0))
            appendLine("• 收入合计：$symbol%.2f".format(totalIncome / 100.0))
            if (totalTransfer > 0L) {
                appendLine("• 转账合计：$symbol%.2f".format(totalTransfer / 100.0))
            }
            val netPrefix = if (net >= 0L) "+" else "-"
            appendLine("• 净收支：$netPrefix$symbol%.2f".format(abs(net) / 100.0))
            if (expenses.isNotEmpty()) {
                appendLine("• 笔均支出：$symbol%.2f".format(totalExpense / 100.0 / expenses.size.toDouble()))
            }
            largestExpense?.let { hi ->
                appendLine("• 单笔最高支出：${hi.merchant}（$symbol%.2f · ${hi.dateText}）".format(hi.amountCents / 100.0))
            }
            largestIncome?.let { hi ->
                appendLine("• 单笔最高收入：${hi.merchant}（$symbol%.2f · ${hi.dateText}）".format(hi.amountCents / 100.0))
            }
            if (topCategories.isNotEmpty() && totalExpense > 0L) {
                appendLine()
                appendLine("分类支出分布：")
                topCategories.take(6).forEachIndexed { idx, item ->
                    appendLine("  ${idx + 1}. ${item.label}：$symbol%.2f（%.1f%% · ${item.count}笔）".format(item.amountCents / 100.0, item.sharePercent))
                }
            }
            if (topChannels.isNotEmpty() && channelSumTotal > 0L) {
                appendLine()
                appendLine(if (hasCompositeChannels) "渠道支出分布（组合渠道已按符号拆分统计）：" else "渠道支出分布：")
                topChannels.take(6).forEachIndexed { idx, item ->
                    appendLine("  ${idx + 1}. ${item.label}：$symbol%.2f（%.1f%% · ${item.count}笔）".format(item.amountCents / 100.0, item.sharePercent))
                }
            }
        }.trim()

        return QuickAnalysisReport(
            scopeTitle = scopeTitle,
            baseCurrencyCode = baseCurrencyCode,
            currencySymbol = symbol,
            dateRange = dateRange,
            totalCount = transactions.size,
            expenseCount = expenses.size,
            incomeCount = incomes.size,
            transferCount = transfers.size,
            foreignCount = foreignCount,
            totalExpenseCents = totalExpense,
            totalIncomeCents = totalIncome,
            totalTransferCents = totalTransfer,
            netCents = net,
            avgExpenseCents = avgExpenseCents,
            largestExpense = largestExpense,
            largestIncome = largestIncome,
            topCategories = topCategories,
            topChannels = topChannels,
            hasCompositeChannels = hasCompositeChannels,
            plainText = plainText
        )
    }

    fun generateQuickSelectionSummary(
        transactions: List<LedgerTransaction>,
        baseCurrencyCode: String = "CNY",
        ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny,
        scopeTitle: String = "选中账单快速分析"
    ): String = generateQuickSelectionReport(
        transactions = transactions,
        baseCurrencyCode = baseCurrencyCode,
        ratesToCny = ratesToCny,
        scopeTitle = scopeTitle
    ).plainText
}
