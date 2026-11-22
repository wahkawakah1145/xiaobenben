package com.example.data

import com.example.model.CurrencyCatalog
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
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

data class ImportPreviewResult(
    val formatLabel: String,
    val transactions: List<LedgerTransaction>,
    val skippedCount: Int,
    val detectedHeaderRowOneBased: Int? = null,
    val detectedColumnsSummary: String = "",
    val duplicateIds: Set<String> = emptySet(),
    val duplicateReasons: Map<String, String> = emptyMap()
) {
    val newTransactions: List<LedgerTransaction>
        get() = if (duplicateIds.isEmpty()) transactions else transactions.filter { it.id !in duplicateIds }

    val duplicateCount: Int
        get() = duplicateIds.size

    fun isSelfTransferExcluded(tx: LedgerTransaction): Boolean {
        if (tx.txType == TransactionType.TRANSFER) return true
        val cat = tx.category.trim()
        if (cat == "不计收支" || cat == "账户互转") return true
        val combined = listOfNotNull(
            tx.merchant,
            tx.productName,
            tx.tradeType,
            tx.note,
            tx.rawNotification
        ).joinToString(" ")
        return SELF_TRANSFER_EXCLUDE_KEYWORDS.any { combined.contains(it, ignoreCase = true) }
    }

    fun defaultExclusionTag(tx: LedgerTransaction): String? = when {
        tx.id in duplicateIds -> "重复"
        isSelfTransferExcluded(tx) -> "本人互转/不计收支"
        else -> null
    }

    fun defaultExclusionDetail(tx: LedgerTransaction): String? = when {
        tx.id in duplicateIds -> duplicateReasons[tx.id]?.takeIf { it.isNotBlank() } ?: "与已有或同批账单重复"
        isSelfTransferExcluded(tx) -> "属于本人互转/提现/内部划转，默认不勾选"
        else -> null
    }

    val selfTransferExcludedCount: Int
        get() = transactions.count { it.id !in duplicateIds && isSelfTransferExcluded(it) }

    val recommendedTransactions: List<LedgerTransaction>
        get() = transactions.filter { defaultExclusionTag(it) == null }

    fun defaultSelectedIds(): Set<String> =
        recommendedTransactions.mapTo(LinkedHashSet()) { it.id }

    companion object {
        private val SELF_TRANSFER_EXCLUDE_KEYWORDS = listOf(
            "本人互转",
            "本人转账",
            "同名转账",
            "同名账户",
            "账户互转",
            "内部转账",
            "零钱提现",
            "余额提现",
            "提现到银行卡",
            "提现至银行卡",
            "零钱充值",
            "零钱通转入",
            "零钱通转出",
            "余额宝转入",
            "余额宝转出",
            "不计收支"
        )
    }
}

object ManualImportManager {

