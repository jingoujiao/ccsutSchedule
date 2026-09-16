package com.jingoujiao.ccsutschedule.data

import java.io.InputStream

/**
 * 上课啦（xskb.xlsx）课表解析。
 *
 * 文件结构：第一列是节次，之后是若干「星期X」列；每个单元格里可能塞了多门课，
 * 每门课形如
 *
 *     高等数学2（上）
 *
 *     于卫东【5-18周】
 *     7-南203
 *
 * 课程之间以空行分隔。解析规则（与文件解耦，均可单测）：
 * 1. 表头优先按文字识别“星期X”，识别不出来时退化为按列序（第一列节次，后面依次周一..周日）；
 * 2. 每个「含【周次】」的行作为一节课的锚点，它上面最近的非空行是课名，下面紧邻的行是教室；
 * 3. 只有节次相邻且课名/教师/教室/周次完全相同的块才合并（第 1-2 节与第 5-6 节不会并成一个）。
 */
object XskbParser {

    data class Result(
        val title: String = "",
        val ownerLabel: String = "",
        val courses: List<Course> = emptyList(),
        val warnings: List<String> = emptyList(),
        val usedPeriods: List<Int> = emptyList(),
        val usedWeeks: List<Int> = emptyList(),
    )

    data class Block(
        val name: String,
        val teacher: String,
        val location: String,
        val weeks: List<Int>,
    )

    private val WEEKDAY_PATTERN = Regex("""(?:星期|周)([一二三四五六日天])""")

    fun parse(input: InputStream, sourceLabel: String = ""): Result = parse(XlsxReader.read(input).rows, sourceLabel)

    fun parse(rows: List<List<String>>, sourceLabel: String = ""): Result {
        val warnings = mutableListOf<String>()
        val grid = rows.map { row -> row.map { it.replace('\u00a0', ' ').trim() } }
        if (grid.isEmpty()) throw IllegalArgumentException("表格是空的")

        val header = findHeader(grid)

        val weekdayColumns: Map<Int, Int>
        var periodColumn: Int
        var dataStart: Int
        if (header != null) {
            weekdayColumns = header.columns
            periodColumn = header.periodColumn
            dataStart = header.rowIndex + 1
        } else {
            // 退化：第一列节次，第 2..8 列依次为周一..周日
            weekdayColumns = (1..7).associateWith { it }
            periodColumn = 0
            dataStart = 0
            warnings.add("没能识别到“星期一…星期日”表头，已按列序（第 2-8 列 = 周一至周日）解析，请核对结果。")
        }

        val courses = mutableListOf<Course>()
        var counter = 0
        for (rowIndex in dataStart until grid.size) {
            val row = grid[rowIndex]
            val periodCell = row.getOrNull(periodColumn).orEmpty()
            val rowPeriods = parsePeriods(periodCell)
            val hasAnyContent = row.any { it.isNotBlank() }
            if (rowPeriods.isEmpty()) {
                if (hasAnyContent && rowIndex > dataStart && looksLikeCourseRow(row, weekdayColumns)) {
                    warnings.add("第 ${rowIndex + 1} 行的节次“$periodCell”无法识别，已跳过该行。")
                }
                continue
            }
            for ((weekday, column) in weekdayColumns) {
                val text = row.getOrNull(column).orEmpty()
                if (text.isBlank()) continue
                val blocks = parseCell(text)
                if (blocks.isEmpty()) continue
                for (block in blocks) {
                    counter += 1
                    courses.add(
                        Course(
                            id = "xskb-%03d".format(counter),
                            name = block.name,
                            teacher = block.teacher,
                            location = block.location,
                            weekday = weekday,
                            periods = rowPeriods,
                            weeks = block.weeks,
                        )
                    )
                }
            }
        }

        if (courses.isEmpty()) {
            warnings.add("没有解析到任何课程，请确认选择的是教务处导出的课表文件。")
        }

        val merged = mergeAdjacent(courses)
        if (merged.any { it.weeks.isEmpty() }) {
            warnings.add("有 ${merged.count { it.weeks.isEmpty() }} 个课程块没有读到周次，已按“每周”处理。")
        }

        return Result(
            title = extractTitle(grid),
            ownerLabel = extractOwner(grid),
            courses = merged,
            warnings = warnings,
            usedPeriods = merged.flatMap { it.periods }.distinct().sorted(),
            usedWeeks = merged.flatMap { it.weeks }.distinct().sorted(),
        )
    }

