package com.jingoujiao.ccsutschedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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

private val PERIOD_COLUMN_WIDTH = 46.dp
private val DAY_WIDTH = 76.dp
private val CELL_HEIGHT = 62.dp
private val HEADER_HEIGHT = 30.dp

@Composable
fun WeekScreen(
    state: AppStateData,
    selectedWeek: Int,
    today: LocalDate,
    onSelectWeek: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    onAddCourse: (Int, Int) -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddManual: () -> Unit,
) {
    val settings = state.settings
    val courses = state.schedule.courses
    val week = selectedWeek.coerceAtLeast(1)
    val todayWeek = WeekUtils.weekOf(today, WeekUtils.parseIso(settings.termStartDate))
    val todayWeekday = today.dayOfWeek.value

    val visibleCourses = remember(courses, week, settings.showOtherWeeks) {
        if (settings.showOtherWeeks) courses else courses.filter { it.activeInWeek(week) }
    }
    val maxPeriod = remember(settings.periods, courses) {
        val fromCourses = courses.maxOfOrNull { it.endPeriod } ?: 0
        maxOf(settings.periods.size, fromCourses).coerceIn(1, 12)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = state.schedule.title.ifBlank { "我的课表" },
            subtitle = subtitleOf(state, today, todayWeek),
            actions = {
                IconAction(Glyph.Settings, "设置", onOpenSettings)
                IconAction(Glyph.Import, "导入课表", onOpenImport)
                IconAction(Glyph.Plus, "手动添加课程", onAddManual)
            },
        )

        WeekSelector(
            totalWeeks = settings.totalWeeks,
            selectedWeek = week,
            currentWeek = todayWeek,
            hasCourses = courses.isNotEmpty(),
            onSelectWeek = onSelectWeek,
        )

        if (courses.isEmpty()) {
            EmptyState(
                title = "还没有课表",
                subtitle = "点右上角的导入按钮，选择教务处导出的 xskb.xlsx；\n也可以点 “+” 手动添加课程。",
                glyph = Glyph.Import,
            )
            return@Column
        }

        WeekGrid(
            settings = settings,
            courses = visibleCourses,
            week = week,
            maxPeriod = maxPeriod,
            todayWeekday = if (week == todayWeek) todayWeekday else -1,
            onCourseClick = onCourseClick,
            onAddCourse = onAddCourse,
        )
    }
}

private fun subtitleOf(state: AppStateData, today: LocalDate, todayWeek: Int): String {
    val owner = state.schedule.ownerLabel
    val weekText = if (todayWeek > 0) {
        "今天是 ${WeekUtils.formatMonthDay(today)} ${WeekUtils.weekdayLongLabel(today.dayOfWeek.value)} · 第 $todayWeek 周"
    } else {
        "未设置开学日期，去设置里填上就能自动定位到当前周"
    }
    return listOf(owner, weekText).filter { it.isNotBlank() }.joinToString("  ·  ")
}

@Composable
private fun WeekSelector(
    totalWeeks: Int,
    selectedWeek: Int,
    currentWeek: Int,
    hasCourses: Boolean,
    onSelectWeek: (Int) -> Unit,
) {
    val weeks = (1..totalWeeks.coerceIn(1, 60)).toList()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedWeek - 3).coerceAtLeast(0))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Glyph.ChevronLeft, "上一周", onClick = {
            if (selectedWeek > 1) onSelectWeek(selectedWeek - 1)
        })
        LazyRow(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(weeks.size) { index ->
                val week = weeks[index]
                val isCurrent = week == currentWeek
                Box {
                    PillChip(
                        text = "第${week}周",
                        selected = week == selectedWeek,
                        onClick = { onSelectWeek(week) },
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    if (isCurrent) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 5.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                }
            }
        }
        IconAction(Glyph.ChevronRight, "下一周", onClick = {
            if (selectedWeek < totalWeeks) onSelectWeek(selectedWeek + 1)
        })
        if (currentWeek > 0 && selectedWeek != currentWeek) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectWeek(currentWeek) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    "回本周",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (!hasCourses) Spacer(Modifier.width(2.dp))
    }
}

