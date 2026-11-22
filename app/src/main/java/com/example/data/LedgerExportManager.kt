package com.example.data

import com.example.model.CurrencyCatalog
import com.example.model.LedgerTransaction
import com.example.parser.LocalSpendingAnalyzer
import com.example.parser.QuickAnalysisReport
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class LedgerExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    CSV(
        label = "CSV 表格",
        extension = "csv",
        mimeType = "text/csv",
        description = "通用逗号分隔表格（UTF-8 BOM，Excel / WPS 直接打开不乱码）"
    ),
    XLSX(
        label = "Excel (.xlsx)",
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        description = "原生多工作表 Excel：明细表 + 可选独立「收支分析」工作表"
    ),
    JSON(
        label = "JSON 数据",
        extension = "json",
        mimeType = "application/json",
        description = "结构化标准 JSON，包含完整账单字段与可选收支分析摘要"
    )
}

data class LedgerExportResult(
    val format: LedgerExportFormat,
    val exportedCount: Int,
    val includedAnalysis: Boolean
)

/**
 * Zero-dependency streaming exporter supporting CSV, native OOXML XLSX (via ZipOutputStream),
 * and JSON, with optional LocalSpendingAnalyzer report text & breakdown.
 */
object LedgerExportManager {

    private val CSV_HEADERS = listOf(
        "交易时间",
        "收/支",
        "分类",
        "交易对方",
        "商品",
        "金额",
        "币种",
        "支付方式",
        "交易类型",
        "本次余额",
        "当前状态",
        "交易单号",
        "商户单号",
        "备注"
    )

    fun suggestedFileName(
        scopeTitle: String,
        format: LedgerExportFormat,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val safeScope = scopeTitle
            .trim()
            .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
            .trim('_')
            .take(24)
            .ifBlank { "小本本账单" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(nowMillis))
        return "${safeScope}_${stamp}.${format.extension}"
    }

    fun exportToStream(
        outputStream: OutputStream,
        format: LedgerExportFormat,
        transactions: List<LedgerTransaction>,
        scopeTitle: String = "小本本账单",
        baseCurrencyCode: String = "CNY",
        ratesToCny: Map<String, Double> = CurrencyCatalog.defaultRatesToCny,
        includeAnalysisText: Boolean = true,
        bookName: String? = null
    ): LedgerExportResult {
        val sorted = transactions.sortedByDescending { it.occurredAtMillis }
        val effectiveScope = bookName?.takeIf { it.isNotBlank() }?.let { "账本「$it」" } ?: scopeTitle
        val report = if (includeAnalysisText) {
            LocalSpendingAnalyzer.generateQuickSelectionReport(
                transactions = sorted,
                baseCurrencyCode = baseCurrencyCode,
                ratesToCny = ratesToCny,
                scopeTitle = effectiveScope
            )
        } else {
            null
        }

        when (format) {
            LedgerExportFormat.CSV -> writeCsv(outputStream, sorted, effectiveScope, report)
            LedgerExportFormat.XLSX -> writeXlsx(outputStream, sorted, effectiveScope, report)
            LedgerExportFormat.JSON -> writeJson(outputStream, sorted, effectiveScope, baseCurrencyCode, report, bookName)
        }

        return LedgerExportResult(
            format = format,
            exportedCount = sorted.size,
            includedAnalysis = report != null
        )
    }

    private fun writeCsv(
        outputStream: OutputStream,
        transactions: List<LedgerTransaction>,
        scopeTitle: String,
        report: QuickAnalysisReport?
    ) {
        val buffered = BufferedOutputStream(outputStream, 16 * 1024)
        // UTF-8 BOM so desktop Excel / WPS recognizes Chinese characters cleanly
        buffered.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val writer = OutputStreamWriter(buffered, Charsets.UTF_8)
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        if (report != null) {
            writer.write("# 小本本账单导出 · ${scopeTitle.replace('\n', ' ')}\r\n")
            report.plainText.lines().forEach { line ->
                writer.write("# $line\r\n")
            }
            writer.write("\r\n")
        }

        writer.write(CSV_HEADERS.joinToString(",") { escapeCsv(it) })
        writer.write("\r\n")

        transactions.forEach { tx ->
            val row = buildRowStrings(tx, timeFmt)
            writer.write(row.joinToString(",") { escapeCsv(it) })
            writer.write("\r\n")
        }
        writer.flush()
    }