    private val dateTimePatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-M-d H:mm:ss",
        "yyyy-M-d H:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy/M/d H:mm:ss",
        "yyyy/M/d H:mm",
        "yyyy.MM.dd HH:mm:ss",
        "yyyy.MM.dd HH:mm",
        "yyyy年MM月dd日 HH:mm:ss",
        "yyyy年MM月dd日 HH:mm",
        "yyyy年M月d日 H:mm:ss",
        "yyyy年M月d日 H:mm",
        "yyyyMMdd HH:mm:ss",
        "yyyyMMdd HH:mm",
        "yyyyMMddHHmmss",
        "yyyyMMddHHmm",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "yyyy.MM.dd",
        "yyyy年MM月dd日",
        "yyyyMMdd"
    )

    private data class ColumnMapping(
        val headerRowIndex: Int,
        val score: Int,
        val headers: List<String>,
        val dateCol: Int,
        val timeCol: Int,
        val tradeTypeCol: Int,
        val typeCol: Int,
        val merchantCol: Int,
        val productCol: Int,
        val channelCol: Int,
        val amountCol: Int,
        val expenseAmountCol: Int,
        val incomeAmountCol: Int,
        val balanceCol: Int,
        val currencyCol: Int,
        val categoryCol: Int,
        val statusCol: Int,
        val transactionNoCol: Int,
        val merchantOrderNoCol: Int,
        val noteCol: Int
    ) {
        val hasUsableAmountColumn: Boolean
            get() = amountCol >= 0 || expenseAmountCol >= 0 || incomeAmountCol >= 0
    }

    /**
     * Marks imported entries that duplicate either existing database records or earlier entries
     * within the same import batch.
     */
    fun annotateDuplicates(
        preview: ImportPreviewResult,
        existingTransactions: List<LedgerTransaction>
    ): ImportPreviewResult {
        if (preview.transactions.isEmpty()) return preview

        val existingTxNos = HashSet<String>(existingTransactions.size)
        val existingOrderNos = HashSet<String>(existingTransactions.size)
        val existingBuckets = HashMap<Triple<Long, String, String>, MutableList<LedgerTransaction>>()

        existingTransactions.forEach { tx ->
            normalizeOrderNo(tx.transactionNo)?.let { existingTxNos.add(it) }
            normalizeOrderNo(tx.merchantOrderNo)?.let { existingOrderNos.add(it) }
            val key = Triple(tx.amountCents, tx.currencyCode.uppercase(Locale.US), tx.type.uppercase(Locale.US))
            existingBuckets.getOrPut(key) { mutableListOf() }.add(tx)
        }

        val batchTxNos = HashSet<String>()
        val batchOrderNos = HashSet<String>()
        val batchBuckets = HashMap<Triple<Long, String, String>, MutableList<LedgerTransaction>>()

        val duplicateIds = LinkedHashSet<String>()
        val duplicateReasons = LinkedHashMap<String, String>()

        for (candidate in preview.transactions) {
            val candTxNo = normalizeOrderNo(candidate.transactionNo)
            val candOrderNo = normalizeOrderNo(candidate.merchantOrderNo)
            val key = Triple(
                candidate.amountCents,
                candidate.currencyCode.uppercase(Locale.US),
                candidate.type.uppercase(Locale.US)
            )

            var reason: String? = null
            when {
                candTxNo != null && candTxNo in existingTxNos -> {
                    reason = "与已有账单交易单号/日志号一致"
                }
                candOrderNo != null && candOrderNo in existingOrderNos -> {
                    reason = "与已有账单商户单号一致"
                }
                existingBuckets[key]?.any { existing -> isLikelySameTransaction(candidate, existing) } == true -> {
                    reason = "与已有账单时间/金额/商户重复"
                }
                candTxNo != null && candTxNo in batchTxNos -> {
                    reason = "导入内容内交易单号/日志号重复"
                }
                candOrderNo != null && candOrderNo in batchOrderNos -> {
                    reason = "导入内容内商户单号重复"
                }
                batchBuckets[key]?.any { earlier -> isLikelySameTransaction(candidate, earlier) } == true -> {
                    reason = "导入内容内重复条目"
                }
            }

            if (reason != null) {
                duplicateIds.add(candidate.id)
                duplicateReasons[candidate.id] = reason
            } else {
                candTxNo?.let { batchTxNos.add(it) }
                candOrderNo?.let { batchOrderNos.add(it) }
                batchBuckets.getOrPut(key) { mutableListOf() }.add(candidate)
            }
        }

        return preview.copy(
            duplicateIds = duplicateIds,
            duplicateReasons = duplicateReasons
        )
    }

    private fun normalizeOrderNo(raw: String?): String? {
        val cleaned = raw?.trim()?.replace(Regex("""\s+"""), "")?.uppercase(Locale.US).orEmpty()
        return cleaned.takeIf { it.length >= 4 }
    }

    private fun normalizeCompareText(raw: String?): String =
        raw?.trim()?.lowercase(Locale.ROOT)?.replace(Regex("""[\s\-_()（）·•]+"""), "").orEmpty()

    private fun isLikelySameTransaction(a: LedgerTransaction, b: LedgerTransaction): Boolean {
        val diffMs = abs(a.occurredAtMillis - b.occurredAtMillis)
        if (diffMs > 60_000L) return false

        // If both carry distinct non-blank transaction numbers, treat them as separate bills.
        val aNo = normalizeOrderNo(a.transactionNo)
        val bNo = normalizeOrderNo(b.transactionNo)
        if (aNo != null && bNo != null && aNo != bNo) return false

        // If both carry balanceAfterCents at the exact same second and balances differ, they are distinct.
        if (a.balanceAfterCents != null && b.balanceAfterCents != null && a.balanceAfterCents != b.balanceAfterCents) {
            return false
        }

        val ma = normalizeCompareText(a.merchant)
        val mb = normalizeCompareText(b.merchant)
        val merchantMatch = ma.isNotEmpty() && mb.isNotEmpty() && (ma == mb || ma.contains(mb) || mb.contains(ma))
        if (merchantMatch) return true

        val ca = normalizeCompareText(a.channel)
        val cb = normalizeCompareText(b.channel)
        val channelMatch = ca.isNotEmpty() && cb.isNotEmpty() && (ca == cb || ca.contains(cb) || cb.contains(ca))
        val sameSecond = (a.occurredAtMillis / 1000L) == (b.occurredAtMillis / 1000L)
        return sameSecond && channelMatch
    }

    fun parseImportedBytes(
        bytes: ByteArray,
        fileNameHint: String? = null,
        customRules: List<CustomParsingRule> = emptyList(),
        useRuleMatching: Boolean = true
    ): Result<ImportPreviewResult> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("所选文件内容为空"))
        }
        val lowerName = fileNameHint?.lowercase().orEmpty()
        if (lowerName.endsWith(".xlsx") || XlsxTableReader.isXlsxBytes(bytes)) {
            return runCatching {
                parseXlsxBytes(
                    bytes = bytes,
                    customRules = if (useRuleMatching) customRules else emptyList(),
                    useRuleMatching = useRuleMatching
                )
            }
        }
        val decodedText = decodeTextAutoCharset(bytes)
        return parseImportedContent(decodedText, fileNameHint, customRules, useRuleMatching)
    }

    fun parseImportedContent(
        rawContent: String,
        fileNameHint: String? = null,
        customRules: List<CustomParsingRule> = emptyList(),
        useRuleMatching: Boolean = true
    ): Result<ImportPreviewResult> {
        val text = rawContent.trim().removePrefix("\uFEFF")
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("导入内容为空"))
        }

        val activeRules = if (useRuleMatching) customRules else emptyList()
        return runCatching {
            val lowerName = fileNameHint?.lowercase().orEmpty()
            when {
                lowerName.endsWith(".json") || text.startsWith("{") || text.startsWith("[") -> {
                    parseJsonContent(text, activeRules, useRuleMatching)
                }
                lowerName.endsWith(".csv") || lowerName.endsWith(".tsv") || looksLikeDelimitedTable(text) -> {
                    parseCsvContent(text, activeRules, useRuleMatching)
                }
                else -> {
                    parseFreeformLines(text, activeRules, useRuleMatching)
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

    private fun parseXlsxBytes(
        bytes: ByteArray,
        customRules: List<CustomParsingRule>,
        useRuleMatching: Boolean
    ): ImportPreviewResult {
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
            fromExcel = true,
            customRules = customRules,
            useRuleMatching = useRuleMatching
        )
    }

    private fun parseJsonContent(
        text: String,
        customRules: List<CustomParsingRule>,
        useRuleMatching: Boolean
    ): ImportPreviewResult {
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
                o.has("amountCents") -> abs(o.optLong("amountCents", 0L))
                o.has("amount") -> (abs(o.optDouble("amount", 0.0)) * 100.0).roundToLong()
                else -> 0L
            }
            if (amountCents <= 0L) {
                skipped++
                continue
            }
            val currency = resolveCurrencyCode(
                o.optString("currencyCode").ifBlank { o.optString("currency", "CNY") }
            )
            val merchant = o.optString("merchant").ifBlank { "导入账单" }
            val rawType = o.optString("type", "EXPENSE")
            val type = mapType(rawType)
            val productName = cleanOptionalCell(o.optString("productName").ifBlank { o.optString("product") })
            val note = cleanOptionalCell(o.optString("note"))
            val category = o.optString("category")
                .ifBlank { ChinaNotificationParser.inferCategory(merchant, productName ?: note.orEmpty(), type) }
            val occurredAt = when {
                o.optLong("occurredAtMillis", 0L) > 0L -> o.optLong("occurredAtMillis")
                o.has("date") && o.has("time") -> parseDateAndTimeMillis(o.optString("date"), o.optString("time"))
                o.has("time") -> parseDateMillis(o.optString("time"))
                o.has("date") -> parseDateMillis(o.optString("date"))
                else -> System.currentTimeMillis()
            }
            val balanceAfterCents = when {
                o.has("balanceAfterCents") && !o.isNull("balanceAfterCents") -> o.optLong("balanceAfterCents")
                o.has("balance") && !o.isNull("balance") ->
                    cleanNumericAmount(o.optString("balance"))?.let { (it * 100.0).roundToLong() }
                else -> null
            }
            val rawTx = LedgerTransaction(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                amountCents = amountCents,
                currencyCode = currency,
                type = type.name,
                category = category,
                merchant = merchant,
                channel = o.optString("channel").ifBlank { "手动导入" },
                cardLastFour = cleanOptionalCell(o.optString("cardLastFour")),
                sourceApp = o.optString("sourceApp").ifBlank { SourceApp.IMPORT.name },
                parseMethod = o.optString("parseMethod").ifBlank { ParseMethod.IMPORT.name },
                rawNotification = cleanOptionalCell(o.optString("rawNotification")),
                note = note,
                tradeType = cleanOptionalCell(o.optString("tradeType")),
                productName = productName,
                tradeStatus = cleanOptionalCell(o.optString("tradeStatus")),
                transactionNo = cleanOptionalCell(o.optString("transactionNo")),
                merchantOrderNo = cleanOptionalCell(o.optString("merchantOrderNo")),
                balanceAfterCents = balanceAfterCents,
                occurredAtMillis = occurredAt,
                confirmed = true
            )
            val enriched = if (useRuleMatching && customRules.isNotEmpty()) {
                applyRulesToTransaction(rawTx, customRules)
            } else {
                rawTx
            }
            if (enriched == null) {
                skipped++
            } else {
                items.add(enriched)
            }
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
            it.contains("金额") || it.contains("交易时间") || it.contains("交易日期") ||
                it.contains("交易对方") || it.contains("对手信息") ||
                it.contains("amount", ignoreCase = true)
        }
    }

    private fun parseCsvContent(
        text: String,
        customRules: List<CustomParsingRule>,
        useRuleMatching: Boolean
    ): ImportPreviewResult {
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
            fromExcel = false,
            customRules = customRules,
            useRuleMatching = useRuleMatching
        )
    }

    private fun buildTableImportResult(
        rows: List<List<String>>,
        mapping: ColumnMapping,
        baseFormatLabel: String,
        fromExcel: Boolean,
        customRules: List<CustomParsingRule>,
        useRuleMatching: Boolean
    ): ImportPreviewResult {
        val items = mutableListOf<LedgerTransaction>()
        var skipped = 0

        // Bank exports (e.g. Agricultural Bank of China / 农业银行) encode direction by sign
        // (-70.43 for expense, +100.00 or 100.00 for income) when there is no separate 收/支 column.
        val signedAmountMode = mapping.typeCol < 0 &&
            mapping.amountCol >= 0 &&
            ((mapping.headerRowIndex + 1) until rows.size).any { rowIdx ->
                val raw = cleanNumericAmount(rows[rowIdx].getOrNull(mapping.amountCol))
                raw != null && raw < 0.0
            }

        for (i in (mapping.headerRowIndex + 1) until rows.size) {
            val cols = rows[i]
            if (cols.count { it.isNotBlank() } < 2) {
                skipped++
                continue
            }

            val resolvedAmountAndType = resolveAmountAndType(cols, mapping, signedAmountMode)
            if (resolvedAmountAndType == null) {
                skipped++
                continue
            }
            val (amountCents, inferredType) = resolvedAmountAndType
            val rawDirection = cols.getOrNull(mapping.typeCol).orEmpty()
            val tradeType = cleanOptionalCell(cols.getOrNull(mapping.tradeTypeCol))
            val rawMerchantHint = cleanOptionalCell(cols.getOrNull(mapping.merchantCol))
            val productHint = cleanOptionalCell(cols.getOrNull(mapping.productCol))
            val noteHint = cleanOptionalCell(cols.getOrNull(mapping.noteCol))
            val internalTransferContext = listOfNotNull(rawDirection, tradeType, rawMerchantHint, productHint, noteHint).joinToString(" ")
            val type = when {
                ChinaNotificationParser.isSelfTransferOrWithdraw(internalTransferContext) -> TransactionType.TRANSFER
                rawDirection.isNotBlank() -> mapType(rawDirection, inferredType)
                !tradeType.isNullOrBlank() -> mapType(tradeType, inferredType)
                else -> inferredType
            }

            val rawMerchant = cleanOptionalCell(cols.getOrNull(mapping.merchantCol))
            val productName = cleanOptionalCell(cols.getOrNull(mapping.productCol))
            val noteCell = cleanOptionalCell(cols.getOrNull(mapping.noteCol))
            val tradeStatus = cleanOptionalCell(cols.getOrNull(mapping.statusCol))
            val transactionNo = cleanOptionalCell(cols.getOrNull(mapping.transactionNoCol))
            val merchantOrderNo = cleanOptionalCell(cols.getOrNull(mapping.merchantOrderNoCol))
            val balanceAfterCents = if (mapping.balanceCol >= 0) {
                cleanNumericAmount(cols.getOrNull(mapping.balanceCol))?.let { (it * 100.0).roundToLong() }
            } else {
                null
            }

            val merchant = rawMerchant
                ?: productName
                ?: tradeType
                ?: "导入账单"

            val note = noteCell ?: if (mapping.noteCol < 0 && productName != null && productName != merchant) {
                productName
            } else {
                null
            }

            val rawChannelCell = cleanOptionalCell(cols.getOrNull(mapping.channelCol))
            val channel = if (rawChannelCell != null) {
                com.example.model.ChannelCatalog.normalizeRecognizedChannel(rawChannelCell)
            } else {
                "手动导入"
            }
            val amountRawForCurrency = cols.getOrNull(mapping.amountCol)
                ?: cols.getOrNull(mapping.expenseAmountCol)
                ?: cols.getOrNull(mapping.incomeAmountCol)
            val currency = resolveCurrencyCode(cols.getOrNull(mapping.currencyCol), amountRawForCurrency)
            val rawDate = cols.getOrNull(mapping.dateCol).orEmpty()
            val rawTime = cols.getOrNull(mapping.timeCol).orEmpty()
            val occurredAt = parseDateAndTimeMillis(
                rawDate = rawDate,
                rawTime = rawTime,
                allowExcelSerial = fromExcel
            )

            val explicitCategory = cleanOptionalCell(cols.getOrNull(mapping.categoryCol))
            val contextForInfer = listOfNotNull(productName, tradeType, note).joinToString(" ")
            val category = explicitCategory
                ?: ChinaNotificationParser.inferCategory(merchant, contextForInfer, type)

            val rawTx = LedgerTransaction(
                amountCents = amountCents,
                currencyCode = currency,
                type = type.name,
                category = category.take(40),
                merchant = merchant.take(120),
                channel = channel.take(60),
                sourceApp = SourceApp.IMPORT.name,
                parseMethod = ParseMethod.IMPORT.name,
                note = note?.take(160),
                tradeType = tradeType?.take(60),
                productName = productName?.take(120),
                tradeStatus = tradeStatus?.take(40),
                transactionNo = transactionNo?.take(80),
                merchantOrderNo = merchantOrderNo?.take(80),
                balanceAfterCents = balanceAfterCents,
                occurredAtMillis = occurredAt,
                confirmed = true
            )

            val finalTx = if (useRuleMatching && customRules.isNotEmpty()) {
                applyRulesToTransaction(rawTx, customRules)
            } else {
                rawTx
            }
            if (finalTx == null) {
                skipped++
            } else {
                items.add(finalTx)
            }
        }

        require(items.isNotEmpty()) { "$baseFormatLabel 中未找到有效账单记录（已定位第 ${mapping.headerRowIndex + 1} 行为表头）" }

        val mappedColumns = buildList {
            if (mapping.dateCol >= 0) add("日期:${mapping.headers.getOrNull(mapping.dateCol)}")
            if (mapping.timeCol >= 0) add("时间:${mapping.headers.getOrNull(mapping.timeCol)}")
            if (mapping.tradeTypeCol >= 0) add("摘要/类型:${mapping.headers.getOrNull(mapping.tradeTypeCol)}")
            if (mapping.merchantCol >= 0) add("对方:${mapping.headers.getOrNull(mapping.merchantCol)}")
            if (mapping.productCol >= 0) add("商品:${mapping.headers.getOrNull(mapping.productCol)}")
            if (mapping.typeCol >= 0) add("收/支:${mapping.headers.getOrNull(mapping.typeCol)}")
            if (mapping.amountCol >= 0) add("金额:${mapping.headers.getOrNull(mapping.amountCol)}")
            if (mapping.expenseAmountCol >= 0) add("支出:${mapping.headers.getOrNull(mapping.expenseAmountCol)}")
            if (mapping.incomeAmountCol >= 0) add("收入:${mapping.headers.getOrNull(mapping.incomeAmountCol)}")
            if (mapping.balanceCol >= 0) add("余额:${mapping.headers.getOrNull(mapping.balanceCol)}")
            if (mapping.channelCol >= 0) add("渠道:${mapping.headers.getOrNull(mapping.channelCol)}")
            if (mapping.statusCol >= 0) add("状态:${mapping.headers.getOrNull(mapping.statusCol)}")
            if (mapping.transactionNoCol >= 0) add("单号/日志号:${mapping.headers.getOrNull(mapping.transactionNoCol)}")
            if (mapping.merchantOrderNoCol >= 0) add("商户单号:${mapping.headers.getOrNull(mapping.merchantOrderNoCol)}")
            if (mapping.noteCol >= 0) add("附言/备注:${mapping.headers.getOrNull(mapping.noteCol)}")
        }.joinToString(" · ")

        return ImportPreviewResult(
            formatLabel = "$baseFormatLabel · 自动定位第 ${mapping.headerRowIndex + 1} 行为表头",
            transactions = items,
            skippedCount = skipped,
            detectedHeaderRowOneBased = mapping.headerRowIndex + 1,
            detectedColumnsSummary = mappedColumns
        )
    }

    private fun applyRulesToTransaction(
        tx: LedgerTransaction,
        customRules: List<CustomParsingRule>
    ): LedgerTransaction? {
        val searchable = listOfNotNull(
            tx.merchant,
            tx.productName,
            tx.tradeType,
            tx.channel,
            tx.tradeStatus,
            tx.note,
            tx.rawNotification
        ).joinToString(" ")
        val matched = customRules
            .asSequence()
            .filter { it.enabled && it.keywordPattern.isNotBlank() }
            .sortedByDescending { it.priority }
            .firstOrNull { rule ->
                rule.keywordPattern.split("|", ",", "，")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .any { kw -> searchable.contains(kw, ignoreCase = true) }
            } ?: return tx

        if (matched.isIgnoreRule) return null

        val nextType = when (matched.defaultType.uppercase()) {
            "EXPENSE", "INCOME", "TRANSFER" -> matched.defaultType.uppercase()
            else -> tx.type
        }
        val nextCategory = matched.defaultCategory.takeIf { it.isNotBlank() && it != "自动" } ?: tx.category
        val nextChannel = matched.channelName.takeIf { it.isNotBlank() && it != "自动" }
            ?.let { com.example.model.ChannelCatalog.normalizeRecognizedChannel(it) }
            ?: tx.channel
        val nextMerchant = matched.merchantOverride.takeIf { it.isNotBlank() } ?: tx.merchant
        return tx.copy(
            type = nextType,
            category = nextCategory.take(40),
            channel = nextChannel.take(60),
            merchant = nextMerchant.take(120),
            parseMethod = ParseMethod.RULE_ENGINE.name
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
                        val normalized = cell.lowercase().replace(Regex("""[\s\u3000]+"""), "")
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
                        val normalized = cell.lowercase().replace(Regex("""[\s\u3000]+"""), "")
                        if (excludeKeys.any { normalized.contains(it.lowercase()) }) return@forEachIndexed
                        if (normalized.contains(lowerKey)) {
                            used.add(idx)
                            return idx
                        }
                    }
                }
                return -1
            }

            val dateCol = findBestCol(
                exactKeys = listOf("交易日期", "记账日期", "入账日期", "发生日期", "日期", "date", "tradedate"),
                containsKeys = listOf("交易日期", "记账日期", "入账日期", "日期", "date"),
                excludeKeys = listOf("时间", "time")
            )
            val timeCol = findBestCol(
                exactKeys = listOf("交易时间", "记账时间", "创建时间", "入账时间", "时间", "time", "datetime", "timestamp"),
                containsKeys = listOf("交易时间", "记账时间", "时间", "time", "timestamp")
            )
            val balanceCol = findBestCol(
                exactKeys = listOf("本次余额", "账户余额", "交易后余额", "余额", "可用余额", "balance"),
                containsKeys = listOf("余额", "balance")
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
                exactKeys = listOf("金额(元)", "金额（元）", "金额", "交易金额", "实付金额", "发生额", "amount", "money", "total"),
                containsKeys = listOf("金额", "发生额", "实付", "amount", "price", "value"),
                excludeKeys = listOf("余额", "balance")
            )
            val typeCol = findBestCol(
                exactKeys = listOf("收/支", "收支", "收支类型", "借贷方向", "direction"),
                containsKeys = listOf("收/支", "收支", "借贷")
            )
            val tradeTypeCol = findBestCol(
                exactKeys = listOf("交易类型", "业务类型", "交易摘要", "业务摘要", "摘要", "类型", "tradetype", "type", "summary"),
                containsKeys = listOf("交易类型", "业务类型", "交易摘要", "摘要", "type"),
                excludeKeys = listOf("收支", "收/支")
            )
            val merchantCol = findBestCol(
                exactKeys = listOf(
                    "交易对方", "对手信息", "交易对手", "对手名称", "对手户名", "对方户名",
                    "商户", "商户名称", "对方名称", "交易对象", "收款方", "merchant", "payee", "counterparty"
                ),
                containsKeys = listOf("交易对方", "对手信息", "交易对手", "对手", "商户", "交易对象", "对方", "payee", "merchant"),
                excludeKeys = listOf("单号", "订单号", "order", "账号", "卡号")
            )
            val productCol = findBestCol(
                exactKeys = listOf("商品", "商品名称", "商品说明", "product", "item"),
                containsKeys = listOf("商品", "product", "item")
            )
            val channelCol = findBestCol(
                exactKeys = listOf("支付方式", "收/付款方式", "付款方式", "交易渠道", "渠道", "账户", "channel", "account", "method"),
                containsKeys = listOf("支付方式", "付款方式", "交易渠道", "渠道", "账户", "银行", "channel", "account")
            )
            val statusCol = findBestCol(
                exactKeys = listOf("当前状态", "交易状态", "状态", "status", "state"),
                containsKeys = listOf("状态", "status")
            )
            val transactionNoCol = findBestCol(
                exactKeys = listOf("交易单号", "交易订单号", "流水号", "日志号", "交易日志号", "核心流水号", "凭证号", "transactionno", "trade_no"),
                containsKeys = listOf("交易单号", "交易订单", "流水号", "日志号", "transaction")
            )
            val merchantOrderNoCol = findBestCol(
                exactKeys = listOf("商户单号", "商家订单号", "商户订单号", "merchantorderno", "orderno"),
                containsKeys = listOf("商户单号", "商家订单", "商户订单", "order")
            )
            val noteCol = findBestCol(
                exactKeys = listOf("交易附言", "附言", "备注", "说明", "用途", "note", "memo", "remark"),
                containsKeys = listOf("附言", "备注", "说明", "用途", "note", "memo", "remark")
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
                dateCol = dateCol,
                timeCol = timeCol,
                tradeTypeCol = tradeTypeCol,
                typeCol = typeCol,
                merchantCol = merchantCol,
                productCol = productCol,
                channelCol = channelCol,
                amountCol = amountCol,
                expenseAmountCol = expenseAmountCol,
                incomeAmountCol = incomeAmountCol,
                balanceCol = balanceCol,
                currencyCol = currencyCol,
                categoryCol = categoryCol,
                statusCol = statusCol,
                transactionNoCol = transactionNoCol,
                merchantOrderNoCol = merchantOrderNoCol,
                noteCol = noteCol
            )
            if (!candidate.hasUsableAmountColumn) continue

            var score = 0
            if (amountCol >= 0) score += 4
            if (expenseAmountCol >= 0) score += 3
            if (incomeAmountCol >= 0) score += 3
            if (dateCol >= 0) score += 3
            if (timeCol >= 0) score += 3
            if (merchantCol >= 0) score += 3
            if (typeCol >= 0) score += 2
            if (tradeTypeCol >= 0) score += 2
            if (channelCol >= 0) score += 2
            if (productCol >= 0) score += 2
            if (balanceCol >= 0) score += 1
            if (statusCol >= 0) score += 1
            if (transactionNoCol >= 0) score += 1
            if (merchantOrderNoCol >= 0) score += 1
            if (noteCol >= 0) score += 1
            if (categoryCol >= 0) score += 1
            if (currencyCol >= 0) score += 1

            val hasDataBelow = ((r + 1) until minOf(rows.size, r + 15)).any { nextIdx ->
                resolveAmountAndType(rows[nextIdx], candidate, signedAmountMode = false) != null
            }
            if (hasDataBelow) score += 5

            val minScore = if (allowWeakFallback) 4 else 7
            if (score >= minScore && (best == null || score > best.score)) {
                best = candidate.copy(score = score)
            }
        }
        return best
    }

    private fun cleanOptionalCell(raw: String?): String? {
        val cleaned = raw?.trim()?.removePrefix("\t")?.trim().orEmpty()
        return cleaned.takeIf { it.isNotEmpty() && it != "/" && it != "-" && !it.equals("null", ignoreCase = true) }
    }

    private fun resolveAmountAndType(
        cols: List<String>,
        mapping: ColumnMapping,
        signedAmountMode: Boolean = false
    ): Pair<Long, TransactionType>? {
        val rawAmountText = cols.getOrNull(mapping.amountCol)?.trim()
        val mainRaw = cleanNumericAmount(rawAmountText)
        if (mainRaw != null && abs(mainRaw) > 0.0) {
            val type = when {
                mainRaw < 0 -> TransactionType.EXPENSE
                rawAmountText?.startsWith("+") == true -> TransactionType.INCOME
                signedAmountMode && mainRaw > 0 -> TransactionType.INCOME
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
            .replace(Regex("""[¥￥$€£฿₱₹₽₺₪₩]"""), "")
            .replace(Regex("""\b[A-Za-z]{2,4}\$?"""), "")
            .replace(",", "")
            .replace("元", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun resolveCurrencyCode(raw: String?, amountRawFallback: String? = null): String {
        CurrencyCatalog.findByToken(raw)?.let { return it.code }
        if (!amountRawFallback.isNullOrBlank()) {
            val detected = ChinaNotificationParser.detectCurrencyCode(amountRawFallback)
            if (detected != "CNY") return detected
        }
        return "CNY"
    }

    private fun parseFreeformLines(
        text: String,
        customRules: List<CustomParsingRule>,
        useRuleMatching: Boolean
    ): ImportPreviewResult {
        val items = mutableListOf<LedgerTransaction>()
        var skipped = 0
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        for (line in lines) {
            val extractedLineTime = ChinaNotificationParser.extractOccurredAtMillis(line)
            if (useRuleMatching) {
                val parsed = ChinaNotificationParser.parse(
                    packageName = "manual.import",
                    title = "",
                    content = line,
                    customRules = customRules,
                    enableLocalSemanticPass = false
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
                            parseMethod = ParseMethod.RULE_ENGINE.name,
                            rawNotification = line.take(220),
                            occurredAtMillis = parsed.occurredAtMillis ?: extractedLineTime ?: System.currentTimeMillis(),
                            confirmed = true
                        )
                    )
                    continue
                }
            }

            val numMatch = Regex("""([0-9]+(?:\.[0-9]{1,2})?)""").find(line)
            val yuan = numMatch?.groupValues?.get(1)?.toDoubleOrNull()
            if (yuan != null && yuan > 0.0) {
                val currency = ChinaNotificationParser.detectCurrencyCode(line)
                val merchant = line.replace(numMatch.value, "")
                    .replace(Regex("""\b[A-Za-z]{3}\b|[¥￥$€£฿₱₹₽₺₪₩]|元"""), "")
                    .trim()
                    .ifBlank { "导入记录" }
                val type = when {
                    ChinaNotificationParser.isSelfTransferOrWithdraw(line) -> TransactionType.TRANSFER
                    line.contains("收入") || line.contains("工资") || line.contains("退款") -> TransactionType.INCOME
                    else -> TransactionType.EXPENSE
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
                        rawNotification = line.take(220),
                        occurredAtMillis = extractedLineTime ?: System.currentTimeMillis(),
                        confirmed = true
                    )
                )
            } else {
                skipped++
            }
        }

        require(items.isNotEmpty()) { "未从输入文本中识别出有效的账单金额" }
        val modeTag = if (useRuleMatching) "规则匹配" else "基础文本提取"
        return ImportPreviewResult(
            formatLabel = "文本批量导入 ($modeTag)",
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
        raw.contains("不计收支") || raw.contains("转账") || raw.contains("中性") ||
            raw.equals("TRANSFER", ignoreCase = true) -> TransactionType.TRANSFER
        raw.contains("支出") || raw.contains("付款") || raw.contains("消费") ||
            raw.equals("EXPENSE", ignoreCase = true) || raw.contains("借") -> TransactionType.EXPENSE
        else -> fallback
    }

    /**
     * Intelligently parses either a combined date+time pair (e.g. "20251121" + "135144",
     * "20251121" + "95144", "2025-11-21" + "13:51:44") or a single date/time/numeric timestamp.
     */
    fun parseDateAndTimeMillis(
        rawDate: String,
        rawTime: String,
        allowExcelSerial: Boolean = false
    ): Long {
        val datePart = rawDate.trim()
        val timePart = rawTime.trim()
        if (datePart.isEmpty() && timePart.isEmpty()) return System.currentTimeMillis()
        if (datePart.isEmpty()) return parseDateMillis(timePart, allowExcelSerial)
        if (timePart.isEmpty()) return parseDateMillis(datePart, allowExcelSerial)

        // If timePart already contains a full date+time or epoch timestamp, prefer it directly.
        if (timePart.length >= 10 && (timePart.contains("-") || timePart.contains("/") || timePart.all { it.isDigit() })) {
            tryParseSmartTimestamp(timePart, allowExcelSerial)?.let { return it }
        }

        val dateYmd = parseDateOnlyYmd(datePart, allowExcelSerial)
        val timeHms = parseTimeOnlyHms(timePart, allowExcelSerial)
        if (dateYmd != null && timeHms != null) {
            return Calendar.getInstance().apply {
                set(Calendar.YEAR, dateYmd.first)
                set(Calendar.MONTH, dateYmd.second - 1)
                set(Calendar.DAY_OF_MONTH, dateYmd.third)
                set(Calendar.HOUR_OF_DAY, timeHms.first)
                set(Calendar.MINUTE, timeHms.second)
                set(Calendar.SECOND, timeHms.third)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        return parseDateMillis("$datePart $timePart", allowExcelSerial)
    }

    private fun parseDateOnlyYmd(raw: String, allowExcelSerial: Boolean): Triple<Int, Int, Int>? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.length == 8 && cleaned.all { it.isDigit() }) {
            val y = cleaned.substring(0, 4).toIntOrNull() ?: return null
            val m = cleaned.substring(4, 6).toIntOrNull() ?: return null
            val d = cleaned.substring(6, 8).toIntOrNull() ?: return null
            if (y in 1990..2100 && m in 1..12 && d in 1..31) return Triple(y, m, d)
        }
        val sepMatch = Regex("""^(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?$""").matchEntire(cleaned)
        if (sepMatch != null) {
            val y = sepMatch.groupValues[1].toIntOrNull() ?: return null
            val m = sepMatch.groupValues[2].toIntOrNull() ?: return null
            val d = sepMatch.groupValues[3].toIntOrNull() ?: return null
            if (y in 1990..2100 && m in 1..12 && d in 1..31) return Triple(y, m, d)
        }
        if (allowExcelSerial) {
            cleaned.toDoubleOrNull()?.let { serial ->
                val formatted = XlsxTableReader.excelSerialToFormattedDate(serial)
                if (formatted != null) {
                    val dateHead = formatted.substringBefore(' ')
                    return parseDateOnlyYmd(dateHead, allowExcelSerial = false)
                }
            }
        }
        return null
    }

    private fun parseTimeOnlyHms(raw: String, allowExcelSerial: Boolean): Triple<Int, Int, Int>? {
        val cleaned = raw.trim().replace('：', ':')
        if (cleaned.isEmpty()) return null
        val colonMatch = Regex("""^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$""").matchEntire(cleaned)
        if (colonMatch != null) {
            val h = colonMatch.groupValues[1].toIntOrNull() ?: return null
            val m = colonMatch.groupValues[2].toIntOrNull() ?: return null
            val s = colonMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59 && s in 0..59) return Triple(h, m, s)
        }
        // Pure numeric time like "135144", "95144" (09:51:44), "0951" (09:51:00)
        if (cleaned.all { it.isDigit() }) {
            val padded = when (cleaned.length) {
                6 -> cleaned
                5 -> "0$cleaned"
                4 -> "${cleaned}00"
                3 -> "0${cleaned}00"
                else -> null
            }
            if (padded != null) {
                val h = padded.substring(0, 2).toIntOrNull() ?: return null
                val m = padded.substring(2, 4).toIntOrNull() ?: return null
                val s = padded.substring(4, 6).toIntOrNull() ?: return null
                if (h in 0..23 && m in 0..59 && s in 0..59) return Triple(h, m, s)
            }
        }
        if (allowExcelSerial) {
            cleaned.toDoubleOrNull()?.let { frac ->
                if (frac >= 0.0 && frac < 1.0) {
                    val totalSeconds = (frac * 86_400.0).roundToLong().toInt().coerceIn(0, 86_399)
                    return Triple(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
                }
            }
        }
        return null
    }

    private fun tryParseSmartTimestamp(raw: String, allowExcelSerial: Boolean): Long? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null

        // Pure numeric timestamps: epoch millis (13 digits), epoch seconds (10 digits),
        // compact yyyyMMddHHmmss (14 digits), yyyyMMddHHmm (12 digits), yyyyMMdd (8 digits)
        if (cleaned.all { it.isDigit() }) {
            when (cleaned.length) {
                13 -> {
                    val ms = cleaned.toLongOrNull()
                    if (ms != null && ms in 946_684_800_000L..4_102_444_800_000L) return ms
                }
                10 -> {
                    val sec = cleaned.toLongOrNull()
                    if (sec != null && sec in 946_684_800L..4_102_444_800L) return sec * 1000L
                }
                14 -> {
                    val y = cleaned.substring(0, 4).toIntOrNull()
                    val mo = cleaned.substring(4, 6).toIntOrNull()
                    val d = cleaned.substring(6, 8).toIntOrNull()
                    val h = cleaned.substring(8, 10).toIntOrNull()
                    val mi = cleaned.substring(10, 12).toIntOrNull()
                    val s = cleaned.substring(12, 14).toIntOrNull()
                    if (y != null && mo != null && d != null && h != null && mi != null && s != null &&
                        y in 1990..2100 && mo in 1..12 && d in 1..31 && h in 0..23 && mi in 0..59 && s in 0..59
                    ) {
                        return Calendar.getInstance().apply {
                            set(y, mo - 1, d, h, mi, s)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                }
                12 -> {
                    val y = cleaned.substring(0, 4).toIntOrNull()
                    val mo = cleaned.substring(4, 6).toIntOrNull()
                    val d = cleaned.substring(6, 8).toIntOrNull()
                    val h = cleaned.substring(8, 10).toIntOrNull()
                    val mi = cleaned.substring(10, 12).toIntOrNull()
                    if (y != null && mo != null && d != null && h != null && mi != null &&
                        y in 1990..2100 && mo in 1..12 && d in 1..31 && h in 0..23 && mi in 0..59
                    ) {
                        return Calendar.getInstance().apply {
                            set(y, mo - 1, d, h, mi, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                }
                8 -> {
                    parseDateOnlyYmd(cleaned, allowExcelSerial = false)?.let { (y, mo, d) ->
                        return Calendar.getInstance().apply {
                            set(y, mo - 1, d, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                }
            }
        }

        if (allowExcelSerial) {
            cleaned.toDoubleOrNull()?.let { serial ->
                val formatted = XlsxTableReader.excelSerialToFormattedDate(serial)
                if (formatted != null) {
                    parseWithPatterns(formatted)?.let { return it }
                }
            }
        }

        val normalized = cleaned.replace('T', ' ').substringBefore('.').trim()
        return parseWithPatterns(normalized)
    }

    private fun parseWithPatterns(normalized: String): Long? {
        for (pattern in dateTimePatterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }.parse(normalized)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    private fun parseDateMillis(raw: String, allowExcelSerial: Boolean = false): Long {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return System.currentTimeMillis()
        tryParseSmartTimestamp(cleaned, allowExcelSerial)?.let { return it }
        return ChinaNotificationParser.extractOccurredAtMillis(cleaned) ?: System.currentTimeMillis()
    }
}
