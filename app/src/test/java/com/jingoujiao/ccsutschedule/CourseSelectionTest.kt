package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.coursesVisibleInWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表网格的「按周过滤」。
 *
 * 这一条曾经在 1.4.0 的重构里被漏掉：网格画的是全量课程，
 * 结果每一周看起来一模一样、同一时段还会多出别的周的课。
 */
class CourseSelectionTest {

    private fun course(
        id: String,
        weekday: Int,
        weeks: List<Int>,
        name: String = "课",
    ) = Course(
        id = id,
        name = name,
        weekday = weekday,
        periods = listOf(5, 6),
        weeks = weeks,
    )

    @Test
    fun `默认只画这一周有效的课`() {
        val courses = listOf(
            course("a", weekday = 1, weeks = listOf(3)),
            course("b", weekday = 1, weeks = listOf(4)),
            course("c", weekday = 2, weeks = listOf(4, 5)),
        )

        val week4 = coursesVisibleInWeek(courses, week = 4, showOtherWeeks = false)

        // 第 3 周那份「军事理论」不会串到第 4 周来（以前会多出一格）
        assertEquals(listOf("b", "c"), week4.map { it.id })
    }

    @Test
    fun `每周的课（周次为空）每一周都画`() {
        val courses = listOf(course("every", weekday = 3, weeks = emptyList()))

        assertTrue(coursesVisibleInWeek(courses, week = 1, showOtherWeeks = false).any { it.id == "every" })
        assertTrue(coursesVisibleInWeek(courses, week = 19, showOtherWeeks = false).any { it.id == "every" })
    }

    @Test
    fun `打开显示非本周课程时画全部`() {
        val courses = listOf(
            course("a", weekday = 1, weeks = listOf(3)),
            course("b", weekday = 1, weeks = listOf(4)),
        )

        val all = coursesVisibleInWeek(courses, week = 4, showOtherWeeks = true)

        assertEquals(listOf("a", "b"), all.map { it.id })
    }

    @Test
    fun `没有课的周返回空`() {
        val courses = listOf(course("a", weekday = 1, weeks = listOf(5, 6)))

        assertTrue(coursesVisibleInWeek(courses, week = 1, showOtherWeeks = false).isEmpty())
    }
}
