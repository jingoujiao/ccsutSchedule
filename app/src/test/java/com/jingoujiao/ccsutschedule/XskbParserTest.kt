package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.XskbParser
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XskbParserTest {

    private val mondayCourseA = "大学生职业生涯规划与就业指导（上）\n\n毛一【5-12周】\n7-北303"
    private val wednesdayCourseB = "人工智能与计算机基础\n\n刘旭【6-8周】\n7-中306"
    private val fridayCourseC = "人工智能与计算机基础\n\n刘旭【16周】\n9-南404\n\n人工智能与计算机基础\n\n刘旭【5-15周】\n9-南404"
    private val mondayMilitary = "军事理论\n\n陆诗雨【3周】\n9-112\n\n军事理论\n\n陆诗雨【4周】\n7-北201"

    private fun sampleRows(): List<List<String>> = listOf(
        listOf("2026-2027学年第1学期 课表"),
        listOf("年级：2026  院系：经济与管理学院  专业：大数据管理与应用  姓名：李杰珉"),
        listOf("节次", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"),
        listOf("1.0", mondayCourseA, "", wednesdayCourseB, "", fridayCourseC, "", ""),
        listOf("2.0", mondayCourseA, "", wednesdayCourseB, "", fridayCourseC, "", ""),
        listOf("3.0", "", "高等数学2（上）\n\n于卫东【5-18周】\n7-南203", "", "", "", "", ""),
        listOf("4.0", "", "高等数学2（上）\n\n于卫东【5-18周】\n7-南203", "", "", "", "", ""),
        listOf("5.0", mondayMilitary, "", "", "", "", "", ""),
        listOf("6.0", mondayMilitary, "", "", "", "", "", ""),
        listOf("9.0", "", "", "", "", "", "", ""),
        listOf("10.0", "", "", "", "", "", "", ""),
    )

    private fun parseSample() = XskbParser.parse(ByteArrayInputStream(XlsxFixture.build(sampleRows())), "xskb.xlsx")

    @Test
    fun readsXlsxAndParsesAllCourses() {
        val result = parseSample()

        assertEquals(7, result.courses.size)
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        assertEquals("2026-2027学年第1学期 课表", result.title)
        assertTrue(result.ownerLabel.contains("大数据管理与应用"))
        assertTrue(result.ownerLabel.contains("李杰珉"))
    }

    @Test
    fun mergesAdjacentPeriodsIntoSingleBlock() {
        val result = parseSample()
        val courseA = result.courses.single { it.name.startsWith("大学生职业") }
        assertEquals(1, courseA.weekday)
        assertEquals(listOf(1, 2), courseA.periods)
        assertEquals((5..12).toList(), courseA.weeks)
        assertEquals("毛一", courseA.teacher)
        assertEquals("7-北303", courseA.location)
    }

    @Test
    fun mapsWeekdayColumnsFromHeaderText() {
        val result = parseSample()
        val wednesday = result.courses.single { it.name == "人工智能与计算机基础" && it.weekday == 3 }
        assertEquals(listOf(1, 2), wednesday.periods)
        assertEquals(listOf(6, 7, 8), wednesday.weeks)
        assertEquals("7-中306", wednesday.location)

        val math = result.courses.single { it.name.startsWith("高等数学") }
        assertEquals(2, math.weekday)
        assertEquals(listOf(3, 4), math.periods)
    }

    @Test
    fun keepsDifferentWeekRangesAsSeparateBlocks() {
        val result = parseSample()
        val friday = result.courses.filter { it.weekday == 5 }
        assertEquals(2, friday.size)
        assertEquals(listOf(16), friday.single { it.weeks == listOf(16) }.weeks)
        assertEquals((5..15).toList(), friday.single { it.weeks.size == 11 }.weeks)
        assertTrue(friday.all { it.periods == listOf(1, 2) })
    }

    @Test
    fun splitsMultipleCoursesInOneCell() {
        val result = parseSample()
        val mondayLower = result.courses.filter { it.weekday == 1 && it.name == "军事理论" }
        assertEquals(2, mondayLower.size)
        assertEquals(listOf(5, 6), mondayLower[0].periods)
        assertEquals(listOf(5, 6), mondayLower[1].periods)
        assertEquals(listOf(3), mondayLower.single { it.location == "9-112" }.weeks)
        assertEquals(listOf(4), mondayLower.single { it.location == "7-北201" }.weeks)
    }

    @Test
    fun doesNotCountEmptyTrailingPeriods() {
        val result = parseSample()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), result.usedPeriods)
        assertEquals((3..18).toList(), result.usedWeeks)
    }

    @Test
    fun sameCourseNameSharesColorKey() {
        val result = parseSample()
        val keys = result.courses.filter { it.name == "人工智能与计算机基础" }.map { it.colorKey }.distinct()
        assertEquals(1, keys.size)
    }

    @Test
    fun fallsBackToColumnOrderWhenHeaderIsMissing() {
        // 完全没有“星期”字样的表头：应退化为按列序（第 2-8 列 = 周一至周日）并给出告警
        val body = listOf(
            listOf("节次", "第一天", "第二天", "第三天", "第四天", "第五天", "第六天", "第七天"),
            listOf("1.0", "", "英语\n\n王老师【1-4周】\n1-101", "", "", "", "", ""),
        )
        val result = XskbParser.parse(body, "broken.xlsx")
        assertEquals(1, result.courses.size)
        assertEquals(2, result.courses.first().weekday)
        assertEquals("英语", result.courses.first().name)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun parsesPeriodNumbersTolerantly() {
        assertEquals(1, XskbParser.parsePeriod("1.0"))
        assertEquals(3, XskbParser.parsePeriod("第3节"))
        assertEquals(10, XskbParser.parsePeriod("10"))
        assertEquals(null, XskbParser.parsePeriod("备注"))
    }

    @Test
    fun parsesCellWithoutWeeksAsEveryWeek() {
        val blocks = XskbParser.parseCell("自习\n\n7-北101")
        assertEquals(1, blocks.size)
        val course = Course(id = "1", name = blocks[0].name, weekday = 1, periods = listOf(1), weeks = blocks[0].weeks)
        assertNotNull(course)
        assertTrue(course.weeks.isEmpty())
    }
}