@Composable
private fun WeekGrid(
    settings: AppSettings,
    courses: List<Course>,
    week: Int,
    maxPeriod: Int,
    todayWeekday: Int,
    onCourseClick: (Course) -> Unit,
    onAddCourse: (Int, Int) -> Unit,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val periods = settings.periods
    val dark = LocalDarkTheme.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 6.dp)
    ) {
        // 星期表头（横向跟随表体滚动，纵向固定）
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(PERIOD_COLUMN_WIDTH))
            Box(
                Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                Row(Modifier.offset { IntOffset(-horizontal.value, 0) }) {
                    for (weekday in 1..7) {
                        DayHeader(weekday, weekday == todayWeekday)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(vertical)
        ) {
            PeriodColumn(periods, maxPeriod)
            Box(
                Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                Row(Modifier.horizontalScroll(horizontal)) {
                    for (weekday in 1..7) {
                        DayColumn(
                            weekday = weekday,
                            courses = courses.filter { it.weekday == weekday },
                            week = week,
                            maxPeriod = maxPeriod,
                            isToday = weekday == todayWeekday,
                            dark = dark,
                            showOtherWeeks = settings.showOtherWeeks,
                            onCourseClick = onCourseClick,
                            onAddCourse = onAddCourse,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(weekday: Int, isToday: Boolean) {
    Column(
        modifier = Modifier.width(DAY_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(HEADER_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = WeekUtils.weekdayLabel(weekday),
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeriodColumn(periods: List<PeriodTime>, maxPeriod: Int) {
    Column(Modifier.width(PERIOD_COLUMN_WIDTH)) {
        for (period in 1..maxPeriod) {
            val time = periods.firstOrNull { it.index == period }
            Column(
                modifier = Modifier
                    .height(CELL_HEIGHT)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "$period",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (time != null) {
                    Text(time.start, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                    Text(time.end, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    weekday: Int,
    courses: List<Course>,
    week: Int,
    maxPeriod: Int,
    isToday: Boolean,
    dark: Boolean,
    showOtherWeeks: Boolean,
    onCourseClick: (Course) -> Unit,
    onAddCourse: (Int, Int) -> Unit,
) {
    val advanced = remember(courses, maxPeriod) { layoutColumn(courses, maxPeriod) }
    Column(
        Modifier
            .width(DAY_WIDTH)
            .background(
                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.035f) else Color.Transparent
            )
    ) {
        for (slot in advanced) {
            when (slot) {
                is Slot.Block -> {
                    val height = CELL_HEIGHT * slot.span - 4.dp
                    Column(
                        Modifier
                            .height(height)
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        slot.courses.forEach { course ->
                            CourseBlockCard(
                                course = course,
                                compact = slot.courses.size > 1 || slot.span == 1,
                                dark = dark,
                                dimmed = showOtherWeeks && !course.activeInWeek(week),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
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
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAddCourse(weekday, slot.period) }
                    )
                }
            }
        }
    }
}

private sealed interface Slot {
    data class Block(val startPeriod: Int, val span: Int, val courses: List<Course>) : Slot
    data class Empty(val period: Int) : Slot
}

/** 把一列课程排成「从第几节开始、跨几节」的槽位，空的节次给一个可点的空格。 */
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
        } else {
            val covering = courses.any { period in it.periods }
            if (covering) {
                period += 1
            } else {
                slots.add(Slot.Empty(period))
                period += 1
            }
        }
    }
    // 极端情况下的重叠课：兜底塞到末尾，保证不丢课
    val leftovers = courses.filterNot { it.id in rendered }
    if (leftovers.isNotEmpty()) {
        slots.add(Slot.Block(maxPeriod, 1, leftovers))
    }
    return slots
}

@Composable
private fun CourseBlockCard(
    course: Course,
    compact: Boolean,
    dark: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = courseColor(course.colorKey, dark)
    val background = accent.copy(alpha = if (dark) 0.24f else 0.16f)
    Row(
        modifier = modifier
            .alpha(if (dimmed) 0.42f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = course.name,
                fontSize = if (compact) 11.sp else 12.sp,
                lineHeight = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (compact) 4 else 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    text = course.location,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!compact && course.teacher.isNotBlank()) {
                Text(
                    text = course.teacher,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
