package com.jingoujiao.ccsutschedule.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** 周次表达式解析 / 格式化，以及「日期 ↔ 教学周」换算。纯 Kotlin，可单测。 */
object WeekUtils {

    /** 匹配 “5-12周”“16周”“1-4,6-8周”“6—17周”“5、7、9周” 里的周次主体。 */
    private val WEEK_GROUP = Regex(
        """(\d{1,2}(?:\s*[-–—~～至]\s*\d{1,2})?(?:\s*[,，、]\s*\d{1,2}(?:\s*[-–—~～至]\s*\d{1,2})?)*)\s*周"""
    )

    private val RANGE_SEPARATOR = Regex("""[-–—~～至]""")

    /** 从任意文本里解析出周次集合（升序去重）。解析不到返回空列表。 */
    fun parseWeeks(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val text = raw.replace('（', '(').replace('）', ')')
        val result = sortedSetOf<Int>()
        var single = false
        var double = false
        for (match in WEEK_GROUP.findAll(text)) {
            val body = match.groupValues[1]
            val tail = text.substring(match.range.last + 1, minOf(text.length, match.range.last + 6))
            if (tail.contains("单")) single = true
            if (tail.contains("双")) double = true
            for (part in body.split(',', '，', '、')) {
                val piece = part.trim()
                if (piece.isEmpty()) continue
                if (RANGE_SEPARATOR.containsMatchIn(piece)) {
                    val bounds = RANGE_SEPARATOR.split(piece).mapNotNull { it.trim().toIntOrNull() }
                    if (bounds.size >= 2) {
                        val from = minOf(bounds[0], bounds[1])
                        val to = maxOf(bounds[0], bounds[1])
                        if (to - from <= 40) {
                            for (w in from..to) result.add(w)
                        }
                    } else if (bounds.size == 1) {
                        result.add(bounds[0])
                    }
                } else {
                    piece.toIntOrNull()?.let { result.add(it) }
                }
            }
        }
        if (single && !double) {
            return result.filter { it % 2 == 1 }
        }
        if (double && !single) {
            return result.filter { it % 2 == 0 }
        }
        return result.filter { it in 1..60 }.sorted()
    }

    /** 该行是否像 “教师【5-12周】” 这样的周次行。 */
    fun looksLikeWeekLine(line: String): Boolean =
        line.contains('周') && WEEK_GROUP.containsMatchIn(line)

    /** 把周次集合压缩成 “5-12周” / “1-3,5-6周” / “每周”。 */
    fun formatWeeks(weeks: List<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return "每周"
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var prev = start
        for (i in 1 until sorted.size) {
            val current = sorted[i]
            if (current == prev + 1) {
                prev = current
                continue
            }
            parts.add(if (start == prev) "$start" else "$start-$prev")
            start = current
            prev = current
        }
        parts.add(if (start == prev) "$start" else "$start-$prev")
        return parts.joinToString(",") + "周"
    }

    val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val WEEKDAY_LONG_LABELS = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    fun weekdayLabel(weekday: Int): String = WEEKDAY_LABELS[(weekday - 1).coerceIn(0, 6)]

    fun weekdayLongLabel(weekday: Int): String = WEEKDAY_LONG_LABELS[(weekday - 1).coerceIn(0, 6)]

    fun parseIso(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return try {
            LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 计算 [date] 落在第几教学周（第 1 周为 [termStart] 所在周）。返回 1..99，未设置或早于开学返回 0。 */
    fun weekOf(date: LocalDate, termStart: LocalDate?): Int {
        if (termStart == null) return 0
        val startMonday = mondayOf(termStart)
        val target = mondayOf(date)
        val days = java.time.temporal.ChronoUnit.DAYS.between(startMonday, target)
        if (days < 0) return 0
        return (days / 7).toInt() + 1
    }

    /** 第 [week] 周对应的周一。 */
    fun mondayOfWeek(week: Int, termStart: LocalDate?): LocalDate? {
        if (termStart == null) return null
        return mondayOf(termStart).plusWeeks((week - 1).toLong())
    }

    fun formatMonthDay(date: LocalDate): String = "%d月%d日".format(date.monthValue, date.dayOfMonth)

    fun formatFull(date: LocalDate): String = "%d年%d月%d日".format(date.year, date.monthValue, date.dayOfMonth)

    /** 分钟数 → “1小时20分” 这类余量文案。 */
    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0分"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}分"
            m == 0 -> "${h}小时"
            else -> "${h}小时${m}分"
        }
    }
}
