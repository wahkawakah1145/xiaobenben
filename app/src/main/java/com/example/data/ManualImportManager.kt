package com.example.data

import com.example.model.CustomParsingRule
import com.example.model.LedgerTransaction
import com.example.model.ParseMethod
import com.example.model.SourceApp
import com.example.model.TransactionType
import com.example.parser.ChinaNotificationParser
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

data class ImportPreviewResult(
    val formatLabel: String,
    val transactions: List<LedgerTransaction>,
    val skippedCount: Int,
    val detectedHeaderRowOneBased: Int? = null,
    val detectedColumnsSummary: String = ""
)

object ManualImportManager {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd", Locale.CHINA),
        SimpleDateFormat("yyyyMMdd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    )

    private data class ColumnMapping(
        val headerRowIndex: Int,
        val score: Int,
        val headers: List<String>,
        val timeCol: Int,
        val typeCol: Int,
        val merchantCol: Int,
        val productCol: Int,
        val channelCol: Int,
        val amountCol: Int,
        val expenseAmountCol: Int,
        val incomeAmountCol: Int,
        val currencyCol: Int,
        val categoryCol: Int
    ) {
        val hasUsableAmountColumn: Boolean
            get() = amountCol >= 0 || expenseAmountCol >= 0 || incomeAmountCol >= 0
    }

    fun parseImportedBytes(
        bytes: ByteArray,
        fileNameHint: String? = null,
        customRules: List<CustomParsingRule> = emptyList()
    ): Result<ImportPreviewResult> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("所选文件内容为空"))
        }
        val lowerName = fileNameHint?.lowercase().orEmpty()
        if (lowerName.endsWith(".xlsx") || XlsxTableReader.isXlsxBytes(bytes)) {
            return runCatching { parseXlsxBytes(bytes) }
        }
        val decodedText = decodeTextAutoCharset(bytes)
        return parseImportedContent(decodedText, fileNameHint, customRules)
    }

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
                lowerName.endsWith(".csv") || lowerName.endsWith(".tsv") || looksLikeDelimitedTable(text) -> {
                    parseCsvContent(text)
                }
                else -> {
                    parseFreeformLines(text, customRules)
                }
            }
        }
    }

    private fun decodeTextAutoCharset(bytes: ByteArray): String {
        val cleanBytes = if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return runCatching {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(cleanBytes)).toString()
        }.getOrElse {
            runCatching {
                String(cleanBytes, Charset.forName("GB18030"))
            }.getOrElse {
                String(cleanBytes, Charsets.UTF_8)
            }
        }
    }

    private fun parseXlsxBytes(bytes: ByteArray): ImportPreviewResult {
        val sheets = XlsxTableReader.readSheets(bytes)
        require(sheets.isNotEmpty()) { "XLSX 文件中未找到可读工作表" }

        var bestSheet: XlsxSheetTable? = null
        var bestMapping: ColumnMapping? = null
        for (sheet in sheets) {
            val mapping = detectHeaderRow(sheet.rows) ?: continue
            if (bestMapping == null || mapping.score > bestMapping.score) {
                bestMapping = mapping
                bestSheet = sheet
            }
        }

        val chosenSheet = bestSheet ?: sheets.first()
        val chosenMapping = bestMapping ?: detectHeaderRow(chosenSheet.rows, allowWeakFallback = true)
            ?: throw IllegalArgumentException("未在 XLSX 表格中找到有效表头（需包含金额、商户/时间等列）")

        return buildTableImportResult(
            rows = chosenSheet.rows,
            mapping = chosenMapping,
            baseFormatLabel = if (sheets.size > 1) "XLSX (${chosenSheet.sheetName})" else "XLSX 表格",
            fromExcel = true
        )
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

    private fun looksLikeDelimitedTable(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }.take(80)
        if (lines.size < 2) return false
        val delimiter = detectDelimiter(lines)
        val multiColLines = lines.count { splitDelimitedLine(it, delimiter).size >= 3 }
        if (multiColLines < 2) return false
        val rows = lines.map { splitDelimitedLine(it, delimiter) }
        return detectHeaderRow(rows) != null || lines.any {
            it.contains("金额") || it.contains("交易时间") || it.contains("交易对方") ||
                it.contains("amount", ignoreCase = true)
        }
    }

    private fun parseCsvContent(text: String): ImportPreviewResult {
        val rawLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(rawLines.isNotEmpty()) { "CSV 内容为空" }
        val delimiter = detectDelimiter(rawLines.take(80))
        val rows = rawLines.map { splitDelimitedLine(it, delimiter) }

        val mapping = detectHeaderRow(rows)
            ?: detectHeaderRow(rows, allowWeakFallback = true)
            ?: throw IllegalArgumentException("CSV 中未找到有效表头（需包含金额、时间/商户等列）")

        return buildTableImportResult(
            rows = rows,
            mapping = mapping,
            baseFormatLabel = if (delimiter == '\t') "TSV 表格" else "CSV 账单",
            fromExcel = false
        )
    }

    private fun buildTableImportResult(
        rows: List<List<String>>,
        mapping: ColumnMapping,
        baseFormatLabel: String,
        fromExcel: Boolean
    ): ImportPreviewResult {
        val items = mutableListOf<LedgerTransaction>()
        var skipped = 0

        for (i in (mapping.headerRowIndex + 1) until rows.size) {
            val cols = rows[i]
            if (cols.count { it.isNotBlank() } < 2) {
                skipped++
                continue
            }

            val resolvedAmountAndType = resolveAmountAndType(cols, mapping)
            if (resolvedAmountAndType == null) {
                skipped++
                continue
            }
            val (amountCents, inferredType) = resolvedAmountAndType
            val rawType = cols.getOrNull(mapping.typeCol).orEmpty()
            val type = if (rawType.isNotBlank()) mapType(rawType, inferredType) else inferredType

            val merchant = cols.getOrNull(mapping.merchantCol)?.trim().orEmpty()
                .ifBlank { cols.getOrNull(mapping.productCol)?.trim().orEmpty() }
                .ifBlank { "导入账单" }
            val note = cols.getOrNull(mapping.productCol)?.trim()
                ?.takeIf { it.isNotBlank() && it != "/" && it != merchant }
            val channel = cols.getOrNull(mapping.channelCol)?.trim().orEmpty()
                .ifBlank { "手动导入" }
            val currency = resolveCurrencyCode(cols.getOrNull(mapping.currencyCol))
            val rawTime = cols.getOrNull(mapping.timeCol).orEmpty()
            val occurredAt = parseDateMillis(rawTime, allowExcelSerial = fromExcel)

            val explicitCategory = cols.getOrNull(mapping.categoryCol)?.trim().orEmpty()
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
                    category = category.take(40),
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

        require(items.isNotEmpty()) { "$baseFormatLabel 中未找到有效账单记录（已定位第 ${mapping.headerRowIndex + 1} 行为表头）" }

        val mappedColumns = buildList {
            if (mapping.timeCol >= 0) add("时间:${mapping.headers.getOrNull(mapping.timeCol)}")
            if (mapping.merchantCol >= 0) add("对象:${mapping.headers.getOrNull(mapping.merchantCol)}")
            if (mapping.amountCol >= 0) add("金额:${mapping.headers.getOrNull(mapping.amountCol)}")
            if (mapping.expenseAmountCol >= 0) add("支出:${mapping.headers.getOrNull(mapping.expenseAmountCol)}")
            if (mapping.incomeAmountCol >= 0) add("收入:${mapping.headers.getOrNull(mapping.incomeAmountCol)}")
            if (mapping.channelCol >= 0) add("渠道:${mapping.headers.getOrNull(mapping.channelCol)}")
        }.joinToString(" · ")

        return ImportPreviewResult(
            formatLabel = "$baseFormatLabel · 自动定位第 ${mapping.headerRowIndex + 1} 行为表头",
            transactions = items,
            skippedCount = skipped,
            detectedHeaderRowOneBased = mapping.headerRowIndex + 1,
            detectedColumnsSummary = mappedColumns
        )
    }

    private fun detectHeaderRow(
        rows: List<List<String>>,
        allowWeakFallback: Boolean = false
    ): ColumnMapping? {
        val scanLimit = minOf(rows.size, 80)
        var best: ColumnMapping? = null

        for (r in 0 until scanLimit) {
            val row = rows[r].map { it.trim().removePrefix("\uFEFF") }
            val nonEmptyCount = row.count { it.isNotBlank() }
            if (nonEmptyCount < 2) continue

            val used = mutableSetOf<Int>()
            fun findBestCol(
                exactKeys: List<String>,
                containsKeys: List<String>,
                excludeKeys: List<String> = emptyList()
            ): Int {
                for (key in exactKeys) {
                    val lowerKey = key.lowercase()
                    row.forEachIndexed { idx, cell ->
                        if (idx in used || cell.isBlank() || cell.length > 28) return@forEachIndexed
                        val normalized = cell.lowercase().replace(" ", "")
                        if (excludeKeys.any { normalized.contains(it.lowercase()) }) return@forEachIndexed
                        if (normalized == lowerKey) {
                            used.add(idx)
                            return idx
                        }
                    }
                }
                for (key in containsKeys) {
                    val lowerKey = key.lowercase()
                    row.forEachIndexed { idx, cell ->
                        if (idx in used || cell.isBlank() || cell.length > 28) return@forEachIndexed
                        val normalized = cell.lowercase().replace(" ", "")
                        if (excludeKeys.any { normalized.contains(it.lowercase()) }) return@forEachIndexed
                        if (normalized.contains(lowerKey)) {
                            used.add(idx)
                            return idx
                        }
                    }
                }
                return -1
            }

            val timeCol = findBestCol(
                exactKeys = listOf("交易时间", "记账时间", "创建时间", "入账时间", "日期", "时间", "time", "date", "datetime"),
                containsKeys = listOf("交易时间", "记账时间", "日期", "时间", "date", "time")
            )
            val expenseAmountCol = findBestCol(
                exactKeys = listOf("支出金额", "支出", "借方金额", "付款金额", "expense", "debit"),
                containsKeys = listOf("支出金额", "借方金额", "付款金额", "支出(")
            )
            val incomeAmountCol = findBestCol(
                exactKeys = listOf("收入金额", "收入", "贷方金额", "收款金额", "income", "credit"),
                containsKeys = listOf("收入金额", "贷方金额", "收款金额", "收入(")
            )
            val amountCol = findBestCol(
                exactKeys = listOf("金额", "交易金额", "实付金额", "金额(元)", "amount", "money", "total"),
                containsKeys = listOf("金额", "发生额", "实付", "amount", "price", "value"),
                excludeKeys = listOf("余额", "balance")
            )
            val typeCol = findBestCol(
                exactKeys = listOf("收/支", "收支", "收支类型", "交易类型", "类型", "type", "direction"),
                containsKeys = listOf("收/支", "收支", "交易类型", "借贷", "type")
            )
            val merchantCol = findBestCol(
                exactKeys = listOf("交易对方", "商户", "商户名称", "对方名称", "交易对象", "收款方", "merchant", "payee", "counterparty"),
                containsKeys = listOf("交易对方", "商户", "交易对象", "对方", "payee", "merchant")
            )
            val productCol = findBestCol(
                exactKeys = listOf("商品", "商品名称", "商品说明", "摘要", "备注", "说明", "note", "memo"),
                containsKeys = listOf("商品", "摘要", "备注", "说明", "note", "memo", "desc")
            )
            val channelCol = findBestCol(
                exactKeys = listOf("支付方式", "收/付款方式", "付款方式", "渠道", "账户", "交易渠道", "channel", "account", "method"),
                containsKeys = listOf("支付方式", "付款方式", "渠道", "账户", "银行", "channel", "account")
            )
            val currencyCol = findBestCol(
                exactKeys = listOf("币种", "货币", "currency"),
                containsKeys = listOf("币种", "货币", "currency")
            )
            val categoryCol = findBestCol(
                exactKeys = listOf("交易分类", "分类", "类别", "category"),
                containsKeys = listOf("分类", "类别", "category")
            )

            val candidate = ColumnMapping(
                headerRowIndex = r,
                score = 0,
                headers = row,
                timeCol = timeCol,
                typeCol = typeCol,
                merchantCol = merchantCol,
                productCol = productCol,
                channelCol = channelCol,
                amountCol = amountCol,
                expenseAmountCol = expenseAmountCol,
                incomeAmountCol = incomeAmountCol,
                currencyCol = currencyCol,
                categoryCol = categoryCol
            )
            if (!candidate.hasUsableAmountColumn) continue

            var score = 0
            if (amountCol >= 0) score += 4
            if (expenseAmountCol >= 0) score += 3
            if (incomeAmountCol >= 0) score += 3
            if (timeCol >= 0) score += 3
            if (merchantCol >= 0) score += 3
            if (typeCol >= 0) score += 2
            if (channelCol >= 0) score += 2
            if (productCol >= 0) score += 1
            if (categoryCol >= 0) score += 1
            if (currencyCol >= 0) score += 1

            // Verify there is at least one parseable amount row below this header
            val hasDataBelow = ((r + 1) until minOf(rows.size, r + 15)).any { nextIdx ->
                resolveAmountAndType(rows[nextIdx], candidate) != null
            }
            if (hasDataBelow) score += 5

            val minScore = if (allowWeakFallback) 4 else 7
            if (score >= minScore && (best == null || score > best.score)) {
                best = candidate.copy(score = score)
            }
        }
        return best
    }

    private fun resolveAmountAndType(
        cols: List<String>,
        mapping: ColumnMapping
    ): Pair<Long, TransactionType>? {
        val rawAmountText = cols.getOrNull(mapping.amountCol)?.trim()
        val mainRaw = cleanNumericAmount(rawAmountText)
        if (mainRaw != null && abs(mainRaw) > 0.0) {
            val type = when {
                mainRaw < 0 -> TransactionType.EXPENSE
                rawAmountText?.startsWith("+") == true -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
            val cents = (abs(mainRaw) * 100.0).roundToLong().coerceAtLeast(1L)
            return cents to type
        }
        val expRaw = cleanNumericAmount(cols.getOrNull(mapping.expenseAmountCol))
        if (expRaw != null && abs(expRaw) > 0.0) {
            val cents = (abs(expRaw) * 100.0).roundToLong().coerceAtLeast(1L)
            return cents to TransactionType.EXPENSE
        }
        val incRaw = cleanNumericAmount(cols.getOrNull(mapping.incomeAmountCol))
        if (incRaw != null && abs(incRaw) > 0.0) {
            val cents = (abs(incRaw) * 100.0).roundToLong().coerceAtLeast(1L)
            return cents to TransactionType.INCOME
        }
        return null
    }

    private fun cleanNumericAmount(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace("¥", "")
            .replace("￥", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("HK$", "", ignoreCase = true)
            .replace("USD", "", ignoreCase = true)
            .replace("CNY", "", ignoreCase = true)
            .replace(",", "")
            .replace("元", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun resolveCurrencyCode(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "/") return "CNY"
        val upper = text.uppercase()
        if (upper.length == 3 && upper.all { it in 'A'..'Z' }) return upper
        return when {
            text.contains("美元") || text.contains("美金") -> "USD"
            text.contains("港币") || text.contains("港元") -> "HKD"
            text.contains("欧元") -> "EUR"
            text.contains("日元") -> "JPY"
            text.contains("英镑") -> "GBP"
            else -> "CNY"
        }
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

    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(40)
        val commas = sample.sumOf { it.count { ch -> ch == ',' } }
        val tabs = sample.sumOf { it.count { ch -> ch == '\t' } }
        val semis = sample.sumOf { it.count { ch -> ch == ';' } }
        return when {
            tabs > commas && tabs >= sample.size -> '\t'
            semis > commas && semis >= sample.size -> ';'
            else -> ','
        }
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun mapType(raw: String, fallback: TransactionType = TransactionType.EXPENSE): TransactionType = when {
        raw.contains("收入") || raw.contains("收款") || raw.contains("退") ||
            raw.equals("INCOME", ignoreCase = true) || raw.contains("贷") -> TransactionType.INCOME
        raw.contains("不计收支") || raw.contains("转账") ||
            raw.equals("TRANSFER", ignoreCase = true) -> TransactionType.TRANSFER
        raw.contains("支出") || raw.contains("付款") || raw.contains("消费") ||
            raw.equals("EXPENSE", ignoreCase = true) || raw.contains("借") -> TransactionType.EXPENSE
        else -> fallback
    }

    private fun parseDateMillis(raw: String, allowExcelSerial: Boolean = false): Long {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return System.currentTimeMillis()
        if (allowExcelSerial) {
            cleaned.toDoubleOrNull()?.let { serial ->
                val formatted = XlsxTableReader.excelSerialToFormattedDate(serial)
                if (formatted != null) {
                    for (fmt in dateFormats) {
                        runCatching {
                            val parsed = fmt.parse(formatted)
                            if (parsed != null) return parsed.time
                        }
                    }
                }
            }
        }
        val normalized = cleaned.replace('T', ' ').substringBefore('.').trim()
        for (fmt in dateFormats) {
            runCatching {
                val parsed = fmt.parse(normalized)
                if (parsed != null) return parsed.time
            }
        }
        return System.currentTimeMillis()
    }
}