    private fun writeJson(
        outputStream: OutputStream,
        transactions: List<LedgerTransaction>,
        scopeTitle: String,
        baseCurrencyCode: String,
        report: QuickAnalysisReport?,
        bookName: String? = null
    ) {
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val root = JSONObject().apply {
            put("app", "小本本")
            put("scopeTitle", scopeTitle)
            bookName?.takeIf { it.isNotBlank() }?.let { put("bookName", it) }
            put("baseCurrencyCode", baseCurrencyCode)
            put("exportedAtMillis", System.currentTimeMillis())
            put("count", transactions.size)
            if (report != null) {
                put("analysis", JSONObject().apply {
                    put("scopeTitle", report.scopeTitle)
                    put("dateRange", report.dateRange)
                    put("totalCount", report.totalCount)
                    put("expenseCount", report.expenseCount)
                    put("incomeCount", report.incomeCount)
                    put("transferCount", report.transferCount)
                    put("totalExpenseCents", report.totalExpenseCents)
                    put("totalIncomeCents", report.totalIncomeCents)
                    put("totalTransferCents", report.totalTransferCents)
                    put("netCents", report.netCents)
                    put("avgExpenseCents", report.avgExpenseCents)
                    put("plainText", report.plainText)
                    put("topCategories", JSONArray().apply {
                        report.topCategories.forEach { item ->
                            put(JSONObject().apply {
                                put("label", item.label)
                                put("amountCents", item.amountCents)
                                put("count", item.count)
                                put("sharePercent", String.format(Locale.US, "%.2f", item.sharePercent).toDouble())
                            })
                        }
                    })
                    put("topChannels", JSONArray().apply {
                        report.topChannels.forEach { item ->
                            put(JSONObject().apply {
                                put("label", item.label)
                                put("amountCents", item.amountCents)
                                put("count", item.count)
                                put("sharePercent", String.format(Locale.US, "%.2f", item.sharePercent).toDouble())
                            })
                        }
                    })
                })
            }
            put("transactions", JSONArray().apply {
                transactions.forEach { tx ->
                    put(JSONObject().apply {
                        put("id", tx.id)
                        put("occurredAtMillis", tx.occurredAtMillis)
                        put("occurredAt", timeFmt.format(Date(tx.occurredAtMillis)))
                        put("type", tx.type)
                        put("typeLabel", tx.txType.label)
                        put("category", tx.category)
                        put("merchant", tx.merchant)
                        put("amountCents", tx.amountCents)
                        put("amount", String.format(Locale.US, "%.2f", tx.amountCents / 100.0).toDouble())
                        put("currencyCode", tx.currencyCode)
                        put("channel", tx.channel)
                        tx.cardLastFour?.takeIf { it.isNotBlank() }?.let { put("cardLastFour", it) }
                        tx.tradeType?.takeIf { it.isNotBlank() }?.let { put("tradeType", it) }
                        tx.productName?.takeIf { it.isNotBlank() }?.let { put("productName", it) }
                        tx.tradeStatus?.takeIf { it.isNotBlank() }?.let { put("tradeStatus", it) }
                        tx.transactionNo?.takeIf { it.isNotBlank() }?.let { put("transactionNo", it) }
                        tx.merchantOrderNo?.takeIf { it.isNotBlank() }?.let { put("merchantOrderNo", it) }
                        tx.balanceAfterCents?.let {
                            put("balanceAfterCents", it)
                            put("balanceAfter", String.format(Locale.US, "%.2f", it / 100.0).toDouble())
                        }
                        tx.note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
                    })
                }
            })
        }
        val writer = OutputStreamWriter(BufferedOutputStream(outputStream, 16 * 1024), Charsets.UTF_8)
        writer.write(root.toString(2))
        writer.flush()
    }

