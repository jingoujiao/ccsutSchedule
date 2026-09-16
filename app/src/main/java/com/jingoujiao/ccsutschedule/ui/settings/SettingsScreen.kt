package com.jingoujiao.ccsutschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.UpdateChecker
import com.jingoujiao.ccsutschedule.ui.CardSurface
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.GlyphIcon
import com.jingoujiao.ccsutschedule.ui.ScreenHeader
import com.jingoujiao.ccsutschedule.ui.SettingRow
import com.jingoujiao.ccsutschedule.ui.UpdateUi

/** 设置里的子页面。 */
enum class SettingsPage(
    val title: String,
    val subtitle: String,
    val glyph: Glyph,
) {
    ABOUT("关于", "作者、仓库与反馈", Glyph.Info),
    COURSE("课程", "导入、添加、清空课表", Glyph.Calendar),
    APPEARANCE("外观", "主题、配色、背景", Glyph.Palette),
    GENERAL("常规", "检查更新与其它", Glyph.Settings),
    TIME("时间", "第 1 周周一、作息时间", Glyph.Clock),
}

@Composable
fun SettingsScreen(
    state: AppStateData,
    page: SettingsPage?,
    versionName: String,
    updateUi: UpdateUi,
    onNavigate: (SettingsPage?) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onUpdateTitle: (String) -> Unit,
    onOpenImport: () -> Unit,
    onAddCourse: () -> Unit,
    onClearCourses: () -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (UpdateChecker.Update) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    when (page) {
        null -> SettingsHome(
            state = state,
            versionName = versionName,
            onNavigate = onNavigate,
        )

        SettingsPage.ABOUT -> AboutPage(
            versionName = versionName,
            onBack = { onNavigate(null) },
            onOpenUrl = onOpenUrl,
        )

        SettingsPage.COURSE -> CourseSettingsPage(
            state = state,
            onBack = { onNavigate(null) },
            onUpdateTitle = onUpdateTitle,
            onOpenImport = onOpenImport,
            onAddCourse = onAddCourse,
            onClearCourses = onClearCourses,
        )

        SettingsPage.APPEARANCE -> AppearanceSettingsPage(
            state = state,
            onBack = { onNavigate(null) },
            onUpdateSettings = onUpdateSettings,
            onPickBackground = onPickBackground,
            onClearBackground = onClearBackground,
        )

        SettingsPage.TIME -> TimeSettingsPage(
            state = state,
            onBack = { onNavigate(null) },
            onUpdateSettings = onUpdateSettings,
        )

        SettingsPage.GENERAL -> GeneralSettingsPage(
            versionName = versionName,
            state = state,
            updateUi = updateUi,
            onBack = { onNavigate(null) },
            onUpdateSettings = onUpdateSettings,
            onCheckUpdate = onCheckUpdate,
            onDownloadUpdate = onDownloadUpdate,
        )
    }
}

@Composable
private fun SettingsHome(
    state: AppStateData,
    versionName: String,
    onNavigate: (SettingsPage) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "设置", subtitle = "版本 $versionName")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardSurface {
                SettingRow(
                    title = SettingsPage.ABOUT.title,
                    subtitle = SettingsPage.ABOUT.subtitle,
                    glyph = SettingsPage.ABOUT.glyph,
                    onClick = { onNavigate(SettingsPage.ABOUT) },
                    trailing = { Chevron() },
                )
            }

            CardSurface {
                SettingsPage.entries.filter { it != SettingsPage.ABOUT }.forEach { item ->
                    SettingRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        glyph = item.glyph,
                        onClick = { onNavigate(item) },
                        trailing = { Chevron() },
                    )
                }
            }

            CardSurface {
                val summary = buildString {
                    append(state.schedule.courses.size)
                    append(" 个课程块")
                    if (state.schedule.title.isNotBlank()) {
                        append(" · ")
                        append(state.schedule.title)
                    }
                }
                Text(
                    summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
internal fun Chevron() {
    GlyphIcon(Glyph.ChevronRight, MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
}

@Composable
internal fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
internal fun PageScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = title,
            subtitle = subtitle,
            leading = {
                com.jingoujiao.ccsutschedule.ui.IconAction(Glyph.Back, "返回设置", onBack)
            },
        )
        PageColumn { content() }
    }
}
