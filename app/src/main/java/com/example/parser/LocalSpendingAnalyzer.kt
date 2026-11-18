package com.example.parser

import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import java.util.Calendar

object LocalSpendingAnalyzer {

    fun generateMonthlySummary(
        monthLabel: String,
        transactions: List<LedgerTransaction>,
        monthlyBudgetCents: Long
    ): String {
        if (transactions.isEmpty()) {
            return "当前 $monthLabel 暂无账单记录，记账后即可生成本地消费分析。"
        }

        val expenses = transactions.filter { it.txType == TransactionType.EXPENSE }
        val incomes = transactions.filter { it.txType == TransactionType.INCOME }
        val totalExpense = expenses.sumOf { it.amountCents }
        val totalIncome = incomes.sumOf { it.amountCents }

        val topCategory = expenses
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
            .maxByOrNull { it.value }

        val topChannel = expenses
            .groupBy { it.channel }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
            .maxByOrNull { it.value }

        val largestTx = expenses.maxByOrNull { it.amountCents }
        val daysInMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val dailyAvgYuan = (totalExpense / 100.0) / daysInMonth

        return buildString {
            appendLine("1. 收支与预算概况：${monthLabel}累计支出 ¥%.2f（日均约 ¥%.2f），收入 ¥%.2f。".format(
                totalExpense / 100.0,
                dailyAvgYuan,
                totalIncome / 100.0
            ))
            if (monthlyBudgetCents > 0L) {
                val pct = (totalExpense * 100.0 / monthlyBudgetCents).toInt()
                val statusHint = when {
                    pct >= 100 -> "已超出预算 ${pct - 100}%，建议减少非必要消费。"
                    pct >= 80 -> "已使用预算 $pct%，接近上限，请注意控制大额支出。"
                    else -> "当前预算使用率 $pct%，整体消费节奏稳健。"
                }
                appendLine("2. 预算执行：$statusHint")
            }
            if (topCategory != null && totalExpense > 0L) {
                val catPct = (topCategory.value * 100.0 / totalExpense).toInt()
                val catYuan = "%.2f".format(topCategory.value / 100.0)
                val chHint = topChannel?.let { "；主要支付渠道为「${it.key}」（¥%.2f）".format(it.value / 100.0) }.orEmpty()
                val maxHint = largestTx?.let { "；单笔最高消费为「${it.merchant}」¥%.2f".format(it.amountCents / 100.0) }.orEmpty()
                append("3. 结构重心：支出最高分类为「${topCategory.key}」（占比 $catPct%，¥$catYuan）$chHint$maxHint。")
            }
        }.trim()
    }
}
