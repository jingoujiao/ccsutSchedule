package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.MAX_OVERLAP
import com.jingoujiao.ccsutschedule.data.OverlapRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 同一时段最多 3 门课的规则。 */
class OverlapRulesTest {

    private fun course(
        id: String,
        weekday: Int = 1,
        periods: List<Int> = listOf(1),
        weeks: List<Int> = emptyList(),
        name: String = id,
    ) = Course(id = id, name = name, weekday = weekday, periods = periods, weeks = weeks)

    @Test
    fun `周次为空表示每周，与谁都相交`() {
        assertTrue(OverlapRules.weeksIntersect(emptyList(), listOf(1, 2, 3)))
        assertTrue(OverlapRules.weeksIntersect(listOf(5), emptyList()))
        assertTrue(OverlapRules.weeksIntersect(emptyList(), emptyList()))
    }

    @Test
    fun `周次不交叉的课不算重叠`() {
        assertFalse(OverlapRules.weeksIntersect(listOf(1, 2, 3), listOf(4, 5, 6)))
        assertFalse(OverlapRules.overlaps(course("a", weeks = listOf(1, 2)), course("b", weeks = listOf(3, 4))))
    }

    @Test
    fun `不同星期或不同节次不算重叠`() {
        assertFalse(OverlapRules.overlaps(course("a", weekday = 1), course("b", weekday = 2)))
        assertFalse(OverlapRules.overlaps(course("a", periods = listOf(1, 2)), course("b", periods = listOf(3, 4))))
        assertTrue(OverlapRules.overlaps(course("a", periods = listOf(1, 2)), course("b", periods = listOf(2, 3))))
    }

    @Test
    fun `countAt 只数同一节上有周次交集的课`() {
        val all = listOf(
            course("a", weekday = 2, periods = listOf(3, 4)),
            course("b", weekday = 2, periods = listOf(4)),
            course("c", weekday = 2, periods = listOf(4), weeks = listOf(9, 10)),
            course("d", weekday = 3, periods = listOf(4)),
        )
        assertEquals(2, OverlapRules.countAt(all, weekday = 2, period = 4, weeks = listOf(1, 2)))
        assertEquals(3, OverlapRules.countAt(all, weekday = 2, period = 4, weeks = listOf(9)))
        assertEquals(2, OverlapRules.countAt(all, weekday = 2, period = 4, weeks = listOf(9), excludeId = "c"))
        assertEquals(0, OverlapRules.countAt(all, weekday = 2, period = 9, weeks = emptyList()))
    }

    @Test
    fun `已经有 2 门重叠还能再放第 3 门`() {
        val all = listOf(
            course("a", weekday = 2, periods = listOf(3, 4)),
            course("b", weekday = 2, periods = listOf(4)),
        )
        val candidate = course("new", weekday = 2, periods = listOf(4))
        assertNull(OverlapRules.rejectReason(candidate, all))
    }

    @Test
    fun `已经有 3 门重叠就存不下了`() {
        val all = listOf(
            course("a", weekday = 2, periods = listOf(4)),
            course("b", weekday = 2, periods = listOf(4)),
            course("c", weekday = 2, periods = listOf(4)),
        )
        val candidate = course("new", weekday = 2, periods = listOf(4))
        val reason = OverlapRules.rejectReason(candidate, all)
        assertNotNull(reason)
        assertTrue("提示里应当说明是第几节", reason!!.contains("第 4 节"))
        assertEquals(MAX_OVERLAP, 3)
    }

    @Test
    fun `跨多节时任意一节到顶都不行`() {
        val all = listOf(
            course("a", weekday = 5, periods = listOf(7)),
            course("b", weekday = 5, periods = listOf(7)),
            course("c", weekday = 5, periods = listOf(7)),
        )
        // 第 6 节是空的，但第 7 节已经 3 门
        assertNotNull(OverlapRules.rejectReason(course("new", weekday = 5, periods = listOf(6, 7)), all))
        assertNull(OverlapRules.rejectReason(course("new", weekday = 5, periods = listOf(5, 6)), all))
    }

    @Test
    fun `编辑自己时不算自己占的那一格`() {
        val all = listOf(
            course("a", weekday = 2, periods = listOf(4)),
            course("b", weekday = 2, periods = listOf(4)),
            course("c", weekday = 2, periods = listOf(4)),
        )
        // c 自己已经把这一节占满了，改成同一天同一节应当允许
        assertNull(OverlapRules.rejectReason(all.first { it.id == "c" }, all))
    }

    @Test
    fun `conflictsWith 返回所有时间重叠的课`() {
        val all = listOf(
            course("a", weekday = 1, periods = listOf(1, 2)),
            course("b", weekday = 1, periods = listOf(2, 3)),
            course("c", weekday = 1, periods = listOf(5)),
        )
        val conflicts = OverlapRules.conflictsWith(all[0], all)
        assertEquals(listOf("b"), conflicts.map { it.id })
    }
}
