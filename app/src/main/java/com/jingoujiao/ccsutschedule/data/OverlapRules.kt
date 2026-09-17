package com.jingoujiao.ccsutschedule.data

/**
 * 同一时段最多允许几门课重叠。
 *
 * 一个格子挤 4 门课以上就没法看了，所以「保存课程」「拖动改期」都会被这条规则拦住；
 * 网格渲染也会把超过上限的部分折叠成 `+N`。
 */
const val MAX_OVERLAP: Int = 3

/**
 * 时段重叠规则（纯函数，可单测）。
 *
 * 「重叠」= 同一天 + 有共同节次 + 周次有交集（周次为空表示每周，与谁都相交）。
 */
object OverlapRules {

    fun weeksIntersect(a: List<Int>, b: List<Int>): Boolean =
        a.isEmpty() || b.isEmpty() || a.any { it in b }

    fun overlaps(a: Course, b: Course): Boolean =
        a.weekday == b.weekday &&
            a.periods.any { it in b.periods } &&
            weeksIntersect(a.weeks, b.weeks)

    /** 与 [course] 在时间上重叠的其它课程。 */
    fun conflictsWith(course: Course, all: List<Course>): List<Course> =
        all.filter { it.id != course.id && overlaps(course, it) }

    /** [weekday] 第 [period] 节上，和 [weeks] 有交集的课程数（不含 [excludeId]）。 */
    fun countAt(
        all: List<Course>,
        weekday: Int,
        period: Int,
        weeks: List<Int>,
        excludeId: String? = null,
    ): Int = all.count { course ->
        course.id != excludeId &&
            course.weekday == weekday &&
            period in course.periods &&
            weeksIntersect(course.weeks, weeks)
    }

    /**
     * 这门课还能不能放下：任何一节上的重叠数达到 [MAX_OVERLAP] 就不行。
     *
     * @return null 表示可以；否则返回给用户看的中文原因。
     */
    fun rejectReason(course: Course, all: List<Course>): String? {
        course.periods.forEach { period ->
            val count = countAt(all, course.weekday, period, course.weeks, course.id)
            if (count >= MAX_OVERLAP) {
                return "${WeekUtils.weekdayLabel(course.weekday)}第 $period 节已有 $count 门课，" +
                    "同一时段最多 $MAX_OVERLAP 门"
            }
        }
        return null
    }
}
