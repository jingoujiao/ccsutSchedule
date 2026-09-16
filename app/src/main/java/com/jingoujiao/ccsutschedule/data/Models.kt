package com.jingoujiao.ccsutschedule.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * 一门课在课表网格里的一个「格子块」。
 *
 * [periods] 是连续节次（如 [1,2] 表示第 1-2 节连上），[weeks] 是该块生效的教学周，
 * 空列表语义为「每周」。
 */
@Serializable
data class Course(
    val id: String,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val weekday: Int,
    val periods: List<Int> = emptyList(),
    val weeks: List<Int> = emptyList(),
    val colorKey: Int = 0,
    val note: String = "",
) {
    val startPeriod: Int get() = periods.minOrNull() ?: 1
    val endPeriod: Int get() = periods.maxOrNull() ?: startPeriod
    val span: Int get() = (endPeriod - startPeriod + 1).coerceAtLeast(1)

    fun activeInWeek(week: Int): Boolean = weeks.isEmpty() || weeks.contains(week)

    fun sameBlockAs(other: Course): Boolean =
        weekday == other.weekday &&
            name == other.name &&
            teacher == other.teacher &&
            location == other.location &&
            weeks == other.weeks
}

@Serializable
data class PeriodTime(
    val index: Int,
    val start: String,
    val end: String,
) {
    val startMinutes: Int get() = toMinutes(start)
    val endMinutes: Int get() = toMinutes(end)

    companion object {
        fun toMinutes(text: String): Int {
            val parts = text.split(":")
            val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return -1
            val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            return h * 60 + m
        }

        fun format(minutes: Int): String {
            val h = (minutes / 60).coerceIn(0, 23)
            val m = (minutes % 60).coerceIn(0, 59)
            return "%02d:%02d".format(h, m)
        }
    }
}

@Serializable
data class ScheduleData(
    /** 学期标题，导入时从文件里读取，例如 “2026-2027学年第1学期”。 */
    val title: String = "",
    /** 导入时读取到的身份信息，例如 “经济与管理学院 · 大数据管理与应用 · 李杰珉”。 */
    val ownerLabel: String = "",
    /** 数据来源说明，例如 “xskb.xlsx · 2026-10-08 12:00 导入”。 */
    val importSource: String = "",
    val courses: List<Course> = emptyList(),
)

@Serializable
data class AppSettings(
    /**
     * 课表第 1 周的周一（ISO yyyy-MM-dd）。留空表示尚未设置。
     *
     * 注意：这不是「开学日」。xskb.xlsx 里没有任何日期，只有「第几周有课」，
     * 所以必须由用户指定「第 1 周周一 = 哪一天」，日期才能和课程对上；
     * 开学日与正式上课日往往不是同一周（开学、军训那几周通常不算教学周）。
     *
     * 存储键用新名 `firstWeekMonday`，同时接受旧键 `termStartDate`，老数据不会丢。
     */
    @OptIn(ExperimentalSerializationApi::class)
    @JsonNames("termStartDate")
    val firstWeekMonday: String = "",
    val totalWeeks: Int = 20,
    val periods: List<PeriodTime> = defaultPeriodTimes(),
    val themeMode: String = ThemeMode.SYSTEM,
    /** 主题主色相（0..359），由配色方案决定。 */
    val paletteHue: Int = 222,
    /** 自定义背景图片的绝对路径（App 私有目录内）。 */
    val backgroundImagePath: String = "",
    /** 背景图不透明度 0..1。 */
    val backgroundAlpha: Float = 0.28f,
    /** 是否在网格里用淡色显示非本周课程。 */
    val showOtherWeeks: Boolean = false,
)

@Serializable
data class AppStateData(
    val schedule: ScheduleData = ScheduleData(),
    val settings: AppSettings = AppSettings(),
)

object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    val all: List<String> = listOf(SYSTEM, LIGHT, DARK)

    fun label(mode: String): String = when (mode) {
        LIGHT -> "浅色"
        DARK -> "深色"
        else -> "跟随系统"
    }
}

/** 默认作息：8 节课 + 2 节晚课，用户可在设置里逐节修改。 */
fun defaultPeriodTimes(): List<PeriodTime> = listOf(
    PeriodTime(1, "08:00", "08:45"),
    PeriodTime(2, "08:55", "09:40"),
    PeriodTime(3, "10:00", "10:45"),
    PeriodTime(4, "10:55", "11:40"),
    PeriodTime(5, "14:00", "14:45"),
    PeriodTime(6, "14:55", "15:40"),
    PeriodTime(7, "16:00", "16:45"),
    PeriodTime(8, "16:55", "17:40"),
    PeriodTime(9, "19:00", "19:45"),
    PeriodTime(10, "19:55", "20:40"),
)

/** 配色方案预设：只存主色相，具体色板由色相推导。 */
object PalettePresets {
    data class Preset(val name: String, val hue: Int)

    val all: List<Preset> = listOf(
        Preset("长工蓝", 222),
        Preset("青竹", 168),
        Preset("紫藤", 276),
        Preset("枫橘", 24),
        Preset("松墨", 330),
    )

    fun of(hue: Int): Preset = all.minByOrNull { kotlin.math.abs(it.hue - hue) } ?: all.first()
}

/** 课程卡片配色数量，配色由 [com.jingoujiao.ccsutschedule.ui.theme.courseColor] 按 key 推导。 */
const val COURSE_COLOR_COUNT = 12
