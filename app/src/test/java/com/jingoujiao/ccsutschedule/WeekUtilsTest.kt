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
}
