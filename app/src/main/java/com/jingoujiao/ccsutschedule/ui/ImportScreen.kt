package com.jingoujiao.ccsutschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.data.XskbParser

@Composable
fun ImportScreen(
    state: AppStateData,
    fileName: String,
    isParsing: Boolean,
    error: String?,
    result: XskbParser.Result?,
    onPickFile: () -> Unit,
    onConfirm: (replace: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "导入课表",
            subtitle = "支持教务处导出的 xskb.xlsx",
            leading = { IconAction(Glyph.Back, "返回", onBack) },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CardSurface {
                SectionLabel("怎么拿到文件")
                Text(
                    "1. 用电脑登录教务处，进入「课表查询 / 上课啦」页面；\n" +
                        "2. 导出或下载课表，得到 xskb.xlsx；\n" +
                        "3. 把文件传到手机（微信/QQ/网盘均可），再从这里选进来。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = if (fileName.isBlank()) "选择 xskb.xlsx 文件" else "重新选择文件",
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth(),
                    glyph = Glyph.Import,
                )
                if (fileName.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Check, MaterialTheme.colorScheme.primary, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(fileName, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (isParsing) {
                CardSurface {
                    Text("正在解析文件…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (error != null) {
                CardSurface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Warning, MaterialTheme.colorScheme.error, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("解析失败：$error", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (result != null) {
                CardSurface {
                    SectionLabel("解析结果")
                    InfoLine("学期", result.title.ifBlank { "（文件里没写）" })
                    InfoLine("身份", result.ownerLabel.ifBlank { "（文件里没写）" })
                    InfoLine("课程块", "${result.courses.size} 个")
                    InfoLine(
                        "节次",
                        if (result.usedPeriods.isEmpty()) "—" else "第 ${result.usedPeriods.first()}-${result.usedPeriods.last()} 节",
                    )
                    InfoLine(
                        "周次",
                        if (result.usedWeeks.isEmpty()) "—" else "第 ${result.usedWeeks.first()}-${result.usedWeeks.last()} 周",
                    )
                    InfoLine("星期", result.courses.map { it.weekday }.distinct().sorted().joinToString("、") { WeekUtils.weekdayLabel(it) })
                    if (result.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        result.warnings.forEach { warning ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                GlyphIcon(Glyph.Warning, MaterialTheme.colorScheme.tertiary, size = 15.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(warning, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }

                CardSurface {
                    SectionLabel("课程清单")
                    result.courses
                        .sortedWith(compareBy({ it.weekday }, { it.startPeriod }))
                        .forEach { course ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    WeekUtils.weekdayLabel(course.weekday),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(34.dp),
                                )
                                Text(
                                    "${course.startPeriod}-${course.endPeriod}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(34.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(course.name, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                    val sub = listOfNotNull(
                                        course.location.takeIf { it.isNotBlank() },
                                        course.teacher.takeIf { it.isNotBlank() },
                                        WeekUtils.formatWeeks(course.weeks),
                                    ).joinToString("  ·  ")
                                    Text(sub, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                }

                if (state.settings.firstWeekMonday.isBlank()) {
                    HintCard(
                        title = "别忘了告诉 App 第 1 周是哪一天",
                        message = "导入只带来「第几周有课」，文件里没有任何日期。到设置里填上" +
                            "「课表第 1 周的周一」，日期才能和课程对上（注意别填成开学日或军训周）。",
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        text = if (state.schedule.courses.isEmpty()) "导入课表" else "覆盖当前课表",
                        onClick = { onConfirm(true) },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.schedule.courses.isNotEmpty()) {
                        SecondaryButton(
                            text = "追加",
                            onClick = { onConfirm(false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
