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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.COURSE_COLOR_COUNT
import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.courseColor

@Composable
fun CourseDetailSheet(
    course: Course,
    settings: AppSettings,
    conflictText: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val accent = courseColor(course.colorKey, dark)
    CcsutSheet(visible = true, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                course.name.ifBlank { "未命名课程" },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(14.dp))
        DetailLine("时间", detailTime(course, settings))
        DetailLine("周次", WeekUtils.formatWeeks(course.weeks))
        if (course.location.isNotBlank()) DetailLine("教室", course.location)
        if (course.teacher.isNotBlank()) DetailLine("教师", course.teacher)
        if (course.note.isNotBlank()) DetailLine("备注", course.note)
        if (conflictText != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Warning, MaterialTheme.colorScheme.tertiary, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(conflictText, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("编辑", onEdit, modifier = Modifier.weight(1f), glyph = Glyph.Edit)
            SecondaryButton("删除", onDelete, modifier = Modifier.weight(1f), glyph = Glyph.Delete)
        }
    }
}

private fun detailTime(course: Course, settings: AppSettings): String {
    val start = settings.periods.firstOrNull { it.index == course.startPeriod }
    val end = settings.periods.firstOrNull { it.index == course.endPeriod }
    val time = if (start != null && end != null) "${start.start}-${end.end}" else ""
    return buildString {
        append(WeekUtils.weekdayLongLabel(course.weekday))
        append("  第")
        append(course.startPeriod)
        if (course.endPeriod != course.startPeriod) {
            append("-")
            append(course.endPeriod)
        }
        append("节")
        if (time.isNotBlank()) {
            append("  ")
            append(time)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Text(label, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CourseEditorScreen(
    original: Course?,
    allCourses: List<Course>,
    settings: AppSettings,
    isNew: Boolean,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    onBack: () -> Unit,
) {
    val dark = LocalDarkTheme.current
    val defaultWeeks = remember(settings.totalWeeks) { (1..settings.totalWeeks.coerceIn(1, 60)).toList() }
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var teacher by remember { mutableStateOf(original?.teacher.orEmpty()) }
    var location by remember { mutableStateOf(original?.location.orEmpty()) }
    var note by remember { mutableStateOf(original?.note.orEmpty()) }
    var weekday by remember { mutableStateOf(original?.weekday ?: 1) }
    var periods by remember { mutableStateOf(original?.periods?.toSet() ?: setOf(1)) }
    var weeks by remember { mutableStateOf(original?.weeks?.toSet() ?: defaultWeeks.toSet()) }
    var colorKey by remember { mutableStateOf(original?.colorKey ?: 0) }

    val canSave = name.isNotBlank() && periods.isNotEmpty()
    val conflicts = remember(weekday, periods, weeks, allCourses) {
        allCourses.filter { other ->
            other.id != original?.id &&
                other.weekday == weekday &&
                other.periods.any { it in periods } &&
                (other.weeks.isEmpty() || weeks.isEmpty() || other.weeks.any { it in weeks })
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (isNew) "添加课程" else "编辑课程",
            subtitle = if (isNew) "填好课名、星期与节次即可" else "修改后会立即同步到课表",
            leading = { IconAction(Glyph.Back, "返回", onBack) },
            actions = {
                PrimaryButton(
                    text = "保存",
                    onClick = {
                        val sortedPeriods = periods.sorted()
                        onSave(
                            Course(
                                id = original?.id ?: com.jingoujiao.ccsutschedule.data.CourseFactory.newId(),
                                name = name.trim(),
                                teacher = teacher.trim(),
                                location = location.trim(),
                                weekday = weekday,
                                periods = sortedPeriods,
                                weeks = weeks.sorted(),
                                colorKey = colorKey,
                                note = note.trim(),
                            )
                        )
                    },
                    enabled = canSave,
                )
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CardSurface {
                LabeledTextField("课程名称", name, { name = it }, placeholder = "例如：高等数学2（上）")
                Spacer(Modifier.height(12.dp))
                LabeledTextField("任课教师", teacher, { teacher = it }, placeholder = "选填")
                Spacer(Modifier.height(12.dp))
                LabeledTextField("上课教室", location, { location = it }, placeholder = "例如：7-南203")
                Spacer(Modifier.height(12.dp))
                LabeledTextField("备注", note, { note = it }, placeholder = "选填")
            }

            CardSurface {
                SectionLabel("星期")
                ChipWrap(
                    items = (1..7).map { WeekUtils.weekdayLongLabel(it) },
                    selected = { weekday == it + 1 },
                    onClick = { weekday = it + 1 },
                    perRow = 4,
                )
            }

            CardSurface {
                SectionLabel("节次（可多选，相邻节次会显示为一个卡片）")
                ChipWrap(
                    items = (1..settings.periods.size.coerceAtLeast(1)).map { "$it" },
                    selected = { periods.contains(it + 1) },
                    onClick = { index ->
                        val period = index + 1
                        periods = if (periods.contains(period)) periods - period else periods + period
                    },
                    perRow = 8,
                )
                if (conflicts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Warning, MaterialTheme.colorScheme.tertiary, size = 15.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "与「${conflicts.joinToString("、") { it.name.ifBlank { "未命名" } }}」时间重叠",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            CardSurface {
                SectionLabel("周次")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("全部", { weeks = defaultWeeks.toSet() })
                    SecondaryButton("单周", { weeks = defaultWeeks.filter { it % 2 == 1 }.toSet() })
                    SecondaryButton("双周", { weeks = defaultWeeks.filter { it % 2 == 0 }.toSet() })
                    SecondaryButton("清空", { weeks = emptySet() })
                }
                Spacer(Modifier.height(10.dp))
                ChipWrap(
                    items = defaultWeeks.map { "$it" },
                    selected = { weeks.contains(it + 1) },
                    onClick = { index ->
                        val week = index + 1
                        weeks = if (weeks.contains(week)) weeks - week else weeks + week
                    },
                    perRow = 8,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前：${WeekUtils.formatWeeks(weeks.sorted())}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CardSurface {
                SectionLabel("课程颜色")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (index in 0 until COURSE_COLOR_COUNT) {
                        val color = courseColor(index, dark)
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (index == colorKey) 2.5.dp else 0.dp,
                                    color = if (index == colorKey) MaterialTheme.colorScheme.onSurface else color,
                                    shape = CircleShape,
                                )
                                .clickable { colorKey = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index == colorKey) {
                                GlyphIcon(Glyph.Check, androidx.compose.ui.graphics.Color.White, size = 15.dp)
                            }
                        }
                    }
                }
            }

            if (!isNew && original != null) {
                SecondaryButton(
                    text = "删除这门课",
                    onClick = { onDelete(original) },
                    modifier = Modifier.fillMaxWidth(),
                    glyph = Glyph.Delete,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 不用实验性 FlowRow：手动按每行个数切块。 */
@Composable
private fun ChipWrap(
    items: List<String>,
    selected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    perRow: Int,
) {
    var index = 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    val current = index
                    PillChip(
                        text = label,
                        selected = selected(current),
                        onClick = { onClick(current) },
                    )
                    index += 1
                }
            }
        }
    }
}
