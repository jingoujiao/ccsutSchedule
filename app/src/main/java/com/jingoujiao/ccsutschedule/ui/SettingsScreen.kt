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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.PalettePresets
import com.jingoujiao.ccsutschedule.data.PeriodTime
import com.jingoujiao.ccsutschedule.data.ThemeMode
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.buildColorScheme
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun SettingsScreen(
    state: AppStateData,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onUpdateTitle: (String) -> Unit,
    onOpenImport: () -> Unit,
    onAddCourse: () -> Unit,
    onClearCourses: () -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
) {
    val settings = state.settings
    var showDatePicker by remember { mutableStateOf(false) }
    var showPeriodEditor by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val storedFirstDay = WeekUtils.parseIso(settings.firstWeekMonday)
    val termStart = WeekUtils.firstWeekMonday(settings.firstWeekMonday)
    val misaligned = storedFirstDay != null && termStart != null && storedFirstDay != termStart

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "设置",
            subtitle = "作息、学期与外观都在这里",
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("课表信息")
            CardSurface {
                LabeledTextField(
                    label = "课表名称",
                    value = state.schedule.title,
                    onValueChange = onUpdateTitle,
                    placeholder = "例如：2026-2027学年第1学期",
                )
                Spacer(Modifier.height(12.dp))
                SettingRow(
                    title = "课表第 1 周的周一",
                    subtitle = if (termStart == null) {
                        "未设置 · 日期会对不上"
                    } else {
                        "${WeekUtils.formatFull(termStart)} 星期一 · 第 1 周 " +
                            (WeekUtils.weekRangeLabel(1, termStart) ?: "")
                    },
                    glyph = Glyph.Today,
                    onClick = { showDatePicker = true },
                    trailing = {
                        GlyphIcon(Glyph.ChevronRight, MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
                    },
                )
                if (misaligned && storedFirstDay != null && termStart != null) {
                    SettingRow(
                        title = "存的 ${WeekUtils.formatMonthDay(storedFirstDay)} 是" +
                            WeekUtils.weekdayLongLabel(storedFirstDay.dayOfWeek.value) + "，已按周一算",
                        subtitle = "点这里对齐到 ${WeekUtils.formatMonthDay(termStart)}",
                        glyph = Glyph.Warning,
                        onClick = { onUpdateSettings { it.copy(firstWeekMonday = termStart.toString()) } },
                    )
                }
                StepperRow(
                    title = "学期总周数",
                    value = "${settings.totalWeeks} 周",
                    onMinus = { onUpdateSettings { it.copy(totalWeeks = (it.totalWeeks - 1).coerceAtLeast(1)) } },
                    onPlus = { onUpdateSettings { it.copy(totalWeeks = (it.totalWeeks + 1).coerceAtMost(40)) } },
                )
                if (termStart != null) {
                    SettingRow(
                        title = WeekUtils.teachingStatus(LocalDate.now(), termStart, settings.totalWeeks),
                        glyph = Glyph.Info,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("上课时间")
            CardSurface {
                SettingRow(
                    title = "作息时间表",
                    subtitle = "${settings.periods.size} 节课 · ${settings.periods.firstOrNull()?.start ?: "--:--"} 开始",
                    glyph = Glyph.Clock,
                    onClick = { showPeriodEditor = true },
                    trailing = { GlyphIcon(Glyph.ChevronRight, MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp) },
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("外观")
            CardSurface {
                SettingRow(title = "主题模式", glyph = Glyph.Info)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.all.forEach { mode ->
                        PillChip(
                            text = ThemeMode.label(mode),
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CardSurface {
                SettingRow(title = "配色方案", subtitle = PalettePresets.of(settings.paletteHue).name, glyph = Glyph.Palette)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val dark = LocalDarkTheme.current
                    PalettePresets.all.forEach { preset ->
                        val color = buildColorScheme(preset.hue, dark).primary
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (settings.paletteHue == preset.hue) 2.5.dp else 0.dp,
                                        color = if (settings.paletteHue == preset.hue) MaterialTheme.colorScheme.onSurface else color,
                                        shape = CircleShape,
                                    )
                                    .clickable { onUpdateSettings { it.copy(paletteHue = preset.hue) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (settings.paletteHue == preset.hue) {
                                    GlyphIcon(Glyph.Check, Color.White, size = 16.dp)
                                }
                            }
                            Text(
                                preset.name,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            CardSurface {
                SettingRow(
                    title = "自定义背景",
                    subtitle = if (settings.backgroundImagePath.isBlank()) "未设置" else "已设置，可调透明度",
                    glyph = Glyph.Image,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("选择图片", onPickBackground, glyph = Glyph.Image)
                    if (settings.backgroundImagePath.isNotBlank()) {
                        SecondaryButton("清除", onClearBackground, glyph = Glyph.Close)
                    }
                }
                if (settings.backgroundImagePath.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("背景不透明度 ${(settings.backgroundAlpha * 100).toInt()}%", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = settings.backgroundAlpha,
                        onValueChange = { value -> onUpdateSettings { it.copy(backgroundAlpha = value) } },
                        valueRange = 0.05f..0.75f,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            CardSurface {
                SwitchRow(
                    title = "在课表里显示非本周课程",
                    subtitle = "打开后，非当前周的课程会以淡色显示，方便看整学期安排",
                    checked = settings.showOtherWeeks,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(showOtherWeeks = checked) } },
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("数据")
            CardSurface {
                SettingRow(
                    title = "导入 xskb.xlsx 课表",
                    subtitle = "支持教务处导出的「上课啦」课表文件",
                    glyph = Glyph.Import,
                    onClick = onOpenImport,
                    trailing = { GlyphIcon(Glyph.ChevronRight, MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp) },
                )
                SettingRow(
                    title = "手动添加课程",
                    subtitle = "当前共 ${state.schedule.courses.size} 门课块",
                    glyph = Glyph.Plus,
                    onClick = onAddCourse,
                    trailing = { GlyphIcon(Glyph.ChevronRight, MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp) },
                )
                SettingRow(
                    title = "清空课表",
                    subtitle = "只清课程，设置保留",
                    glyph = Glyph.Delete,
                    onClick = { showClearConfirm = true },
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("关于")
            CardSurface {
                SettingRow(title = "长工课程表", subtitle = "版本 1.0.0 · 本地离线，不联网", glyph = Glyph.Info)
                Text(
                    "把教务处导出的 xskb.xlsx 选进来即可。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(110.dp))
        }
    }

    if (showDatePicker) {
        SimpleDatePicker(
            initial = termStart ?: WeekUtils.mondayOf(LocalDate.now()),
            onConfirm = { date ->
                onUpdateSettings { it.copy(firstWeekMonday = date.toString()) }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
            totalWeeks = settings.totalWeeks,
        )
    }

    if (showPeriodEditor) {
        PeriodEditorDialog(
            periods = settings.periods,
            onConfirm = { list -> onUpdateSettings { it.copy(periods = list) } },
            onDismiss = { showPeriodEditor = false },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空课表",
            message = "会删除全部 ${state.schedule.courses.size} 个课程块，不可撤销。",
            confirmText = "清空",
            onConfirm = {
                onClearCourses()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun SimpleDatePicker(
    initial: LocalDate,
    totalWeeks: Int,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.monthValue) }
    var day by remember { mutableStateOf(initial.dayOfMonth) }
    val maxDay = YearMonth.of(year, month).lengthOfMonth()
    val safeDay = day.coerceIn(1, maxDay)
    val picked = LocalDate.of(year, month, safeDay)
    // 一律对齐到所选日期所在周的周一，避免用户选到周中导致整学期偏移
    val firstMonday = WeekUtils.mondayOf(picked)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课表第 1 周的周一") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "选正式上课第 1 周的周一，不是开学日。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                StepperRow("年", "$year", { year -= 1 }, { year += 1 })
                StepperRow("月", "$month", { month = if (month == 1) 12 else month - 1 }, { month = if (month == 12) 1 else month + 1 })
                StepperRow("日", "$safeDay", { day = (safeDay - 1).coerceAtLeast(1) }, { day = (safeDay + 1).coerceAtMost(maxDay) })

                Spacer(Modifier.height(10.dp))
                Text(
                    if (picked == firstMonday) {
                        "第 1 周周一 ＝ ${WeekUtils.formatFull(firstMonday)}"
                    } else {
                        "${WeekUtils.formatMonthDay(picked)} 是${WeekUtils.weekdayLongLabel(picked.dayOfWeek.value)}，" +
                            "已自动对齐到该周周一：${WeekUtils.formatFull(firstMonday)}"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(10.dp))
                SectionLabel("换算预览")
                listOf(1, 2, 3).forEach { week ->
                    PreviewWeekRow(week, firstMonday)
                }
                if (totalWeeks > 3) {
                    PreviewWeekRow(totalWeeks, firstMonday, prefix = "… 最后一周")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    WeekUtils.teachingStatus(LocalDate.now(), firstMonday, totalWeeks),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                )

                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "用本周一（${WeekUtils.mondayOf(LocalDate.now())}）",
                    onClick = {
                        val monday = WeekUtils.mondayOf(LocalDate.now())
                        year = monday.year
                        month = monday.monthValue
                        day = monday.dayOfMonth
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(firstMonday) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PreviewWeekRow(week: Int, firstMonday: LocalDate, prefix: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            prefix ?: "第 $week 周",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(128.dp),
        )
        Text(
            WeekUtils.weekRangeLabel(week, firstMonday).orEmpty(),
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PeriodEditorDialog(
    periods: List<PeriodTime>,
    onConfirm: (List<PeriodTime>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(periods) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("作息时间表") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "按 24 小时制填写每节课的开始与结束时间，例如 08:00 / 08:45。",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                draft.forEachIndexed { index, period ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "第 ${period.index} 节",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(66.dp),
                        )
                        TimeField(
                            value = period.start,
                            onValueChange = { value ->
                                draft = draft.mapIndexed { i, item ->
                                    if (i == index) item.copy(start = value) else item
                                }
                            },
                        )
                        Text("—", modifier = Modifier.padding(horizontal = 6.dp))
                        TimeField(
                            value = period.end,
                            onValueChange = { value ->
                                draft = draft.mapIndexed { i, item ->
                                    if (i == index) item.copy(end = value) else item
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("增加一节", {
                        draft = draft + PeriodTime(draft.size + 1, "19:00", "19:45")
                    })
                    if (draft.size > 1) {
                        SecondaryButton("删掉最后一节", { draft = draft.dropLast(1) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(draft) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TimeField(value: String, onValueChange: (String) -> Unit) {
    Box(
        Modifier
            .width(78.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == ':' }.take(5)) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
        )
    }
}
