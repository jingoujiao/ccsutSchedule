package com.jingoujiao.ccsutschedule

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 测试用 xlsx 生成器：按真实「上课啦」导出文件的同一结构造一个最小工作簿，
 * 这样 XlsxReader 与 XskbParser 可以在 JVM 上端到端验证，仓库里不需要放任何真实课表。
 */
object XlsxFixture {

    fun build(rows: List<List<String>>): ByteArray {
        val shared = LinkedHashMap<String, Int>()
        rows.forEach { row -> row.forEach { cell -> if (cell.isNotEmpty()) shared.getOrPut(cell) { shared.size } } }

        val sheetRows = rows.mapIndexed { rowIndex, row ->
            val cells = row.mapIndexedNotNull { columnIndex, text ->
                if (text.isEmpty()) return@mapIndexedNotNull null
                val ref = columnName(columnIndex) + (rowIndex + 1)
                """<c r="$ref" t="s"><v>${shared[text]}</v></c>"""
            }.joinToString("")
            """<row r="${rowIndex + 1}">$cells</row>"""
        }.joinToString("")

        val sharedStrings = shared.keys.joinToString("") { "<si><t xml:space=\"preserve\">${escape(it)}</t></si>" }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                             xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                     <sheets><sheet name="xskb" sheetId="1" r:id="rId1"/></sheets>
                   </workbook>"""
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                     <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
                                   Target="worksheets/sheet1.xml"/>
                   </Relationships>"""
            )
            put("xl/sharedStrings.xml", """<?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                     count="${shared.size}" uniqueCount="${shared.size}">$sharedStrings</sst>""")
            put(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                     <sheetData>$sheetRows</sheetData>
                   </worksheet>"""
            )
        }
        return out.toByteArray()
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        val builder = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            builder.insert(0, ('A' + remainder))
            value = (value - 1) / 26
        }
        return builder.toString()
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