    private fun writeXlsx(
        outputStream: OutputStream,
        transactions: List<LedgerTransaction>,
        scopeTitle: String,
        report: QuickAnalysisReport?
    ) {
        val hasAnalysisSheet = report != null
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        ZipOutputStream(BufferedOutputStream(outputStream, 16 * 1024)).use { zos ->
            putZipText(zos, "[Content_Types].xml", buildContentTypesXml(hasAnalysisSheet))
            putZipText(zos, "_rels/.rels", ROOT_RELS_XML)
            putZipText(zos, "xl/workbook.xml", buildWorkbookXml(hasAnalysisSheet))
            putZipText(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(hasAnalysisSheet))
            putZipText(zos, "xl/styles.xml", MINIMAL_STYLES_XML)

            // Sheet 1: 账单明细 (streamed directly to ZipOutputStream to keep RAM low)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val s1Writer = OutputStreamWriter(zos, Charsets.UTF_8)
            s1Writer.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                    """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>"""
            )
            writeXlsxRow(s1Writer, 1, CSV_HEADERS.map { XlsxCell.Text(it) }, styleIdx = 1)
            transactions.forEachIndexed { idx, tx ->
                val rowNum = idx + 2
                val cells = listOf(
                    XlsxCell.Text(timeFmt.format(Date(tx.occurredAtMillis))),
                    XlsxCell.Text(tx.txType.label),
                    XlsxCell.Text(tx.category),
                    XlsxCell.Text(tx.merchant),
                    XlsxCell.Text(tx.productName.orEmpty()),
                    XlsxCell.Number(String.format(Locale.US, "%.2f", tx.amountCents / 100.0)),
                    XlsxCell.Text(tx.currencyCode),
                    XlsxCell.Text(tx.channel),
                    XlsxCell.Text(tx.tradeType.orEmpty()),
                    tx.balanceAfterCents?.let {
                        XlsxCell.Number(String.format(Locale.US, "%.2f", it / 100.0))
                    } ?: XlsxCell.Text(""),
                    XlsxCell.Text(tx.tradeStatus.orEmpty()),
                    XlsxCell.Text(tx.transactionNo.orEmpty()),
                    XlsxCell.Text(tx.merchantOrderNo.orEmpty()),
                    XlsxCell.Text(tx.note.orEmpty())
                )
                writeXlsxRow(s1Writer, rowNum, cells)
            }
            s1Writer.write("</sheetData></worksheet>")
            s1Writer.flush()
            zos.closeEntry()

            // Optional Sheet 2: 收支分析
            if (report != null) {
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
                val s2Writer = OutputStreamWriter(zos, Charsets.UTF_8)
                s2Writer.write(
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                        """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>"""
                )
                var row = 1
                writeXlsxRow(s2Writer, row++, listOf(XlsxCell.Text("小本本收支分析报告"), XlsxCell.Text(scopeTitle)), styleIdx = 1)
                writeXlsxRow(s2Writer, row++, listOf(XlsxCell.Text("统计时间范围"), XlsxCell.Text(report.dateRange)))
                writeXlsxRow(s2Writer, row++, listOf(XlsxCell.Text("账单笔数"), XlsxCell.Number(report.totalCount.toString())))
                writeXlsxRow(
                    s2Writer,
                    row++,
                    listOf(
                        XlsxCell.Text("累计支出 (${report.baseCurrencyCode})"),
                        XlsxCell.Number(String.format(Locale.US, "%.2f", report.totalExpenseCents / 100.0))
                    )
                )
                writeXlsxRow(
                    s2Writer,
                    row++,
                    listOf(
                        XlsxCell.Text("累计收入 (${report.baseCurrencyCode})"),
                        XlsxCell.Number(String.format(Locale.US, "%.2f", report.totalIncomeCents / 100.0))
                    )
                )
                writeXlsxRow(
                    s2Writer,
                    row++,
                    listOf(
                        XlsxCell.Text("净结余 (${report.baseCurrencyCode})"),
                        XlsxCell.Number(String.format(Locale.US, "%.2f", report.netCents / 100.0))
                    )
                )
                row++
                if (report.topCategories.isNotEmpty()) {
                    writeXlsxRow(
                        s2Writer,
                        row++,
                        listOf(
                            XlsxCell.Text("分类支出分布"),
                            XlsxCell.Text("金额 (${report.baseCurrencyCode})"),
                            XlsxCell.Text("笔数"),
                            XlsxCell.Text("占比")
                        ),
                        styleIdx = 1
                    )
                    report.topCategories.forEach { cat ->
                        writeXlsxRow(
                            s2Writer,
                            row++,
                            listOf(
                                XlsxCell.Text(cat.label),
                                XlsxCell.Number(String.format(Locale.US, "%.2f", cat.amountCents / 100.0)),
                                XlsxCell.Number(cat.count.toString()),
                                XlsxCell.Text(String.format(Locale.US, "%.1f%%", cat.sharePercent))
                            )
                        )
                    }
                    row++
                }
                if (report.topChannels.isNotEmpty()) {
                    writeXlsxRow(
                        s2Writer,
                        row++,
                        listOf(
                            XlsxCell.Text("渠道分布"),
                            XlsxCell.Text("金额 (${report.baseCurrencyCode})"),
                            XlsxCell.Text("笔数"),
                            XlsxCell.Text("占比")
                        ),
                        styleIdx = 1
                    )
                    report.topChannels.forEach { ch ->
                        writeXlsxRow(
                            s2Writer,
                            row++,
                            listOf(
                                XlsxCell.Text(ch.label),
                                XlsxCell.Number(String.format(Locale.US, "%.2f", ch.amountCents / 100.0)),
                                XlsxCell.Number(ch.count.toString()),
                                XlsxCell.Text(String.format(Locale.US, "%.1f%%", ch.sharePercent))
                            )
                        )
                    }
                    row++
                }
                writeXlsxRow(s2Writer, row++, listOf(XlsxCell.Text("纯文本分析摘要")), styleIdx = 1)
                report.plainText.lines().forEach { line ->
                    writeXlsxRow(s2Writer, row++, listOf(XlsxCell.Text(line)))
                }
                s2Writer.write("</sheetData></worksheet>")
                s2Writer.flush()
                zos.closeEntry()
            }
        }
    }

    private sealed interface XlsxCell {
        data class Text(val value: String) : XlsxCell
        data class Number(val numericLiteral: String) : XlsxCell
    }

    private fun writeXlsxRow(
        writer: OutputStreamWriter,
        rowNumber: Int,
        cells: List<XlsxCell>,
        styleIdx: Int? = null
    ) {
        writer.write("<row r=\"")
        writer.write(rowNumber.toString())
        writer.write("\">")
        cells.forEachIndexed { colIdx, cell ->
            val ref = "${columnLetter(colIdx)}$rowNumber"
            val styleAttr = if (styleIdx != null) " s=\"$styleIdx\"" else ""
            when (cell) {
                is XlsxCell.Number -> {
                    writer.write("<c r=\"$ref\"$styleAttr><v>${cell.numericLiteral}</v></c>")
                }
                is XlsxCell.Text -> {
                    val escaped = escapeXml(cell.value)
                    writer.write("<c r=\"$ref\" t=\"inlineStr\"$styleAttr><is><t xml:space=\"preserve\">$escaped</t></is></c>")
                }
            }
        }
        writer.write("</row>")
    }

    private fun columnLetter(zeroBasedIndex: Int): String {
        var idx = zeroBasedIndex
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.append(('A'.code + (idx % 26)).toChar())
            idx = (idx / 26) - 1
        }
        return sb.reverse().toString()
    }

    private fun buildRowStrings(tx: LedgerTransaction, timeFmt: SimpleDateFormat): List<String> = listOf(
        timeFmt.format(Date(tx.occurredAtMillis)),
        tx.txType.label,
        tx.category,
        tx.merchant,
        tx.productName.orEmpty(),
        String.format(Locale.US, "%.2f", tx.amountCents / 100.0),
        tx.currencyCode,
        tx.channel,
        tx.tradeType.orEmpty(),
        tx.balanceAfterCents?.let { String.format(Locale.US, "%.2f", it / 100.0) }.orEmpty(),
        tx.tradeStatus.orEmpty(),
        tx.transactionNo.orEmpty(),
        tx.merchantOrderNo.orEmpty(),
        tx.note.orEmpty()
    )

    private fun escapeCsv(raw: String): String {
        val needsQuotes = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (!needsQuotes) raw else "\"${raw.replace("\"", "\"\"")}\""
    }

    private fun escapeXml(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length + 8)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    // Strip XML 1.0 illegal control characters
                    if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun putZipText(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildContentTypesXml(hasAnalysisSheet: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        if (hasAnalysisSheet) {
            append("""<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun buildWorkbookXml(hasAnalysisSheet: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        append("""<sheet name="账单明细" sheetId="1" r:id="rId1"/>""")
        if (hasAnalysisSheet) {
            append("""<sheet name="收支分析" sheetId="2" r:id="rId2"/>""")
        }
        append("""</sheets></workbook>""")
    }

    private fun buildWorkbookRelsXml(hasAnalysisSheet: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""")
        if (hasAnalysisSheet) {
            append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>""")
            append("""<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        } else {
            append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        }
        append("""</Relationships>""")
    }

    private const val ROOT_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private const val MINIMAL_STYLES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>""" +
            """</styleSheet>"""
}