    private data class Header(val rowIndex: Int, val columns: Map<Int, Int>, val periodColumn: Int)

    private fun findHeader(grid: List<List<String>>): Header? {
        var best: Header? = null
        var bestScore = 0
        grid.forEachIndexed { rowIndex, row ->
            val columns = LinkedHashMap<Int, Int>()
            row.forEachIndexed { column, text ->
                if (text.isBlank()) return@forEachIndexed
                val match = WEEKDAY_PATTERN.find(text) ?: return@forEachIndexed
                val weekday = weekdayOf(match.groupValues[1]) ?: return@forEachIndexed
                if (!columns.containsKey(weekday)) columns[weekday] = column
            }
            if (columns.size > bestScore) {
                bestScore = columns.size
                val firstWeekdayColumn = columns.values.min()
                val periodColumn = (0 until firstWeekdayColumn).firstOrNull { column ->
                    row.getOrNull(column).orEmpty().let { it.isNotBlank() && WEEKDAY_PATTERN.find(it) == null }
                } ?: (firstWeekdayColumn - 1).coerceAtLeast(0)
                best = Header(rowIndex, columns.toSortedMap(), periodColumn)
            }
        }
        // 至少认出 3 天，且必须同时含周一或周五，才算可靠表头
        val header = best ?: return null
        if (header.columns.size < 3) return null
        if (!header.columns.containsKey(1) && !header.columns.containsKey(5)) return null
        return header
    }

