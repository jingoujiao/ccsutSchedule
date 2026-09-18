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

/**
 * 某一周课表网格里真正要画的课程。
 *
 * 默认**只画这一周有效的课**：不过滤的话，别的周的课会串到每一页上——每周看起来一模一样，
 * 同一时段还会凭空多出重叠的卡片（比如第 3 周与第 4 周的两份「军事理论」一起画出来）。
 * 打开设置里的「显示非本周课程」时才把其它周的课也画出来（由调用方淡显）。
 *
 * 纯函数，有单测（这个过滤在 1.4.0 的重构里被漏掉过，别再丢）。
 */
fun coursesVisibleInWeek(
    courses: List<Course>,
    week: Int,
    showOtherWeeks: Boolean,
): List<Course> = if (showOtherWeeks) courses else courses.filter { it.activeInWeek(week) }

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
    /** 自定义背景图片的绝对路径（App 私有目录内）；为空时用 [backgroundPreset]。 */
    val backgroundImagePath: String = "",
    /** 内置壁纸 id，见 [BackgroundPresets]；空字符串表示只用渐变底色。 */
    val backgroundPreset: String = BackgroundPresets.DEFAULT_ID,
    /** 背景图不透明度 0..1。 */
    val backgroundAlpha: Float = 0.28f,
    /** 玻璃模糊度（磨砂强度）0..1，见 [DEFAULT_GLASS_FROST]。 */
    val glassFrost: Float = DEFAULT_GLASS_FROST,
    /** 课程卡片风格，见 [CourseCardStyle]。 */
    val courseCardStyle: String = CourseCardStyle.COLORED,
    /** 课表字号倍率，见 [SCHEDULE_FONT_SCALE_RANGE]。 */
    val scheduleFontScale: Float = 1f,
    /** 课表文字不透明度（能见度），1 = 完全不透明。 */
    val scheduleFontAlpha: Float = 1f,
    /** 是否在网格里用淡色显示非本周课程。 */
    val showOtherWeeks: Boolean = false,
    /** 启动时自动检查更新。 */
    val autoCheckUpdates: Boolean = true,
    /** 更新包下载源，见 [UpdateSource]。 */
    val updateSource: String = UpdateSource.AUTO,
    /** 自定义加速前缀（[UpdateSource.CUSTOM] 时使用）。 */
    val customMirror: String = "",
    /** Gitee 镜像仓库（owner/repo），为空表示没有镜像。 */
    val giteeRepo: String = "jingoujiao/ccsut-schedule",
)

@Serializable
data class AppStateData(
    val schedule: ScheduleData = ScheduleData(),
    val settings: AppSettings = AppSettings(),
)

/**
 * 更新包下载源。
 *
 * GitHub 在国内直连常常很慢，所以：
 *  - Gitee（码云）国内直连通常快得多，优先用它；
 *  - 也可以用公共加速前缀下载（只是把原始地址拼在加速站后面）；
 *  - 还可以自己填一个前缀（例如自建反代或学校镜像）。
 */
object UpdateSource {
    const val AUTO = "auto"
    const val GITEE = "gitee"
    const val GITHUB = "github"
    const val MIRROR = "mirror"
    const val CUSTOM = "custom"

    val all: List<String> = listOf(AUTO, GITEE, GITHUB, MIRROR, CUSTOM)

    /** 内置的公共加速前缀，可能随时失效；失效时会自动换下一个。 */
    val mirrors: List<Pair<String, String>> = listOf(
        "ghfast.top" to "https://ghfast.top/",
        "gh-proxy.com" to "https://gh-proxy.com/",
        "ghproxy.net" to "https://ghproxy.net/",
        "gh.llkk.cc" to "https://gh.llkk.cc/",
    )

    fun label(source: String): String = when (source) {
        GITEE -> "Gitee 优先"
        GITHUB -> "只用 GitHub"
        MIRROR -> "GitHub 镜像加速"
        CUSTOM -> "自定义加速"
        else -> "自动（Gitee 优先 + 测速）"
    }
}

object ThemeMode {    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    val all: List<String> = listOf(SYSTEM, LIGHT, DARK)

    fun label(mode: String): String = when (mode) {
        LIGHT -> "浅色"
        DARK -> "深色"
        else -> "跟随系统"
    }
}

/** 默认作息：上午 4 节按本校时间，其余为通用时间，用户可在设置里逐节修改。 */
fun defaultPeriodTimes(): List<PeriodTime> = listOf(
    PeriodTime(1, "08:20", "09:05"),
    PeriodTime(2, "09:15", "10:00"),
    PeriodTime(3, "10:20", "11:05"),
    PeriodTime(4, "11:15", "12:00"),
    PeriodTime(5, "14:00", "14:45"),
    PeriodTime(6, "14:55", "15:40"),
    PeriodTime(7, "16:00", "16:45"),
    PeriodTime(8, "16:55", "17:40"),
    PeriodTime(9, "19:00", "19:45"),
    PeriodTime(10, "19:55", "20:40"),
)

/** 旧版默认作息。升级时若用户没改过时间，就自动换成新默认（改了的不动）。 */
val LEGACY_DEFAULT_PERIOD_TIMES: List<PeriodTime> = listOf(
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

/**
 * 作息分段：在第 N 节之后画一条跨整行的分界线，把上午 / 下午 / 晚上区分开。
 */
val SECTION_BREAKS: List<Int> = listOf(4, 8)

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

/**
 * 课程卡片的两种外观方案：
 *  - [COLORED]：现在这样——毛玻璃 + 每门课自己的颜色（同色系课程更好认）；
 *  - [GLASS]：课程本体和底部按钮一样，是统一的透明磨砂玻璃，不带各自的颜色。
 */
object CourseCardStyle {
    const val COLORED = "colored"
    const val GLASS = "glass"

    val all: List<String> = listOf(COLORED, GLASS)

    fun label(style: String): String = when (style) {
        GLASS -> "统一玻璃"
        else -> "课程配色"
    }

    fun usesCourseColor(style: String): Boolean = style != GLASS
}

/** 课表字号倍率范围：小于 1 更紧凑，大于 1 更醒目。 */
val SCHEDULE_FONT_SCALE_RANGE = 0.8f..1.6f

/** 课表文字不透明度范围（能见度）。 */
val SCHEDULE_FONT_ALPHA_RANGE = 0.4f..1f

/** 课程卡片配色数量，配色由 [com.jingoujiao.ccsutschedule.ui.theme.courseColor] 按 key 推导。 */
const val COURSE_COLOR_COUNT = 12

/**
 * 玻璃模糊度（磨砂强度）默认值：偏「厚」一点，接近参考视频里的磨砂白。
 *
 * 0 = 很透、课程色明显；1 = 很白很厚、课程色被冲淡。
 */
const val DEFAULT_GLASS_FROST: Float = 0.9f
