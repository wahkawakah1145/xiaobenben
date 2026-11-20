package com.example.parser

import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class DeduplicationTimeWindow(val label: String, val maxDiffMillis: Long) {
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
    const val MAX_SHORT_WINDOW_MILLIS = 7L * 24L * 3_600_000L

    private val bracketSuffixRegex = Regex("""[（(【\[][^）)】\]]{1,24}[）)】\]]""")
    private val brandPrefixRegex = Regex("""^(支付宝|微信支付|微信|云闪付|银联|财付通|美团|抖音支付|京东支付)[-·:：\s]+""")
    private val legalSuffixRegex = Regex("""(有限责任公司|股份有限公司|有限公司|旗舰店|专卖店|官方店|自营店|直营店|小程序|中国|分店|门店)$""")
    private val punctuationRegex = Regex("""[\s\-_.*·•/\\,:：;；'"`~!@#\$%^&+=|?<>，。！？、（）()【】\[\]{}]+""")

    fun formatWindowLabel(minutes: Int): String {
        val safe = minutes.coerceIn(1, 7 * 24 * 60)
        return when {
            safe >= 60 && safe % 60 == 0 -> "${safe / 60} 小时内"
            else -> "$safe 分钟内"
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
        minSimilarity: Double = 0.58
    ): List<DuplicateGroup> = findDuplicateGroups(
        transactions = transactions,
        maxWindowMillis = timeWindow.maxDiffMillis,
        channelFilter = channelFilter,
        minSimilarity = minSimilarity
    )

    fun findDuplicateGroups(
        transactions: List<LedgerTransaction>,
        maxWindowMillis: Long,
        channelFilter: DeduplicationChannelFilter = DeduplicationChannelFilter.ALL,
        minSimilarity: Double = 0.58
    ): List<DuplicateGroup> {
        if (transactions.size < 2) return emptyList()

        val boundedWindow = maxWindowMillis.coerceIn(60_000L, MAX_SHORT_WINDOW_MILLIS)
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
                        if (withinPairWindow && withinSpanWindow &&
                            areMerchantsSimilar(t1.merchant, t2.merchant, minSimilarity)
                        ) {
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
                        if (minSim >= 0.99) append(" · 对象一致") else append(" · 对象相似度 ${(minSim * 100).toInt()}%")
                        if (isCrossChannel) {
                            append(" · 跨渠道 (${distinctChannels.joinToString("/")})")
                        } else if (distinctChannels.isNotEmpty()) {
                            append(" · ${distinctChannels.first()}")
                        }
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

    private fun pickSuggestedKeep(items: List<LedgerTransaction>): LedgerTransaction {
        return items.maxWithOrNull(
            compareBy<LedgerTransaction> { it.confirmed }
                .thenBy { !it.note.isNullOrBlank() }
                .thenBy { !it.rawNotification.isNullOrBlank() }
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
