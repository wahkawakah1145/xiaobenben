package com.example.parser

import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class DeduplicationTimeWindow(val label: String, val maxDiffMillis: Long) {
    TEN_SECONDS("10 秒内", 10_000L),
    THIRTY_SECONDS("30 秒内", 30_000L),
    ONE_MINUTE("1 分钟内", 60_000L),
    FIVE_MINUTES("5 分钟内", 5L * 60_000L),
    FIFTEEN_MINUTES("15 分钟内", 15L * 60_000L),
    THIRTY_MINUTES("30 分钟内", 30L * 60_000L),
    ONE_HOUR("1 小时内", 3_600_000L),
    SIX_HOURS("6 小时内", 6L * 3_600_000L),
    ONE_DAY("24 小时内", 24L * 3_600_000L)
}

enum class DeduplicationChannelFilter(val label: String) {
    ALL("全部相似组"),
    CROSS_CHANNEL("仅跨渠道重复"),
    SAME_CHANNEL("仅同渠道重复")
}

data class DuplicateGroup(
    val groupId: String,
    val amountCents: Long,
    val currencyCode: String,
    val txType: TransactionType,
    val representativeMerchant: String,
    val items: List<LedgerTransaction>,
    val suggestedKeepId: String,
    val isCrossChannel: Boolean,
    val timeSpanMillis: Long,
    val similarityScore: Double,
    val reason: String
)

object SmartDeduplicator {

    const val DEFAULT_SHORT_WINDOW_MINUTES = 1
    const val DEFAULT_SHORT_WINDOW_SECONDS = 60
    const val MIN_SHORT_WINDOW_MILLIS = 1_000L
    const val MAX_SHORT_WINDOW_MILLIS = 7L * 24L * 3_600_000L

    private val bracketSuffixRegex = Regex("""[（(【\[][^）)】\]]{1,24}[）)】\]]""")
    private val brandPrefixRegex = Regex("""^(支付宝|微信支付|微信|云闪付|银联|财付通|美团|抖音支付|京东支付)[-·:：\s]+""")
    private val legalSuffixRegex = Regex("""(有限责任公司|股份有限公司|有限公司|旗舰店|专卖店|官方店|自营店|直营店|小程序|中国|分店|门店)$""")
    private val punctuationRegex = Regex("""[\s\-_.*·•/\\,:：;；'"`~!@#\$%^&+=|?<>，。！？、（）()【】\[\]{}]+""")

    fun formatWindowLabel(minutes: Int): String {
        val safe = minutes.coerceIn(1, 7 * 24 * 60)
        return formatWindowLabelSeconds(safe * 60)
    }

    fun formatWindowLabelSeconds(seconds: Int): String {
        val safe = seconds.coerceIn(1, (MAX_SHORT_WINDOW_MILLIS / 1000L).toInt())
        return when {
            safe < 60 -> "$safe 秒内"
            safe >= 3600 && safe % 3600 == 0 -> "${safe / 3600} 小时内"
            safe % 60 == 0 -> "${safe / 60} 分钟内"
            else -> "${safe / 60} 分 ${safe % 60} 秒内"
        }
    }

    fun normalizeMerchant(raw: String): String {
        var s = raw.trim().lowercase()
        s = s.replace(brandPrefixRegex, "")
        s = s.replace(bracketSuffixRegex, "")
        s = s.replace(punctuationRegex, "")
        var prev: String
        do {
            prev = s
            s = s.replace(legalSuffixRegex, "")
        } while (s != prev && s.length >= 2)
        return s.ifBlank { raw.trim().lowercase().replace(punctuationRegex, "") }
    }

    fun merchantSimilarity(firstRaw: String, secondRaw: String): Double {
        val a = normalizeMerchant(firstRaw)
        val b = normalizeMerchant(secondRaw)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0

        val shorter = min(a.length, b.length)
        val longer = max(a.length, b.length)
        if (shorter >= 2 && (a.contains(b) || b.contains(a))) {
            val ratio = shorter.toDouble() / longer.toDouble()
            return max(0.84, ratio)
        }

        val charDice = diceCoefficient(a, b)
        val editSim = 1.0 - (levenshteinDistance(a, b).toDouble() / longer.toDouble())
        return max(charDice, editSim)
    }

