package com.jingoujiao.ccsutschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.PeriodTime
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.ui.CardSurface
import com.jingoujiao.ccsutschedule.ui.CcsutDialog
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.PrimaryButton
import com.jingoujiao.ccsutschedule.ui.SecondaryButton
import com.jingoujiao.ccsutschedule.ui.SectionLabel
import com.jingoujiao.ccsutschedule.ui.SettingRow
import com.jingoujiao.ccsutschedule.ui.StepperRow
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun TimeSettingsPage(
    state: AppStateData,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    val settings = state.settings
    val storedFirstDay = WeekUtils.parseIso(settings.firstWeekMonday)
    val firstMonday = WeekUtils.firstWeekMonday(settings.firstWeekMonday)
    val misaligned = storedFirstDay != null && firstMonday != null && storedFirstDay != firstMonday
    var showDatePicker by remember { mutableStateOf(false) }
    var showPeriodEditor by remember { mutableStateOf(false) }

    PageScaffold(
        title = "时间",
        subtitle = "第 1 周周一与作息",
        onBack = onBack,
    ) {
        CardSurface {
            SettingRow(
                title = "课表第 1 周的周一",
                subtitle = if (firstMonday == null) {
                    "未设置 · 日期会对不上"
                } else {
                    "${WeekUtils.formatFull(firstMonday)} 星期一 · 第 1 周 " +
                        (WeekUtils.weekRangeLabel(1, firstMonday) ?: "")
                },
                glyph = Glyph.Today,
                onClick = { showDatePicker = true },
                trailing = { Chevron() },
            )
            if (misaligned) {
                SettingRow(
                    title = "存的 ${WeekUtils.formatMonthDay(storedFirstDay)} 是" +
                        WeekUtils.weekdayLongLabel(storedFirstDay.dayOfWeek.value) + "，已按周一算",
                    subtitle = "点这里对齐到 ${WeekUtils.formatMonthDay(firstMonday)}",
                    glyph = Glyph.Warning,
                    onClick = { onUpdateSettings { it.copy(firstWeekMonday = firstMonday.toString()) } },
                )
            }
            StepperRow(
                title = "学期总周数",
                value = "${settings.totalWeeks} 周",
                onMinus = { onUpdateSettings { it.copy(totalWeeks = (it.totalWeeks - 1).coerceAtLeast(1)) } },
                onPlus = { onUpdateSettings { it.copy(totalWeeks = (it.totalWeeks + 1).coerceAtMost(40)) } },
            )
            if (firstMonday != null) {
                SettingRow(
                    title = WeekUtils.teachingStatus(LocalDate.now(), firstMonday, settings.totalWeeks),
                    glyph = Glyph.Info,
                )
            }
        }

        CardSurface {
            SettingRow(
                title = "作息时间表",
                subtitle = "${settings.periods.size} 节课 · ${settings.periods.firstOrNull()?.start ?: "--:--"} 开始",
                glyph = Glyph.Clock,
                onClick = { showPeriodEditor = true },
                trailing = { Chevron() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "上午 4 节按本校时间，其余可自行修改。",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }

    if (showDatePicker) {
        SimpleDatePicker(
            initial = firstMonday ?: WeekUtils.mondayOf(LocalDate.now()),
            totalWeeks = settings.totalWeeks,
            onConfirm = { date ->
                onUpdateSettings { it.copy(firstWeekMonday = date.toString()) }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showPeriodEditor) {
        PeriodEditorDialog(
            periods = settings.periods,
            onConfirm = { list -> onUpdateSettings { it.copy(periods = list) } },
            onDismiss = { showPeriodEditor = false },
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

    CcsutDialog(
        title = "课表第 1 周的周一",
        onDismiss = onDismiss,
        buttons = {
            SecondaryButton("取消", onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton("确定", { onConfirm(firstMonday) }, modifier = Modifier.weight(1f))
        },
    ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "选正式上课第 1 周的周一，不是开学日。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                StepperRow("年", "$year", { year -= 1 }, { year += 1 })
                StepperRow(
                    "月",
                    "$month",
                    { month = if (month == 1) 12 else month - 1 },
                    { month = if (month == 12) 1 else month + 1 },
                )
                StepperRow(
                    "日",
                    "$safeDay",
                    { day = (safeDay - 1).coerceAtLeast(1) },
                    { day = (safeDay + 1).coerceAtMost(maxDay) },
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    if (picked == firstMonday) {
                        "第 1 周周一 ＝ ${WeekUtils.formatFull(firstMonday)}"
                    } else {
                        "${WeekUtils.formatMonthDay(picked)} 是" +
                            WeekUtils.weekdayLongLabel(picked.dayOfWeek.value) +
                            "，已自动对齐到该周周一：${WeekUtils.formatFull(firstMonday)}"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(10.dp))
                SectionLabel("换算预览")
                listOf(1, 2, 3).forEach { week -> PreviewWeekRow(week, firstMonday) }
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
        }
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
    CcsutDialog(
        title = "作息时间表",
        onDismiss = onDismiss,
        buttons = {
            SecondaryButton("取消", onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton("保存", { onConfirm(draft) }, modifier = Modifier.weight(1f))
        },
    ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "按 24 小时制填写，例如 08:20 / 09:05。",
                    fontSize = 12.sp,
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
        }
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
