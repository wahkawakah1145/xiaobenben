package com.example.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class XlsxSheetTable(
    val sheetName: String,
    val rows: List<List<String>>
)

object XlsxTableReader {

    fun isXlsxBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
    }

    fun readSheets(bytes: ByteArray): List<XlsxSheetTable> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val sheetPaths = resolveSheetPaths(entries)

        return sheetPaths.mapNotNull { (name, path) ->
            val xmlBytes = entries[path] ?: return@mapNotNull null
            val rows = parseWorksheetRows(xmlBytes, sharedStrings)
            if (rows.isEmpty()) null else XlsxSheetTable(sheetName = name, rows = rows)
        }
    }

    fun excelSerialToFormattedDate(serial: Double): String? {
        if (serial < 25_000.0 || serial > 90_000.0) return null
        val wholeDays = kotlin.math.floor(serial).toInt()
        val fractionalDay = serial - wholeDays
        val cal = Calendar.getInstance().apply {
            clear()
            // Excel epoch: 1899-12-30 accounts for the 1900 leap year bug
            set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            add(Calendar.DAY_OF_YEAR, wholeDays)
            val totalSeconds = kotlin.math.round(fractionalDay * 86_400.0).toInt()
            add(Calendar.SECOND, totalSeconds)
        }
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(cal.time)
    }

    private fun newDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }.newDocumentBuilder()

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val doc = newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val siNodes = doc.getElementsByTagName("si")
        val result = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i)
            val sb = StringBuilder()
            collectTextNodes(si, sb)
            result.add(sb.toString())
        }
        return result
    }

    private fun collectTextNodes(node: Node, out: StringBuilder) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val name = node.nodeName.substringAfter(':')
            if (name == "t") {
                out.append(node.textContent.orEmpty())
                return
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectTextNodes(children.item(i), out)
        }
    }

    private fun resolveSheetPaths(entries: Map<String, ByteArray>): List<Pair<String, String>> {
        val relMap = mutableMapOf<String, String>()
        entries["xl/_rels/workbook.xml.rels"]?.let { relBytes ->
            val relDoc = newDocumentBuilder().parse(ByteArrayInputStream(relBytes))
            val rels = relDoc.getElementsByTagName("Relationship")
            for (i in 0 until rels.length) {
                val el = rels.item(i) as? Element ?: continue
                val id = el.getAttribute("Id")
                val target = el.getAttribute("Target").trimStart('/')
                if (id.isNotBlank() && target.isNotBlank()) {
                    val normalized = if (target.startsWith("xl/")) target else "xl/$target"
                    relMap[id] = normalized
                }
            }
        }

        val ordered = mutableListOf<Pair<String, String>>()
        entries["xl/workbook.xml"]?.let { wbBytes ->
            val wbDoc = newDocumentBuilder().parse(ByteArrayInputStream(wbBytes))
            val sheets = wbDoc.getElementsByTagName("sheet")
            for (i in 0 until sheets.length) {
                val el = sheets.item(i) as? Element ?: continue
                val name = el.getAttribute("name").ifBlank { "Sheet${i + 1}" }
                val rId = el.getAttribute("r:id").ifBlank { el.getAttribute("id") }
                val path = relMap[rId]
                if (path != null && entries.containsKey(path)) {
                    ordered.add(name to path)
                }
            }
        }

        if (ordered.isNotEmpty()) return ordered

        return entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sorted()
            .mapIndexed { idx, path -> "Sheet${idx + 1}" to path }
    }

    private fun parseWorksheetRows(
        xmlBytes: ByteArray,
        sharedStrings: List<String>
    ): List<List<String>> {
        val doc = newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
        val rowNodes = doc.getElementsByTagName("row")
        val parsedRows = mutableListOf<List<String>>()

        for (r in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(r) as? Element ?: continue
            val cellNodes = rowEl.childNodes
            val rowCells = mutableMapOf<Int, String>()
            var maxCol = -1
            var sequentialCol = 0

            for (c in 0 until cellNodes.length) {
                val cellEl = cellNodes.item(c) as? Element ?: continue
                if (cellEl.nodeName.substringAfter(':') != "c") continue

                val ref = cellEl.getAttribute("r")
                val colIndex = if (ref.isNotBlank()) columnIndexFromRef(ref) else sequentialCol
                sequentialCol = colIndex + 1

                val value = extractCellText(cellEl, sharedStrings).trim()
                if (colIndex > maxCol) maxCol = colIndex
                if (value.isNotEmpty()) {
                    rowCells[colIndex] = value
                }
            }

            if (rowCells.isNotEmpty() && maxCol >= 0) {
                val denseRow = List(maxCol + 1) { idx -> rowCells[idx].orEmpty() }
                parsedRows.add(denseRow)
            }
        }
        return parsedRows
    }

    private fun extractCellText(cellEl: Element, sharedStrings: List<String>): String {
        val type = cellEl.getAttribute("t")
        if (type == "inlineStr") {
            val isNode = findFirstChild(cellEl, "is")
            if (isNode != null) {
                val sb = StringBuilder()
                collectTextNodes(isNode, sb)
                return sb.toString()
            }
        }
        val vText = findFirstChild(cellEl, "v")?.textContent?.trim().orEmpty()
        if (vText.isEmpty()) return ""
        return when (type) {
            "s" -> {
                val idx = vText.toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else vText
            }
            "b" -> if (vText == "1") "TRUE" else "FALSE"
            else -> vText
        }
    }

    private fun findFirstChild(parent: Element, localName: String): Node? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName.substringAfter(':') == localName) {
                return child
            }
        }
        return null
    }

    private fun columnIndexFromRef(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }
}