    fun areMerchantsSimilar(
        firstRaw: String,
        secondRaw: String,
        minSimilarity: Double = 0.58
    ): Boolean {
        return merchantSimilarity(firstRaw, secondRaw) >= minSimilarity
    }

    fun findDuplicateGroups(
        transactions: List<LedgerTransaction>,
        timeWindow: DeduplicationTimeWindow = DeduplicationTimeWindow.ONE_MINUTE,
        channelFilter: DeduplicationChannelFilter = DeduplicationChannelFilter.ALL,
        minSimilarity: Double = 0.58,
        requireMerchantSimilarity: Boolean = false
    ): List<DuplicateGroup> = findDuplicateGroups(
        transactions = transactions,
        maxWindowMillis = timeWindow.maxDiffMillis,
        channelFilter = channelFilter,
        minSimilarity = minSimilarity,
        requireMerchantSimilarity = requireMerchantSimilarity
    )

    fun findDuplicateGroups(
        transactions: List<LedgerTransaction>,
        maxWindowMillis: Long,
        channelFilter: DeduplicationChannelFilter = DeduplicationChannelFilter.ALL,
        minSimilarity: Double = 0.58,
        requireMerchantSimilarity: Boolean = false
    ): List<DuplicateGroup> {
        if (transactions.size < 2) return emptyList()

        val boundedWindow = maxWindowMillis.coerceIn(MIN_SHORT_WINDOW_MILLIS, MAX_SHORT_WINDOW_MILLIS)
        val buckets = transactions.groupBy { tx ->
            Triple(tx.amountCents, tx.currencyCode.uppercase(), tx.txType)
        }

        val groups = mutableListOf<DuplicateGroup>()

        for ((key, bucket) in buckets) {
            if (bucket.size < 2) continue
            val sorted = bucket.sortedByDescending { it.occurredAtMillis }
            val n = sorted.size
            val visited = BooleanArray(n)

            for (i in 0 until n) {
                if (visited[i]) continue
                val anchorTime = sorted[i].occurredAtMillis
                val componentIndices = mutableListOf(i)
                visited[i] = true
                val queue = ArrayDeque<Int>()
                queue.add(i)

                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    for (j in 0 until n) {
                        if (visited[j]) continue
                        val t1 = sorted[cur]
                        val t2 = sorted[j]
                        // Enforce both pairwise and anchor span <= boundedWindow so long-term repeats are never chained
                        val withinPairWindow = abs(t1.occurredAtMillis - t2.occurredAtMillis) <= boundedWindow
                        val withinSpanWindow = abs(anchorTime - t2.occurredAtMillis) <= boundedWindow
                        val merchantMatches = !requireMerchantSimilarity ||
                            areMerchantsSimilar(t1.merchant, t2.merchant, minSimilarity)
                        if (withinPairWindow && withinSpanWindow && merchantMatches) {
                            visited[j] = true
                            componentIndices.add(j)
                            queue.add(j)
                        }
                    }
                }

                if (componentIndices.size >= 2) {
                    val items = componentIndices.map { sorted[it] }.sortedByDescending { it.occurredAtMillis }
                    val distinctChannels = items.map { it.channel.trim() }.filter { it.isNotEmpty() }.distinct()
                    val isCrossChannel = distinctChannels.size > 1

                    val passesChannelFilter = when (channelFilter) {
                        DeduplicationChannelFilter.ALL -> true
                        DeduplicationChannelFilter.CROSS_CHANNEL -> isCrossChannel
                        DeduplicationChannelFilter.SAME_CHANNEL -> !isCrossChannel
                    }
                    if (!passesChannelFilter) continue

                    val bestKeep = pickSuggestedKeep(items)
                    var minSim = 1.0
                    for (a in 0 until items.size) {
                        for (b in (a + 1) until items.size) {
                            minSim = min(minSim, merchantSimilarity(items[a].merchant, items[b].merchant))
                        }
                    }
                    val span = (items.first().occurredAtMillis - items.last().occurredAtMillis).coerceAtLeast(0L)
                    val reason = buildString {
                        append("短时同金额")
                        when {
                            minSim >= 0.99 -> append(" · 对象一致")
                            requireMerchantSimilarity || minSim >= minSimilarity -> append(" · 对象相似 ${(minSim * 100).toInt()}%")
                            else -> append(" · 仅按金额与时间匹配")
                        }
                        if (isCrossChannel) {
                            append(" · 跨渠道 (${distinctChannels.joinToString("/")})")
                        } else if (distinctChannels.isNotEmpty()) {
                            append(" · ${distinctChannels.first()}")
                        }
                        append(" · 优先清理信息较少条目")
                    }
                    val repMerchant = bestKeep.merchant.ifBlank { items.first().merchant }
                    val groupId = items.joinToString("_") { it.id }.hashCode().toString()

                    groups.add(
                        DuplicateGroup(
                            groupId = groupId,
                            amountCents = key.first,
                            currencyCode = key.second,
                            txType = key.third,
                            representativeMerchant = repMerchant,
                            items = items,
                            suggestedKeepId = bestKeep.id,
                            isCrossChannel = isCrossChannel,
                            timeSpanMillis = span,
                            similarityScore = minSim,
                            reason = reason
                        )
                    )
                }
            }
        }

