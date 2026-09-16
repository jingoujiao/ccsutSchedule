package com.jingoujiao.ccsutschedule.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * 极简 xlsx 读取器：只依赖 java.util.zip 与 JDK/Android 自带的 XML 解析器，
 * 不引入任何第三方库（本机离线，也不需要在 APK 里塞 POI）。
 *
 * 只取第一个工作表的文本网格，保留行列位置。
 */
object XlsxReader {

    data class Sheet(val name: String, val rows: List<List<String>>)

    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"

    fun read(input: InputStream): Sheet {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name.endsWith(".xml")) {
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = zip.read(chunk)
                        if (read <= 0) break
                        total += read
                        if (total > 12L * 1024 * 1024) break // 单个 XML 条目上限，防异常文件
                        buffer.write(chunk, 0, read)
                    }
                    entries[name] = buffer.toByteArray()
                }
                zip.closeEntry()
            }
        }
        if (entries.isEmpty()) throw IllegalArgumentException("不是有效的 xlsx 文件（没有读到任何 XML 内容）")

        val shared = entries[SHARED_STRINGS]?.let(::parseSharedStrings) ?: emptyList()
        val sheetPath = resolveFirstSheetPath(entries) ?: entries.keys.firstOrNull { it.startsWith("xl/worksheets/") }
            ?: throw IllegalArgumentException("xlsx 里没有工作表")
        val sheetBytes = entries[sheetPath] ?: throw IllegalArgumentException("找不到工作表 $sheetPath")
        val rows = parseSheet(sheetBytes, shared)
        return Sheet(sheetPath.substringAfterLast('/').substringBefore('.'), rows)
    }

    private fun resolveFirstSheetPath(entries: Map<String, ByteArray>): String? {
        val workbook = entries[WORKBOOK]?.let { runCatching { parseXml(it) }.getOrNull() } ?: return null
        val sheet = workbook.getElementsByTagName("sheet").item(0) as? Element ?: return null
        val relId = sheet.getAttribute("r:id").ifBlank { sheet.getAttribute("id") }
        if (relId.isBlank()) return null
        val rels = entries[WORKBOOK_RELS]?.let { runCatching { parseXml(it) }.getOrNull() } ?: return null
        val relationships = rels.getElementsByTagName("Relationship")
        for (i in 0 until relationships.length) {
            val element = relationships.item(i) as? Element ?: continue
            if (element.getAttribute("Id") == relId) {
                val target = element.getAttribute("Target")
                return when {
                    target.startsWith("/") -> target.removePrefix("/")
                    target.startsWith("xl/") -> target
                    else -> "xl/" + target.removePrefix("./")
                }
            }
        }
        return null
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes)
        val items = document.getElementsByTagName("si")
        val result = ArrayList<String>(items.length)
        for (i in 0 until items.length) {
            val element = items.item(i) as? Element ?: continue
            val texts = element.getElementsByTagName("t")
            val builder = StringBuilder()
            for (t in 0 until texts.length) {
                builder.append(texts.item(t).textContent ?: "")
            }
            result.add(builder.toString())
        }
        return result
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val document = parseXml(bytes)
        val rowNodes = document.getElementsByTagName("row")
        val rows = ArrayList<List<String>>(rowNodes.length)
        for (i in 0 until rowNodes.length) {
            val rowElement = rowNodes.item(i) as? Element ?: continue
            val cells = rowElement.getElementsByTagName("c")
            val byIndex = HashMap<Int, String>()
            var maxIndex = -1
            var cursor = 0
            for (c in 0 until cells.length) {
                val cell = cells.item(c) as? Element ?: continue
                val ref = cell.getAttribute("r")
                val columnIndex = if (ref.isNotBlank()) columnIndex(ref) else cursor
                cursor = columnIndex + 1
                val type = cell.getAttribute("t")
                val value = when (type) {
                    "s" -> {
                        val v = firstChildText(cell, "v")
                        v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    }

                    "inlineStr" -> {
                        val textNodes = cell.getElementsByTagName("t")
                        val builder = StringBuilder()
                        for (t in 0 until textNodes.length) builder.append(textNodes.item(t).textContent ?: "")
                        builder.toString()
                    }

                    else -> firstChildText(cell, "v") ?: ""
                }
                if (value.isNotEmpty()) {
                    byIndex[columnIndex] = value
                    if (columnIndex > maxIndex) maxIndex = columnIndex
                }
            }
            val row = ArrayList<String>(maxIndex + 1)
            for (index in 0..maxIndex) row.add(byIndex[index] ?: "")
            rows.add(row)
        }
        return rows
    }

    private fun firstChildText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent
    }

    /** “BC12” → 列索引 54（0 基）。 */
    private fun columnIndex(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (ch.isLetter()) {
                index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else {
                break
            }
        }
        return index - 1
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.isExpandEntityReferences = false }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}
