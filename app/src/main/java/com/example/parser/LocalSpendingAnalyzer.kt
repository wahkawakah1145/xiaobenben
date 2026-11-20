package com.example.parser

import com.example.data.CurrencyExchangeManager
import com.example.model.AnalyticsGranularity
import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import java.util.Calendar

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

        val topChannel = expenses
            .groupBy { it.channel }
            .mapValues { (_, list) -> list.sumOf { toBase(it) } }
            .maxByOrNull { it.value }

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

    fun generateQuickSelectionSummary(
        transactions: List<LedgerTransaction>,
        baseCurrencyCode: String = "CNY",
        ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny,
        scopeTitle: String = "选中账单快速分析"
    ): String {
        if (transactions.isEmpty()) {
            return "当前未选中任何账单记录。"
        }
        val symbol = CurrencyCatalog.resolve(baseCurrencyCode).symbol
        fun toBase(tx: LedgerTransaction): Long =
            CurrencyExchangeManager.convertCents(tx.amountCents, tx.currencyCode, baseCurrencyCode, ratesToCny)

        val expenses = transactions.filter { it.txType == TransactionType.EXPENSE }
        val incomes = transactions.filter { it.txType == TransactionType.INCOME }
        val transfers = transactions.filter { it.txType == TransactionType.TRANSFER }

        val totalExpense = expenses.sumOf { toBase(it) }
        val totalIncome = incomes.sumOf { toBase(it) }
        val totalTransfer = transfers.sumOf { toBase(it) }
        val net = totalIncome - totalExpense

        val sortedTimes = transactions.map { it.occurredAtMillis }.sorted()
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        val firstDate = dateFmt.format(java.util.Date(sortedTimes.first()))
        val lastDate = dateFmt.format(java.util.Date(sortedTimes.last()))
        val dateRange = if (firstDate == lastDate) firstDate else "$firstDate 至 $lastDate"

        val foreignCount = transactions.count { !it.currencyCode.equals(baseCurrencyCode, ignoreCase = true) }

        val topCategories = expenses
            .groupBy { it.category }
            .map { (cat, list) -> Triple(cat, list.sumOf { toBase(it) }, list.size) }
            .sortedByDescending { it.second }

        val topChannels = expenses
            .groupBy { it.channel }
            .map { (ch, list) -> Triple(ch, list.sumOf { toBase(it) }, list.size) }
            .sortedByDescending { it.second }

        val largestExpense = expenses.maxByOrNull { toBase(it) }
        val largestIncome = incomes.maxByOrNull { toBase(it) }

        return buildString {
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
            appendLine("• 净收支：$netPrefix$symbol%.2f".format(kotlin.math.abs(net) / 100.0))
            if (expenses.isNotEmpty()) {
                val avgExpense = totalExpense / 100.0 / expenses.size.toDouble()
                appendLine("• 笔均支出：$symbol%.2f".format(avgExpense))
            }
            largestExpense?.let { tx ->
                appendLine("• 单笔最高支出：${tx.merchant}（$symbol%.2f · ${dateFmt.format(java.util.Date(tx.occurredAtMillis))}）".format(toBase(tx) / 100.0))
            }
            largestIncome?.let { tx ->
                appendLine("• 单笔最高收入：${tx.merchant}（$symbol%.2f · ${dateFmt.format(java.util.Date(tx.occurredAtMillis))}）".format(toBase(tx) / 100.0))
            }
            if (topCategories.isNotEmpty() && totalExpense > 0L) {
                appendLine()
                appendLine("分类支出分布：")
                topCategories.take(6).forEachIndexed { idx, (cat, cents, count) ->
                    val pct = cents * 100.0 / totalExpense.toDouble()
                    appendLine("  ${idx + 1}. $cat：$symbol%.2f（%.1f%% · ${count}笔）".format(cents / 100.0, pct))
                }
            }
            if (topChannels.isNotEmpty() && totalExpense > 0L) {
                appendLine()
                appendLine("渠道支出分布：")
                topChannels.take(5).forEachIndexed { idx, (ch, cents, count) ->
                    val pct = cents * 100.0 / totalExpense.toDouble()
                    appendLine("  ${idx + 1}. $ch：$symbol%.2f（%.1f%% · ${count}笔）".format(cents / 100.0, pct))
                }
            }
        }.trim()
    }
}
