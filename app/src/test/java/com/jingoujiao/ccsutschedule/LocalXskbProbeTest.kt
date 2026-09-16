package com.jingoujiao.ccsutschedule

import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.data.XskbParser
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 用仓库根目录下的真实 `xskb.xlsx` 做一次端到端体检。
 *
 * 文件属于个人数据、不入库：文件不存在时本用例自动跳过（不算失败）。
 * 存在时会打印解析摘要，方便人工核对“有没有漏课/串行”。
 */
class LocalXskbProbeTest {

    @Test
    fun parsesRealExportedSchedule() {
        val file = File("../xskb.xlsx").takeIf { it.exists() } ?: File("xskb.xlsx")
        assumeTrue("未找到本机真实课表 xskb.xlsx，跳过", file.exists())

        val result = file.inputStream().use { XskbParser.parse(it, file.name) }

        println("=== xskb 解析摘要 ===")
        println("学期：${result.title}")
        println("身份：${result.ownerLabel}")
        println("课程块：${result.courses.size}")
        println("节次：${result.usedPeriods}")
        println("周次：${result.usedWeeks.firstOrNull()}..${result.usedWeeks.lastOrNull()}")
        println("告警：${result.warnings}")
        result.courses.forEach { course ->
            println(
                "  ${WeekUtils.weekdayLabel(course.weekday)} 第${course.periods.first()}-${course.periods.last()}节 " +
                    "${course.name} / ${course.teacher} / ${course.location} / ${WeekUtils.formatWeeks(course.weeks)}"
            )
        }

        assertTrue("真实课表应当解析出课程", result.courses.isNotEmpty())
        assertTrue("每门课都应当有周次", result.courses.all { it.weeks.isNotEmpty() })
        assertTrue("不应该出现第 9 节以后的空节次", result.usedPeriods.all { it <= 10 })
    }
}