    private fun weekdayOf(char: String): Int? = when (char) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "日", "天" -> 7
        else -> null
    }

    private fun looksLikeCourseRow(row: List<String>, weekdayColumns: Map<Int, Int>): Boolean =
        weekdayColumns.values.any { row.getOrNull(it).orEmpty().isNotBlank() }

    /**
     * 节次单元格 → 节次列表。
     *
     * 教务系统的导出有两种常见写法：
     *  - 一行一节：`1`、`2`、`10`（本校当前文件）
     *  - 一行两节：`1-2`、`3-4`、`第5-6节`、`1、2`
     * 后者如果只取第一个数字，就会出现「占两节的课只占一节」。
     * 另外 `1.0` 这种带小数的写法只应取到 1，不能把小数位当成第二个节次。
     */
    fun parsePeriods(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val normalized = text
            .replace('－', '-').replace('—', '-').replace('–', '-')
            .replace('～', '-').replace('~', '-').replace('至', '-')
            .replace('．', '.').replace('。', '.')
        // 带小数点的数字（1.0）先整体吃掉，避免把小数位当成节次
        val tokens = Regex("""\d{1,2}(?:\.\d+)?""").findAll(normalized).map { it.value }.toList()
        if (tokens.isEmpty()) return emptyList()
        val numbers = tokens.mapNotNull { token -> token.substringBefore('.').toIntOrNull() }
            .filter { it in 1..20 }
        if (numbers.isEmpty()) return emptyList()
        val first = numbers.first()
        if (numbers.size >= 2) {
            val second = numbers[1]
            // `1-2` / `1、2` 这类区间写法：第二个数字是更大的节次
            if (second > first && second - first <= 4) {
                return (first..second).toList()
            }
        }
        return listOf(first)
    }

    /** 兼容旧调用：只取第一个节次。 */
    fun parsePeriod(text: String): Int? = parsePeriods(text).firstOrNull()

    /** 单元格文本 → 课程块列表。 */
    fun parseCell(text: String): List<Block> {
        val lines = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val blocks = mutableListOf<Block>()
        var pendingName: String? = null
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (isAnchorLine(line)) {
                val name = pendingName ?: lines.getOrNull(index - 1) ?: ""
                val teacher = line.substringBefore('【').substringBefore('[').trim()
                val weeks = WeekUtils.parseWeeks(line)
                var location = ""
                val next = lines.getOrNull(index + 1)
                if (next != null && !isAnchorLine(next)) {
                    val afterNext = lines.getOrNull(index + 2)
                    val nextIsNextCourseName = afterNext != null &&
                        isAnchorLine(afterNext) &&
                        !looksLikeLocation(next)
                    if (!nextIsNextCourseName) {
                        location = next
                        index += 1
                    }
                }
                if (name.isNotBlank()) {
                    blocks.add(Block(name, teacher, location, weeks))
                }
                pendingName = null
            } else {
                pendingName = line
            }
            index += 1
        }
        if (blocks.isEmpty() && lines.isNotEmpty()) {
            // 整个格子没有任何「【周次】」锚点：第一行当课名，紧跟的“像教室”的行当教室，周次按“每周”。
            val name = lines.first()
            val location = lines.getOrNull(1)?.takeIf { looksLikeLocation(it) }.orEmpty()
            if (name.isNotBlank()) {
                blocks.add(Block(name, "", location, emptyList()))
            }
        }
        return blocks
    }

    private fun isAnchorLine(line: String): Boolean = line.contains('【') || WeekUtils.looksLikeWeekLine(line)

    private fun looksLikeLocation(text: String): Boolean =
        text.length <= 24 && (text.contains('-') || text.contains('—') || text.contains('–'))

    /** 相同课名/教师/教室/周次且节次相邻的块合并（第 1-2 节 → periods=[1,2]）。 */
    fun mergeAdjacent(courses: List<Course>): List<Course> {
        val result = mutableListOf<Course>()
        val groups = courses.groupBy { listOf(it.weekday, it.name, it.teacher, it.location, it.weeks) }
        for ((_, group) in groups) {
            val sorted = group.sortedBy { it.startPeriod }
            var current: Course? = null
            for (course in sorted) {
                val existing = current
                if (existing != null && course.startPeriod <= existing.endPeriod + 1) {
                    val periods = (existing.periods + course.periods).distinct().sorted()
                    current = existing.copy(periods = periods)
                } else {
                    existing?.let { result.add(it) }
                    current = course
                }
            }
            current?.let { result.add(it) }
        }
        return result
            .sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.name }))
            .mapIndexed { index, course -> course.copy(colorKey = index) }
            .let { assignColorKeys(it) }
    }

    /** 同名课程用同一个颜色，一周内尽量分散。 */
    private fun assignColorKeys(courses: List<Course>): List<Course> {
        val byName = LinkedHashMap<String, Int>()
        return courses.map { course ->
            val key = byName.getOrPut(course.name) { byName.size % COURSE_COLOR_COUNT }
            course.copy(colorKey = key)
        }
    }

    private fun extractTitle(grid: List<List<String>>): String {
        for (row in grid) {
            for (cell in row) {
                if (cell.contains("学期") && (cell.contains("学年") || cell.contains("课表"))) {
                    return cell.replace(Regex("""\s+"""), " ").trim()
                }
            }
        }
        for (row in grid) {
            for (cell in row) {
                if (cell.contains("学期")) return cell.trim()
            }
        }
        return ""
    }

    private fun extractOwner(grid: List<List<String>>): String {
        for (row in grid) {
            for (cell in row) {
                if (cell.contains("年级") || cell.contains("专业") || cell.contains("院系")) {
                    val clean = cell.replace(Regex("""\s+"""), " ").trim()
                    return clean
                        .replace(Regex("""^(年级[:：]?\s*)"""), "")
                        .replace("年级：", "")
                        .trim()
                }
            }
        }
        return ""
    }
}
