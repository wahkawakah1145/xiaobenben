package com.example.parser

import com.example.data.CurrencyExchangeManager
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
    ): String {
        val confirmedTx = transactions.filter { it.confirmed }
        if (confirmedTx.isEmpty()) {
            return "当前 $monthLabel 暂无已确认账单，记账后即可查看分析。"
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
        val daysInMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val dailyAvgYuan = (totalExpense / 100.0) / daysInMonth

        return buildString {
            appendLine(
                "1. 收支概况：${monthLabel}累计支出 $symbol%.2f（日均约 $symbol%.2f），收入 $symbol%.2f。".format(
                    totalExpense / 100.0,
                    dailyAvgYuan,
                    totalIncome / 100.0
                )
            )
            if (monthlyBudgetCents > 0L) {
                val pct = (totalExpense * 100.0 / monthlyBudgetCents).toInt()
                val statusHint = when {
                    pct >= 100 -> "已超出预算 ${pct - 100}%，建议收紧非必要开支。"
                    pct >= 80 -> "已使用预算 $pct%，接近上限。"
                    else -> "当前预算使用率 $pct%，节奏平稳。"
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
}