        return groups.sortedWith(
            compareByDescending<DuplicateGroup> { it.items.first().occurredAtMillis }
                .thenByDescending { it.items.size }
        )
    }

    fun informationRichnessScore(tx: LedgerTransaction): Int {
        var score = 0
        val merchant = tx.merchant.trim()
        val isGenericMerchant = merchant.isEmpty() ||
            merchant == "未知商户" ||
            merchant == "未知渠道消费" ||
            merchant.endsWith("消费") ||
            merchant.endsWith("收款") ||
            merchant.endsWith("转账")
        if (merchant.isNotEmpty()) {
            score += if (isGenericMerchant) 6 else 24
            score += merchant.length.coerceAtMost(24)
        }
        val product = tx.productName?.trim().orEmpty()
        if (product.isNotEmpty()) score += 20 + (product.length / 4).coerceAtMost(8)
        if (!tx.transactionNo.isNullOrBlank()) score += 18
        if (!tx.merchantOrderNo.isNullOrBlank()) score += 14
        val note = tx.note?.trim().orEmpty()
        if (note.isNotEmpty()) score += 16 + (note.length / 6).coerceAtMost(8)
        val raw = tx.rawNotification?.trim().orEmpty()
        if (raw.isNotEmpty()) score += 14 + (raw.length / 16).coerceAtMost(10)
        if (!tx.tradeType.isNullOrBlank()) score += 10
        if (!tx.tradeStatus.isNullOrBlank()) score += 8
        if (!tx.cardLastFour.isNullOrBlank()) score += 10
        if (tx.balanceAfterCents != null) score += 10
        val category = tx.category.trim()
        if (category.isNotEmpty() && category != "其他" && category != "未分类") score += 12
        val channel = tx.channel.trim()
        if (channel.isNotEmpty() && channel != "未知渠道" && channel != "手动记账") {
            score += 10
            if (channel.contains('&') || channel.contains('+')) score += 4
        }
        if (tx.confirmed) score += 6
        return score
    }

    private fun pickSuggestedKeep(items: List<LedgerTransaction>): LedgerTransaction {
        return items.maxWithOrNull(
            compareBy<LedgerTransaction> { informationRichnessScore(it) }
                .thenBy { it.confirmed }
                .thenBy { !it.rawNotification.isNullOrBlank() }
                .thenBy { !it.note.isNullOrBlank() }
                .thenBy { it.merchant.length }
                .thenByDescending { it.occurredAtMillis }
        ) ?: items.first()
    }

    private fun diceCoefficient(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.length == 1 && b.length == 1) return if (a == b) 1.0 else 0.0
        val bigramsA = if (a.length < 2) listOf(a) else (0 until a.length - 1).map { a.substring(it, it + 2) }
        val bigramsB = if (b.length < 2) listOf(b) else (0 until b.length - 1).map { b.substring(it, it + 2) }
        val countsB = bigramsB.groupingBy { it }.eachCount().toMutableMap()
        var matches = 0
        for (bg in bigramsA) {
            val count = countsB[bg] ?: 0
            if (count > 0) {
                matches++
                countsB[bg] = count - 1
            }
        }
        return (2.0 * matches) / (bigramsA.size + bigramsB.size).toDouble()
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n]
    }
}
