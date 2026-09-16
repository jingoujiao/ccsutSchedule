package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.WeekUtils
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekUtilsTest {

    @Test
    fun parsesRangeWithArabicWeeks() {
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12), WeekUtils.parseWeeks("毛一【5-12周】"))
        assertEquals(listOf(16), WeekUtils.parseWeeks("刘旭【16周】"))
        assertEquals(listOf(19), WeekUtils.parseWeeks("喻毅【19周】"))
    }

    @Test
    fun parsesFullWidthAndListSeparators() {
        assertEquals(listOf(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17), WeekUtils.parseWeeks("凌芬【6—17周】"))
        assertEquals(listOf(5, 7, 9), WeekUtils.parseWeeks("【5、7、9周】"))
        assertEquals(listOf(1, 2, 3, 4, 6, 7, 8), WeekUtils.parseWeeks("【1-4,6-8周】"))
        assertEquals(listOf(1, 2, 3), WeekUtils.parseWeeks("第1-3周"))
    }

    @Test
    fun parsesSingleAndDoubleWeeks() {
        assertEquals(listOf(1, 3, 5), WeekUtils.parseWeeks("【1-5周(单)】"))
        assertEquals(listOf(2, 4, 6), WeekUtils.parseWeeks("【1-6周双周】"))
    }

    @Test
    fun returnsEmptyWhenNoWeeks() {
        assertEquals(emptyList<Int>(), WeekUtils.parseWeeks("7-南203"))
        assertEquals(emptyList<Int>(), WeekUtils.parseWeeks(""))
        assertEquals(emptyList<Int>(), WeekUtils.parseWeeks(null))
    }

    @Test
    fun formatsWeeks() {
        assertEquals("5-12周", WeekUtils.formatWeeks(listOf(5, 6, 7, 8, 9, 10, 11, 12)))
        assertEquals("1-3,5-6周", WeekUtils.formatWeeks(listOf(1, 2, 3, 5, 6)))
        assertEquals("7周", WeekUtils.formatWeeks(listOf(7)))
        assertEquals("每周", WeekUtils.formatWeeks(emptyList()))
    }

    @Test
    fun computesTeachingWeek() {
        val monday = WeekUtils.mondayOf(LocalDate.of(2026, 10, 7))
        assertEquals(1, WeekUtils.weekOf(monday, monday))
        assertEquals(1, WeekUtils.weekOf(monday.plusDays(6), monday))
        assertEquals(3, WeekUtils.weekOf(monday.plusWeeks(2), monday))
        assertEquals(0, WeekUtils.weekOf(monday.minusDays(1), monday))
        assertEquals(0, WeekUtils.weekOf(monday, null))
    }

    @Test
    fun reportsWeekDateRange() {
        val firstMonday = LocalDate.of(2026, 10, 5)
        assertEquals("10/5 ~ 10/11", WeekUtils.weekRangeLabel(1, firstMonday))
        assertEquals("10/12 ~ 10/18", WeekUtils.weekRangeLabel(2, firstMonday))
        assertEquals(null, WeekUtils.weekRangeLabel(1, null))
    }

    @Test
    fun teachingStatusExplainsWhetherTodayMatchesACourseWeek() {
        val firstMonday = LocalDate.of(2026, 10, 5)
        // 开学（9/19）与军训周都不算教学周：9/16 还没到第 1 周
        assertEquals(
            "今天 9月16日 还没到第 1 周（第 1 周从 10月5日 开始）",
            WeekUtils.teachingStatus(LocalDate.of(2026, 9, 16), firstMonday, 20),
        )
        assertEquals(
            "今天 10月7日 属于第 1 周",
            WeekUtils.teachingStatus(LocalDate.of(2026, 10, 7), firstMonday, 20),
        )
        assertEquals(
            "第 20 周已结束（最后一周到 2月21日）",
            WeekUtils.teachingStatus(LocalDate.of(2027, 3, 1), firstMonday, 20),
        )
        assertEquals(
            "还没告诉 App 第 1 周是哪一天，日期暂时对不上",
            WeekUtils.teachingStatus(LocalDate.of(2026, 9, 16), null, 20),
        )
    }

    @Test
    fun nonMondayAnchorIsNormalisedToItsWeek() {
        // 用户若把 9/19（周六，开学日）填进来，会被归到 9/14 那一周；
        // UI 会把这个归一化结果显式展示出来，避免整学期偏移而无人察觉。
        val wrong = LocalDate.of(2026, 9, 19)
        assertEquals(LocalDate.of(2026, 9, 14), WeekUtils.mondayOf(wrong))
        // 10/5 那一周相对 9/14 是第 4 周（不是第 1 周）—— 这就是“填错开学日”的后果
        assertEquals(4, WeekUtils.weekOf(LocalDate.of(2026, 10, 5), wrong))
        // 填入正确的第 1 周周一后，10/5 才是第 1 周
        assertEquals(1, WeekUtils.weekOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5)))
        // 全 App 统一入口：从存储文本取出的第 1 周周一一定对齐到周一
        assertEquals(LocalDate.of(2026, 9, 14), WeekUtils.firstWeekMonday("2026-09-19"))
        assertEquals(LocalDate.of(2026, 10, 5), WeekUtils.firstWeekMonday("2026-10-05"))
        assertEquals(null, WeekUtils.firstWeekMonday(""))
        assertEquals(null, WeekUtils.firstWeekMonday("不是日期"))
    }
}
