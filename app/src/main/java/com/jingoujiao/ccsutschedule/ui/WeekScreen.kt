package com.jingoujiao.ccsutschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.PeriodTime
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.courseColor
import java.time.LocalDate
import java.time.LocalTime

private val PERIOD_COLUMN_WIDTH = 44.dp
private val CELL_HEIGHT = 50.dp

/**
 * 周课表：一周七天一屏显示（不横向滚动），纵向可滚动。
 * 布局与交互对齐 example 里的示例图：
 * 「第 N 周 + 大号日期」→「< 第 N 周 >」→「节次 | 周一~周日（含日期）」→ 课程网格。
 */
@Composable
fun WeekScreen(
    state: AppStateData,
    selectedWeek: Int,
    today: LocalDate,
    onSelectWeek: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    onAddCourse: (Int, Int) -> Unit,
    onOpenToday: () -> Unit,
    onOpenImport: () -> Unit,
    onAddManual: () -> Unit,
    onClearCourses: () -> Unit,
) {
    val settings = state.settings
    val courses = state.schedule.courses
    val week = selectedWeek.coerceAtLeast(1)
    val termStart = WeekUtils.parseIso(settings.termStartDate)
    val todayWeek = WeekUtils.weekOf(today, termStart)
    val monday = WeekUtils.mondayOfWeek(week, termStart)
    val isCurrentWeek = todayWeek > 0 && week == todayWeek

    val visibleCourses = remember(courses, week, settings.showOtherWeeks) {
        if (settings.showOtherWeeks) courses else courses.filter { it.activeInWeek(week) }
    }
    val maxPeriod = remember(settings.periods, courses) {
        val fromCourses = courses.maxOfOrNull { it.endPeriod } ?: 0
        maxOf(settings.periods.size, fromCourses).coerceIn(1, 14)
    }
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
    val currentPeriod = remember(settings.periods, isCurrentWeek, nowMinutes) {
        if (!isCurrentWeek) -1
        else settings.periods.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }?.index ?: -1
    }

    var menuVisible = remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopHeader(
            week = week,
            today = today,
            isCurrentWeek = isCurrentWeek,
            weekMonday = monday,
            onOpenToday = onOpenToday,
            onEdit = onAddManual,
            onMore = { menuVisible.value = true },
        )

        WeekSwitcher(
            week = week,
            totalWeeks = settings.totalWeeks,
            onSelectWeek = onSelectWeek,
        )

        if (courses.isEmpty()) {
            EmptyState(
                title = "还没有课表",
                subtitle = "点右上角的铅笔可以手动加课；\n也可以从「…」里导入教务处导出的 xskb.xlsx。",
                glyph = Glyph.Import,
            )
        } else {
            GridHeaderRow(
                monday = monday,
                today = today,
                highlightToday = isCurrentWeek,
            )
            GridBody(
                settings = settings,
                courses = visibleCourses,
                selectedWeek = week,
                maxPeriod = maxPeriod,
                todayWeekday = if (isCurrentWeek) today.dayOfWeek.value else -1,
                highlightPeriod = currentPeriod,
                onCourseClick = onCourseClick,
                onAddCourse = onAddCourse,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (menuVisible.value) {
        ActionSheet(
            visible = true,
            title = "课表操作",
            actions = listOf(
                "今日课程" to onOpenToday,
                "导入 xskb.xlsx 课表" to onOpenImport,
                "手动添加课程" to onAddManual,
                "清空课表" to onClearCourses,
            ),
            onDismiss = { menuVisible.value = false },
        )
    }
}

@Composable
private fun TopHeader(
    week: Int,
    today: LocalDate,
    isCurrentWeek: Boolean,
    weekMonday: LocalDate?,
    onOpenToday: () -> Unit,
    onEdit: () -> Unit,
    onMore: () -> Unit,
) {
    val bigText = when {
        isCurrentWeek -> "${today.monthValue}月${today.dayOfMonth}日 ${WeekUtils.weekdayLabel(today.dayOfWeek.value)}"
        weekMonday != null -> "${weekMonday.monthValue}月${weekMonday.dayOfMonth}日 ${WeekUtils.weekdayLabel(weekMonday.dayOfWeek.value)}"
        else -> "第 $week 周"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenToday)
        ) {
            Text(
                text = "第 $week 周",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = bigText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(Glyph.Edit, "添加课程", onEdit)
        Spacer(Modifier.width(10.dp))
        CircleIconButton(Glyph.More, "更多操作", onMore)
    }
}

/** 只保留「< 第 N 周 >」，周数只能靠左右箭头切换。 */
@Composable
private fun WeekSwitcher(
    week: Int,
    totalWeeks: Int,
    onSelectWeek: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            glyph = Glyph.ChevronLeft,
            contentDescription = "上一周",
            onClick = { if (week > 1) onSelectWeek(week - 1) },
            size = 38.dp,
            tint = if (week > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "第 $week 周",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        CircleIconButton(
            glyph = Glyph.ChevronRight,
            contentDescription = "下一周",
            onClick = { if (week < totalWeeks) onSelectWeek(week + 1) },
            size = 38.dp,
            tint = if (week < totalWeeks) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** 表头：节次 | 周一(日期) … 周日(日期)，今天整列高亮。 */
@Composable
private fun GridHeaderRow(
    monday: LocalDate?,
    today: LocalDate,
    highlightToday: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .width(PERIOD_COLUMN_WIDTH)
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "节次",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (weekday in 1..7) {
            val date = monday?.plusDays((weekday - 1).toLong())
            val isToday = highlightToday && date == today
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = WeekUtils.weekdayLabel(weekday),
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (date != null) {
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 10.sp,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface Slot {
    data class Block(val startPeriod: Int, val span: Int, val courses: List<Course>) : Slot
    data class Empty(val period: Int) : Slot
}

/** 把一列课程排成「从第几节开始、跨几节」的槽位；空格次给出可点击的占位。 */
private fun layoutColumn(courses: List<Course>, maxPeriod: Int): List<Slot> {
    val slots = mutableListOf<Slot>()
    val rendered = mutableSetOf<String>()
    var period = 1
    while (period <= maxPeriod) {
        val starting = courses.filter { it.startPeriod == period }
        if (starting.isNotEmpty()) {
            val span = starting.maxOf { it.span }.coerceAtMost(maxPeriod - period + 1)
            slots.add(Slot.Block(period, span, starting))
            starting.forEach { rendered.add(it.id) }
            period += span
        } else if (courses.any { period in it.periods }) {
            period += 1
        } else {
            slots.add(Slot.Empty(period))
            period += 1
        }
    }
    val leftovers = courses.filterNot { it.id in rendered }
    if (leftovers.isNotEmpty()) {
        slots.add(Slot.Block(maxPeriod, 1, leftovers))
    }
    return slots
}

@Composable
private fun GridBody(
    settings: AppSettings,
    courses: List<Course>,
    selectedWeek: Int,
    maxPeriod: Int,
    todayWeekday: Int,
    highlightPeriod: Int,
    onCourseClick: (Course) -> Unit,
    onAddCourse: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalDarkTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth()) {
        // 节次列
        Column(Modifier.width(PERIOD_COLUMN_WIDTH)) {
            for (period in 1..maxPeriod) {
                PeriodCell(
                    period = period,
                    time = settings.periods.firstOrNull { it.index == period },
                    highlighted = period == highlightPeriod,
                )
            }
        }
        // 周一 … 周日（各占等宽，一屏放下）
        for (weekday in 1..7) {
            val dayCourses = remember(courses, weekday, maxPeriod) {
                layoutColumn(courses.filter { it.weekday == weekday }, maxPeriod)
            }
            Column(modifier = Modifier.weight(1f)) {
                dayCourses.forEach { slot ->
                    when (slot) {
                        is Slot.Block -> {
                            val height = CELL_HEIGHT * slot.span - 2.dp
                            Row(
                                Modifier
                                    .height(height)
                                    .fillMaxWidth()
                                    .padding(1.dp)
                            ) {
                                slot.courses.forEach { course ->
                                    CourseCell(
                                        course = course,
                                        dark = dark,
                                        dimmed = settings.showOtherWeeks && !course.activeInWeek(selectedWeek),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize(),
                                        onClick = { onCourseClick(course) },
                                    )
                                }
                            }
                        }

                        is Slot.Empty -> {
                            Box(
                                Modifier
                                    .height(CELL_HEIGHT)
                                    .fillMaxWidth()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAddCourse(weekday, slot.period) }
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.width(2.dp).height(CELL_HEIGHT * maxPeriod))
        }
        Spacer(Modifier.height(92.dp))
    }
}

@Composable
private fun PeriodCell(period: Int, time: PeriodTime?, highlighted: Boolean) {
    Column(
        modifier = Modifier
            .width(PERIOD_COLUMN_WIDTH)
            .height(CELL_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent)
                .padding(horizontal = 7.dp, vertical = 1.dp),
        ) {
            Text(
                text = "$period",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (time != null) {
            Text(
                text = time.start,
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Text(
                text = time.end,
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/** 一个课程格子。列宽约 45dp，所以字小、行数按跨越的节次决定。 */
@Composable
private fun CourseCell(
    course: Course,
    dark: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = courseColor(course.colorKey, dark)
    Column(
        modifier = modifier
            .alpha(if (dimmed) 0.4f else 1f)
            .clip(RoundedCornerShape(9.dp))
            .background(accent.copy(alpha = if (dark) 0.26f else 0.17f))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = course.name,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (course.span >= 3) 6 else if (course.span == 2) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (course.location.isNotBlank() && course.span >= 2) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = course.location,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
