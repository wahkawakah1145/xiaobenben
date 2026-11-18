package com.example.data

import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import com.example.model.ParseMethod
import com.example.model.SourceApp
import com.example.model.TransactionType
import com.example.parser.ChinaNotificationParser
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

data class ImportPreviewResult(
    val formatLabel: String,
    val transactions: List<LedgerTransaction>,
    val skippedCount: Int
)

object ManualImportManager {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    )

    fun parseImportedContent(
        rawContent: String,
        fileNameHint: String? = null,
        customRules: List<CustomParsingRule> = emptyList()
    ): Result<ImportPreviewResult> {
        val text = rawContent.trim().removePrefix("\uFEFF")
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("导入内容为空"))
        }

        return runCatching {
            val lowerName = fileNameHint?.lowercase().orEmpty()
            when {
                lowerName.endsWith(".json") || text.startsWith("{") || text.startsWith("[") -> {
                    parseJsonContent(text)
                }
                lowerName.endsWith(".csv") || looksLikeCsv(text) -> {
                    parseCsvContent(text)
                }
                else -> {
                    parseFreeformLines(text, customRules)
                }
            }
        }
    }

    private fun parseJsonContent(text: String): ImportPreviewResult {
        val items = mutableListOf<LedgerTransaction>()
        var skipped = 0
        val array = if (text.startsWith("{")) {
            val obj = JSONObject(text)
            obj.optJSONArray("transactions") ?: JSONArray().apply { put(obj) }
        } else {
            JSONArray(text)
        }

        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i)
            if (o == null) {
                skipped++
                continue
            }
            val amountCents = when {
                o.has("amountCents") -> o.optLong("amountCents", 0L)
                o.has("amount") -> (o.optDouble("amount", 0.0) * 100.0).roundToLong()
                else -> 0L
            }
            if (amountCents <= 0L) {
                skipped++
                continue
            }
            val currency = o.optString("currencyCode")
                .ifBlank { o.optString("currency", "CNY") }
                .uppercase()
                .ifBlank { "CNY" }
            val merchant = o.optString("merchant").ifBlank { "导入账单" }
            val rawType = o.optString("type", "EXPENSE")
            val type = mapType(rawType)
            val category = o.optString("category")
                .ifBlank { ChinaNotificationParser.inferCategory(merchant, "", type) }
            val occurredAt = when {
                o.optLong("occurredAtMillis", 0L) > 0L -> o.optLong("occurredAtMillis")
                o.has("time") -> parseDateMillis(o.optString("time"))
                o.has("date") -> parseDateMillis(o.optString("date"))
                else -> System.currentTimeMillis()
            }
            items.add(
                LedgerTransaction(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    amountCents = amountCents,
                    currencyCode = currency,
                    type = type.name,
                    category = category,
                    merchant = merchant,
                    channel = o.optString("channel").ifBlank { "手动导入" },
                    cardLastFour = o.optString("cardLastFour").takeIf { it.isNotBlank() && it != "null" },
                    sourceApp = o.optString("sourceApp").ifBlank { SourceApp.IMPORT.name },
                    parseMethod = o.optString("parseMethod").ifBlank { ParseMethod.IMPORT.name },
                    rawNotification = o.optString("rawNotification").takeIf { it.isNotBlank() && it != "null" },
                    note = o.optString("note").takeIf { it.isNotBlank() && it != "null" },
                    occurredAtMillis = occurredAt,
                    confirmed = true
                )
            )
        }

        require(items.isNotEmpty()) { "JSON 中未解析出有效账单条目" }
        return ImportPreviewResult(
            formatLabel = "JSON 文件",
            transactions = items,
            skippedCount = skipped
        )
    }

    private fun looksLikeCsv(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        return lines.any { it.contains("交易时间") || it.contains("交易对方") || it.contains("金额") } &&
            lines.count { it.contains(",") } >= 2
    }

    private fun parseCsvContent(text: String): ImportPreviewResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val headerIndex = lines.indexOfFirst { line ->
            val cols = splitCsvLine(line)
            cols.size >= 3 && cols.any { it.contains("金额") } &&
                cols.any { it.contains("时间") || it.contains("日期") || it.contains("商户") || it.contains("交易对方") }
        }
        val effectiveHeaderIndex = if (headerIndex >= 0) headerIndex else 0
        val headers = splitCsvLine(lines[effectiveHeaderIndex]).map { it.trim() }

        fun findCol(vararg keys: String): Int =
            headers.indexOfFirst { h -> keys.any { k -> h.contains(k, ignoreCase = true) } }

        val timeCol = findCol("交易时间", "时间", "日期", "time", "date")
        val typeCol = findCol("收/支", "交易类型", "收支", "type")
        val merchantCol = findCol("交易对方", "商户", "对象", "merchant")
        val productCol = findCol("商品", "说明", "摘要", "备注", "note")
        val channelCol = findCol("支付方式", "收/付款方式", "渠道", "账户", "channel")
        val amountCol = findCol("金额", "amount")
        val currencyCol = findCol("币种", "货币", "currency")
        val categoryCol = findCol("交易分类", "分类", "category")

        val items = mutableListOf<LedgerTransaction>()
        var skipped = 0

        for (i in (effectiveHeaderIndex + 1) until lines.size) {
            val cols = splitCsvLine(lines[i])
            if (cols.size < 2) {
                skipped++
                continue
            }
            val rawAmount = cols.getOrNull(amountCol).orEmpty()
                .replace("¥", "")
                .replace("￥", "")
                .replace("$", "")
                .replace(",", "")
                .replace("元", "")
                .trim()
            val yuan = rawAmount.toDoubleOrNull()
            if (yuan == null || yuan <= 0.0) {
                skipped++
                continue
            }
            val amountCents = (yuan * 100.0).roundToLong().coerceAtLeast(1L)
            val rawType = cols.getOrNull(typeCol).orEmpty()
            val type = mapType(rawType)
            val merchant = cols.getOrNull(merchantCol)?.trim().orEmpty()
                .ifBlank { cols.getOrNull(productCol)?.trim().orEmpty() }
                .ifBlank { "导入账单" }
            val note = cols.getOrNull(productCol)?.trim()?.takeIf { it.isNotBlank() && it != "/" && it != merchant }
            val channel = cols.getOrNull(channelCol)?.trim().orEmpty()
                .ifBlank { "手动导入" }
            val currency = cols.getOrNull(currencyCol)?.trim()?.uppercase()
                ?.takeIf { it.length == 3 } ?: "CNY"
            val occurredAt = cols.getOrNull(timeCol)?.let { parseDateMillis(it) } ?: System.currentTimeMillis()
            val explicitCategory = cols.getOrNull(categoryCol)?.trim().orEmpty()
            val category = if (explicitCategory.isNotBlank() && explicitCategory != "/") {
                explicitCategory
            } else {
                ChinaNotificationParser.inferCategory(merchant, note.orEmpty(), type)
            }

            items.add(
                LedgerTransaction(
                    amountCents = amountCents,
                    currencyCode = currency,
                    type = type.name,
                    category = category,
                    merchant = merchant.take(80),
                    channel = channel.take(40),
                    sourceApp = SourceApp.IMPORT.name,
                    parseMethod = ParseMethod.IMPORT.name,
                    note = note?.take(120),
                    occurredAtMillis = occurredAt,
                    confirmed = true
                )
            )
        }

        require(items.isNotEmpty()) { "CSV 中未找到有效账单记录（需包含表头及有效金额列）" }
        return ImportPreviewResult(
            formatLabel = "CSV 账单",
            transactions = items,
            skippedCount = skipped
        )
    }

    private fun parseFreeformLines(
        text: String,
        customRules: List<CustomParsingRule>
    ): ImportPreviewResult {
        val items = mutableListOf<LedgerTransaction>()
        var skipped = 0
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        for (line in lines) {
            val parsed = ChinaNotificationParser.parse(
                packageName = "manual.import",
                title = "",
                content = line,
                customRules = customRules,
                enableLocalSemanticPass = true
            )
            if (parsed != null && parsed.amountCents > 0L) {
                items.add(
                    LedgerTransaction(
                        amountCents = parsed.amountCents,
                        currencyCode = parsed.currencyCode,
                        type = parsed.type.name,
                        category = parsed.category,
                        merchant = parsed.merchant,
                        channel = if (parsed.channel == "未知渠道") "手动导入" else parsed.channel,
                        cardLastFour = parsed.cardLastFour,
                        sourceApp = SourceApp.IMPORT.name,
                        parseMethod = ParseMethod.IMPORT.name,
                        rawNotification = line.take(300),
                        occurredAtMillis = System.currentTimeMillis(),
                        confirmed = true
                    )
                )
                continue
            }

            // Fallback: shorthand line like "星巴克 35.5" or "USD 12.8 ChatGPT"
            val numMatch = Regex("""([0-9]+(?:\.[0-9]{1,2})?)""").find(line)
            val yuan = numMatch?.groupValues?.get(1)?.toDoubleOrNull()
            if (yuan != null && yuan > 0.0) {
                val currency = ChinaNotificationParser.detectCurrencyCode(line)
                val merchant = line.replace(numMatch.value, "")
                    .replace(Regex("""(CNY|USD|HKD|EUR|JPY|GBP|SGD|KRW|AUD|CAD|MOP|TWD|¥|￥|\$|€|£|元)""", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { "导入记录" }
                val type = if (line.contains("收入") || line.contains("工资") || line.contains("退款")) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }
                items.add(
                    LedgerTransaction(
                        amountCents = (yuan * 100.0).roundToLong().coerceAtLeast(1L),
                        currencyCode = currency,
                        type = type.name,
                        category = ChinaNotificationParser.inferCategory(merchant, line, type),
                        merchant = merchant.take(60),
                        channel = "手动导入",
                        sourceApp = SourceApp.IMPORT.name,
                        parseMethod = ParseMethod.IMPORT.name,
                        rawNotification = line.take(300),
                        occurredAtMillis = System.currentTimeMillis(),
                        confirmed = true
                    )
                )
            } else {
                skipped++
            }
        }

        require(items.isNotEmpty()) { "未从输入文本中识别出有效的账单金额" }
        return ImportPreviewResult(
            formatLabel = "文本批量导入",
            transactions = items,
            skippedCount = skipped
        )
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun mapType(raw: String): TransactionType = when {
        raw.contains("收入") || raw.equals("INCOME", ignoreCase = true) -> TransactionType.INCOME
        raw.contains("不计收支") || raw.contains("转账") || raw.equals("TRANSFER", ignoreCase = true) ->
            TransactionType.TRANSFER
        else -> TransactionType.EXPENSE
    }

    private fun parseDateMillis(raw: String): Long {
        val cleaned = raw.trim()
        for (fmt in dateFormats) {
            runCatching {
                val parsed = fmt.parse(cleaned)
                if (parsed != null) return parsed.time
            }
        }
        return System.currentTimeMillis()
    }
}
