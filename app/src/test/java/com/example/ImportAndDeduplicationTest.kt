package com.example

import com.example.data.ManualImportManager
import com.example.model.LedgerTransaction
import com.example.model.TransactionType
import com.example.parser.DeduplicationChannelFilter
import com.example.parser.DeduplicationTimeWindow
import com.example.parser.SmartDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportAndDeduplicationTest {

    @Test
    fun parsesXlsxWithPreambleRowsSharedStringsAndExcelSerialDates() {
        val xlsxBytes = buildMinimalXlsxBytes()
        val preview = ManualImportManager.parseImportedBytes(
            bytes = xlsxBytes,
            fileNameHint = "微信支付账单.xlsx"
        ).getOrThrow()

        assertEquals(5, preview.detectedHeaderRowOneBased)
        assertEquals(2, preview.transactions.size)

        val expense = preview.transactions[0]
        assertEquals(4250L, expense.amountCents)
        assertEquals(TransactionType.EXPENSE, expense.txType)
        assertEquals("瑞幸咖啡", expense.merchant)
        assertEquals("微信支付", expense.channel)
        assertTrue(expense.occurredAtMillis > 1_700_000_000_000L)

        val income = preview.transactions[1]
        assertEquals(185000L, income.amountCents)
        assertEquals(TransactionType.INCOME, income.txType)
        assertEquals("工资代发", income.merchant)
        assertEquals("招商银行", income.channel)
    }

    @Test
    fun detectsHeaderAfterPreambleInGb18030CsvAndSupportsSplitAmountColumns() {
        val bankCsv = """
            XX银行账户交易流水明细导出
            账号：6222 **** **** 8812
            币种：人民币
            说明：以下数据仅供参考，请以银行对账单为准
            
            记账日期,摘要,交易对方,支出金额,收入金额,账户余额,交易渠道
            2026-11-18 12:30:00,午餐消费,麦当劳餐厅,38.50,,12000.00,银联云闪付
            2026-11-18 18:00:00,月度奖金,科技公司,,5000.00,17000.00,网银转账
        """.trimIndent()

        val gbBytes = bankCsv.toByteArray(Charset.forName("GB18030"))
        val preview = ManualImportManager.parseImportedBytes(
            bytes = gbBytes,
            fileNameHint = "bank_statement.csv"
        ).getOrThrow()

        assertEquals(5, preview.detectedHeaderRowOneBased)
        assertEquals(2, preview.transactions.size)

        val tx1 = preview.transactions[0]
        assertEquals(3850L, tx1.amountCents)
        assertEquals(TransactionType.EXPENSE, tx1.txType)
        assertEquals("麦当劳餐厅", tx1.merchant)
        assertEquals("银联云闪付", tx1.channel)

        val tx2 = preview.transactions[1]
        assertEquals(500000L, tx2.amountCents)
        assertEquals(TransactionType.INCOME, tx2.txType)
        assertEquals("科技公司", tx2.merchant)
    }

    @Test
    fun smartDeduplicatorGroupsSimilarMerchantsAndRespectsFilters() {
        val baseTime = 1_731_920_000_000L
        val txAlipay = LedgerTransaction(
            id = "tx-alipay",
            amountCents = 4200L,
            currencyCode = "CNY",
            type = TransactionType.EXPENSE.name,
            category = "餐饮美食",
            merchant = "星巴克(静安寺店)",
            channel = "支付宝",
            note = "早上拿铁",
            occurredAtMillis = baseTime,
            confirmed = true
        )
        val txCmb = LedgerTransaction(
            id = "tx-cmb",
            amountCents = 4200L,
            currencyCode = "CNY",
            type = TransactionType.EXPENSE.name,
            category = "餐饮美食",
            merchant = "星巴克咖啡",
            channel = "招商银行",
            occurredAtMillis = baseTime + 180_000L,
            confirmed = true
        )
        val txSameChannelOld = LedgerTransaction(
            id = "tx-old",
            amountCents = 4200L,
            currencyCode = "CNY",
            type = TransactionType.EXPENSE.name,
            category = "餐饮美食",
            merchant = "星巴克门店",
            channel = "支付宝",
            occurredAtMillis = baseTime - 3L * 24 * 3_600_000L,
            confirmed = false
        )
        val txDifferentMerchant = LedgerTransaction(
            id = "tx-other",
            amountCents = 4200L,
            currencyCode = "CNY",
            type = TransactionType.EXPENSE.name,
            category = "交通出行",
            merchant = "滴滴出行",
            channel = "微信支付",
            occurredAtMillis = baseTime + 60_000L,
            confirmed = true
        )

        val shortThirtyMinutes = SmartDeduplicator.findDuplicateGroups(
            transactions = listOf(txAlipay, txCmb, txSameChannelOld, txDifferentMerchant),
            timeWindow = DeduplicationTimeWindow.THIRTY_MINUTES,
            channelFilter = DeduplicationChannelFilter.CROSS_CHANNEL
        )

        assertEquals(1, shortThirtyMinutes.size)
        val group = shortThirtyMinutes.first()
        assertEquals(2, group.items.size)
        assertTrue(group.isCrossChannel)
        assertEquals("tx-alipay", group.suggestedKeepId)
        assertTrue(group.items.none { it.id == "tx-other" })
        assertTrue(group.items.none { it.id == "tx-old" })

        // Even with maximum 7-day window, 3-day-old long-term repeated item in ALL filter should NOT merge with 2-minute window if custom window is 15 minutes
        val customFifteenMin = SmartDeduplicator.findDuplicateGroups(
            transactions = listOf(txAlipay, txCmb, txSameChannelOld, txDifferentMerchant),
            maxWindowMillis = 15L * 60_000L,
            channelFilter = DeduplicationChannelFilter.ALL
        )
        assertEquals(1, customFifteenMin.size)
        assertEquals(2, customFifteenMin.first().items.size)
        assertTrue(customFifteenMin.first().items.none { it.id == "tx-old" })

        // Tiny 1-minute window excludes the 3-minute-apart transaction as non-duplicate
        val oneMinuteWindow = SmartDeduplicator.findDuplicateGroups(
            transactions = listOf(txAlipay, txCmb, txSameChannelOld),
            maxWindowMillis = 60_000L,
            channelFilter = DeduplicationChannelFilter.ALL
        )
        assertEquals(0, oneMinuteWindow.size)
    }

    private fun buildMinimalXlsxBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            addEntry(
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
                </Types>
                """.trimIndent()
            )
            addEntry(
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="账单明细" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """.trimIndent()
            )
            addEntry(
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """.trimIndent()
            )

            val sharedStrings = listOf(
                "微信支付账单明细导出", // 0
                "微信昵称：小本本测试", // 1
                "起始时间：2026-11-01", // 2
                "----------------------", // 3
                "交易时间", // 4
                "交易类型", // 5
                "交易对方", // 6
                "商品", // 7
                "收/支", // 8
                "金额(元)", // 9
                "支付方式", // 10
                "2026-11-18 09:10:00", // 11
                "商户消费", // 12
                "瑞幸咖啡", // 13
                "生椰拿铁", // 14
                "支出", // 15
                "¥42.50", // 16
                "微信支付", // 17
                "工资代发", // 18
                "11月薪资", // 19
                "收入", // 20
                "招商银行" // 21
            )
            val sstXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>""")
                append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
                sharedStrings.forEach { append("<si><t>$it</t></si>") }
                append("</sst>")
            }
            addEntry("xl/sharedStrings.xml", sstXml)

            val sheetXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c></row>
                    <row r="2"><c r="A2" t="s"><v>1</v></c></row>
                    <row r="3"><c r="A3" t="s"><v>2</v></c></row>
                    <row r="5"><c r="A5" t="s"><v>3</v></c></row>
                    <row r="6">
                      <c r="A6" t="s"><v>4</v></c>
                      <c r="B6" t="s"><v>5</v></c>
                      <c r="C6" t="s"><v>6</v></c>
                      <c r="D6" t="s"><v>7</v></c>
                      <c r="E6" t="s"><v>8</v></c>
                      <c r="F6" t="s"><v>9</v></c>
                      <c r="G6" t="s"><v>10</v></c>
                    </row>
                    <row r="7">
                      <c r="A7" t="s"><v>11</v></c>
                      <c r="B7" t="s"><v>12</v></c>
                      <c r="C7" t="s"><v>13</v></c>
                      <c r="D7" t="s"><v>14</v></c>
                      <c r="E7" t="s"><v>15</v></c>
                      <c r="F7" t="s"><v>16</v></c>
                      <c r="G7" t="s"><v>17</v></c>
                    </row>
                    <row r="8">
                      <c r="A8"><v>45614.5</v></c>
                      <c r="C8" t="s"><v>18</v></c>
                      <c r="D8" t="s"><v>19</v></c>
                      <c r="E8" t="s"><v>20</v></c>
                      <c r="F8"><v>1850.00</v></c>
                      <c r="G8" t="s"><v>21</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
            addEntry("xl/worksheets/sheet1.xml", sheetXml)
        }
        return baos.toByteArray()
    }
}
