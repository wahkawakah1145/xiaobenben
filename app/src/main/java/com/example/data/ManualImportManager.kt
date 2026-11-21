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
        SimpleDateFormat("yyyy-M-d H:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy-M-d H:mm", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy/M/d H:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy/M/d H:mm", Locale.CHINA),
        SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy年M月d日 H:mm:ss", Locale.CHINA),
        SimpleDateFormat("yyyy年M月d日 H:mm", Locale.CHINA),
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
        val tradeTypeCol: Int,
        val typeCol: Int,
        val merchantCol: Int,
        val productCol: Int,
        val channelCol: Int,
        val amountCol: Int,
        val expenseAmountCol: Int,
        val incomeAmountCol: Int,
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
                o.has("amountCents") -> o.optLong("amountCents", 0L)
                o.has("amount") -> (o.optDouble("amount", 0.0) * 100.0).roundToLong()
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
                o.has("time") -> parseDateMillis(o.optString("time"))
                o.has("date") -> parseDateMillis(o.optString("date"))
                else -> System.currentTimeMillis()
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
            it.contains("金额") || it.contains("交易时间") || it.contains("交易对方") ||
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

            val merchant = rawMerchant
                ?: productName
                ?: tradeType
                ?: "导入账单"

            val note = noteCell ?: if (mapping.noteCol < 0 && productName != null && productName != merchant) {
                productName
            } else {
                null
            }

            val channel = cleanOptionalCell(cols.getOrNull(mapping.channelCol)) ?: "手动导入"
            val amountRawForCurrency = cols.getOrNull(mapping.amountCol)
                ?: cols.getOrNull(mapping.expenseAmountCol)
                ?: cols.getOrNull(mapping.incomeAmountCol)
            val currency = resolveCurrencyCode(cols.getOrNull(mapping.currencyCol), amountRawForCurrency)
            val rawTime = cols.getOrNull(mapping.timeCol).orEmpty()
            val occurredAt = parseDateMillis(rawTime, allowExcelSerial = fromExcel)

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
            if (mapping.timeCol >= 0) add("时间:${mapping.headers.getOrNull(mapping.timeCol)}")
            if (mapping.tradeTypeCol >= 0) add("交易类型:${mapping.headers.getOrNull(mapping.tradeTypeCol)}")
            if (mapping.merchantCol >= 0) add("对方:${mapping.headers.getOrNull(mapping.merchantCol)}")
            if (mapping.productCol >= 0) add("商品:${mapping.headers.getOrNull(mapping.productCol)}")
            if (mapping.typeCol >= 0) add("收/支:${mapping.headers.getOrNull(mapping.typeCol)}")
            if (mapping.amountCol >= 0) add("金额:${mapping.headers.getOrNull(mapping.amountCol)}")
            if (mapping.expenseAmountCol >= 0) add("支出:${mapping.headers.getOrNull(mapping.expenseAmountCol)}")
            if (mapping.incomeAmountCol >= 0) add("收入:${mapping.headers.getOrNull(mapping.incomeAmountCol)}")
            if (mapping.channelCol >= 0) add("支付方式:${mapping.headers.getOrNull(mapping.channelCol)}")
            if (mapping.statusCol >= 0) add("状态:${mapping.headers.getOrNull(mapping.statusCol)}")
            if (mapping.transactionNoCol >= 0) add("交易单号:${mapping.headers.getOrNull(mapping.transactionNoCol)}")
            if (mapping.merchantOrderNoCol >= 0) add("商户单号:${mapping.headers.getOrNull(mapping.merchantOrderNoCol)}")
            if (mapping.noteCol >= 0) add("备注:${mapping.headers.getOrNull(mapping.noteCol)}")
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
        val nextChannel = matched.channelName.takeIf { it.isNotBlank() && it != "自动" } ?: tx.channel
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
                exactKeys = listOf("金额(元)", "金额（元）", "金额", "交易金额", "实付金额", "amount", "money", "total"),
                containsKeys = listOf("金额", "发生额", "实付", "amount", "price", "value"),
                excludeKeys = listOf("余额", "balance")
            )
            val typeCol = findBestCol(
                exactKeys = listOf("收/支", "收支", "收支类型", "借贷方向", "direction"),
                containsKeys = listOf("收/支", "收支", "借贷")
            )
            val tradeTypeCol = findBestCol(
                exactKeys = listOf("交易类型", "业务类型", "类型", "tradetype", "type"),
                containsKeys = listOf("交易类型", "业务类型", "type"),
                excludeKeys = listOf("收支", "收/支")
            )
            val merchantCol = findBestCol(
                exactKeys = listOf("交易对方", "商户", "商户名称", "对方名称", "交易对象", "收款方", "merchant", "payee", "counterparty"),
                containsKeys = listOf("交易对方", "商户", "交易对象", "对方", "payee", "merchant"),
                excludeKeys = listOf("单号", "订单号", "order")
            )
            val productCol = findBestCol(
                exactKeys = listOf("商品", "商品名称", "商品说明", "product", "item"),
                containsKeys = listOf("商品", "product", "item")
            )
            val channelCol = findBestCol(
                exactKeys = listOf("支付方式", "收/付款方式", "付款方式", "渠道", "账户", "交易渠道", "channel", "account", "method"),
                containsKeys = listOf("支付方式", "付款方式", "渠道", "账户", "银行", "channel", "account")
            )
            val statusCol = findBestCol(
                exactKeys = listOf("当前状态", "交易状态", "状态", "status", "state"),
                containsKeys = listOf("状态", "status")
            )
            val transactionNoCol = findBestCol(
                exactKeys = listOf("交易单号", "交易订单号", "流水号", "transactionno", "trade_no"),
                containsKeys = listOf("交易单号", "交易订单", "流水号", "transaction")
            )
            val merchantOrderNoCol = findBestCol(
                exactKeys = listOf("商户单号", "商家订单号", "商户订单号", "merchantorderno", "orderno"),
                containsKeys = listOf("商户单号", "商家订单", "商户订单", "order")
            )
            val noteCol = findBestCol(
                exactKeys = listOf("备注", "摘要", "说明", "附言", "note", "memo", "remark"),
                containsKeys = listOf("备注", "摘要", "说明", "附言", "note", "memo", "remark")
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
                tradeTypeCol = tradeTypeCol,
                typeCol = typeCol,
                merchantCol = merchantCol,
                productCol = productCol,
                channelCol = channelCol,
                amountCol = amountCol,
                expenseAmountCol = expenseAmountCol,
                incomeAmountCol = incomeAmountCol,
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
            if (timeCol >= 0) score += 3
            if (merchantCol >= 0) score += 3
            if (typeCol >= 0) score += 2
            if (tradeTypeCol >= 0) score += 2
            if (channelCol >= 0) score += 2
            if (productCol >= 0) score += 2
            if (statusCol >= 0) score += 1
            if (transactionNoCol >= 0) score += 1
            if (merchantOrderNoCol >= 0) score += 1
            if (noteCol >= 0) score += 1
            if (categoryCol >= 0) score += 1
            if (currencyCol >= 0) score += 1

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

    private fun cleanOptionalCell(raw: String?): String? {
        val cleaned = raw?.trim()?.removePrefix("\t")?.trim().orEmpty()
        return cleaned.takeIf { it.isNotEmpty() && it != "/" && it != "-" && !it.equals("null", ignoreCase = true) }
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
        return ChinaNotificationParser.extractOccurredAtMillis(normalized) ?: System.currentTimeMillis()
    }
}
