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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.PeriodTime
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.courseColor
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(
    state: AppStateData,
    today: LocalDate,
    onCourseClick: (Course) -> Unit,
    onOpenWeek: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val settings = state.settings
    val termStart = WeekUtils.parseIso(settings.termStartDate)
    val week = WeekUtils.weekOf(today, termStart)
    val weekday = today.dayOfWeek.value
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }

    val todayCourses = state.schedule.courses
        .filter { it.weekday == weekday }
        .filter { week <= 0 || it.activeInWeek(week) }
        .sortedBy { it.startPeriod }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "今天",
            subtitle = buildString {
                append(WeekUtils.formatFull(today))
                append("  ")
                append(WeekUtils.weekdayLongLabel(weekday))
                if (week > 0) {
                    append("  ·  第 ")
                    append(week)
                    append(" 周")
                }
            },
            actions = { IconAction(Glyph.Calendar, "回到课表", onOpenWeek) },
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (termStart == null) {
                HintCard(
                    title = "还没有设置开学日期",
                    message = "在设置里填上第 1 周周一的日期，就能自动判断“今天第几周”，课表也会按周高亮。",
                    actionText = "去设置",
                    onAction = onOpenSettings,
                )
            }

            if (state.schedule.courses.isEmpty()) {
                EmptyState("今天没有课", "课表还是空的，先导入或手动添加课程吧。", Glyph.Calendar)
                return@Column
            }

            if (todayCourses.isEmpty()) {
                EmptyState(
                    title = "今天没有课",
                    subtitle = if (week > 0) "第 $week 周 ${WeekUtils.weekdayLongLabel(weekday)} 没有安排课程，好好休息。" else "今天没有安排课程。",
                    glyph = Glyph.Today,
                )
                return@Column
            }

            val current = todayCourses.firstOrNull { course ->
                val start = courseStart(course, settings.periods)
                val end = courseEnd(course, settings.periods)
                start >= 0 && nowMinutes in start until end
            }
            val next = todayCourses.firstOrNull { course ->
                courseStart(course, settings.periods) > nowMinutes
            }

            if (current != null) {
                SpotlightCard(
                    label = "正在上课",
                    course = current,
                    periods = settings.periods,
                    trailing = "还剩 ${WeekUtils.formatDuration(courseEnd(current, settings.periods) - nowMinutes)}",
                    onClick = { onCourseClick(current) },
                )
            } else if (next != null) {
                SpotlightCard(
                    label = "下一节课",
                    course = next,
                    periods = settings.periods,
                    trailing = "${WeekUtils.formatDuration(courseStart(next, settings.periods) - nowMinutes)}后开始",
                    onClick = { onCourseClick(next) },
                )
            } else {
                HintCard(
                    title = "今天的课都上完了",
                    message = "共 ${todayCourses.size} 节课，收工。",
                )
            }

            SectionLabel("今日课程（${todayCourses.size}）")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                todayCourses.forEach { course ->
                    TodayCourseRow(
                        course = course,
                        periods = settings.periods,
                        isPast = courseEnd(course, settings.periods) in 1 until nowMinutes,
                        isCurrent = current != null && current.id == course.id,
                        onClick = { onCourseClick(course) },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun courseStart(course: Course, periods: List<PeriodTime>): Int =
    periods.firstOrNull { it.index == course.startPeriod }?.startMinutes ?: -1

private fun courseEnd(course: Course, periods: List<PeriodTime>): Int =
    periods.firstOrNull { it.index == course.endPeriod }?.endMinutes ?: -1

private fun timeRange(course: Course, periods: List<PeriodTime>): String {
    val start = periods.firstOrNull { it.index == course.startPeriod }
    val end = periods.firstOrNull { it.index == course.endPeriod }
    return if (start != null && end != null) "${start.start} - ${end.end}" else "第${course.startPeriod}-${course.endPeriod}节"
}

@Composable
private fun SpotlightCard(
    label: String,
    course: Course,
    periods: List<PeriodTime>,
    trailing: String,
    onClick: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val accent = courseColor(course.colorKey, dark)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = if (dark) 0.30f else 0.20f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    trailing,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                course.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                timeRange(course, periods) + "  ·  第${course.startPeriod}-${course.endPeriod}节",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detail = listOfNotNull(
                course.location.takeIf { it.isNotBlank() },
                course.teacher.takeIf { it.isNotBlank() },
                WeekUtils.formatWeeks(course.weeks).takeIf { course.weeks.isNotEmpty() },
            ).joinToString("  ·  ")
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TodayCourseRow(
    course: Course,
    periods: List<PeriodTime>,
    isPast: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val accent = courseColor(course.colorKey, dark)
    val alpha = if (isPast) 0.45f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isCurrent) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                1.dp,
                if (isCurrent) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .width(58.dp)
                .then(Modifier),
        ) {
            Text(
                timeRange(course, periods).substringBefore(" - "),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                timeRange(course, periods).substringAfter(" - "),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = alpha))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                course.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 2,
            )
            val sub = listOfNotNull(
                course.location.takeIf { it.isNotBlank() },
                course.teacher.takeIf { it.isNotBlank() },
            ).joinToString("  ·  ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (isCurrent) {
            Text("进行中", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun HintCard(
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CardSurface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.Info, MaterialTheme.colorScheme.primary, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(message, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(actionText, onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}
