package com.jingoujiao.ccsutschedule.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.UpdateChecker
import com.jingoujiao.ccsutschedule.ui.CardSurface
import com.jingoujiao.ccsutschedule.ui.ConfirmDialog
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.LabeledTextField
import com.jingoujiao.ccsutschedule.ui.PrimaryButton
import com.jingoujiao.ccsutschedule.ui.SettingRow
import com.jingoujiao.ccsutschedule.ui.SwitchRow
import com.jingoujiao.ccsutschedule.ui.UpdateUi

@Composable
fun CourseSettingsPage(
    state: AppStateData,
    onBack: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onOpenImport: () -> Unit,
    onAddCourse: () -> Unit,
    onClearCourses: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    PageScaffold(
        title = "课程",
        subtitle = "共 ${state.schedule.courses.size} 个课程块",
        onBack = onBack,
    ) {
        CardSurface {
            LabeledTextField(
                label = "课表名称",
                value = state.schedule.title,
                onValueChange = onUpdateTitle,
                placeholder = "例如：2026-2027学年第1学期",
            )
            if (state.schedule.ownerLabel.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.schedule.ownerLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.schedule.importSource.isNotBlank()) {
                Text(
                    "来源：${state.schedule.importSource}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        CardSurface {
            SettingRow(
                title = "导入 xskb.xlsx 课表",
                subtitle = "支持教务处导出的课表文件",
                glyph = Glyph.Import,
                onClick = onOpenImport,
                trailing = { Chevron() },
            )
            SettingRow(
                title = "手动添加课程",
                glyph = Glyph.Plus,
                onClick = onAddCourse,
                trailing = { Chevron() },
            )
            SettingRow(
                title = "清空课表",
                subtitle = "只清课程，设置保留",
                glyph = Glyph.Delete,
                onClick = { showClearConfirm = true },
            )
        }
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
fun GeneralSettingsPage(
    versionName: String,
    state: AppStateData,
    updateUi: UpdateUi,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (UpdateChecker.Update) -> Unit,
) {
    val checking = updateUi is UpdateUi.Checking
    val statusText = when (updateUi) {
        is UpdateUi.Checking -> "正在检查…"
        is UpdateUi.UpToDate -> "已是最新版本"
        is UpdateUi.NoRelease -> "仓库还没有发布正式版本"
        is UpdateUi.Failed -> updateUi.message
        is UpdateUi.Found -> "发现新版本 ${updateUi.update.version}"
        is UpdateUi.Downloading -> "正在下载 ${(updateUi.progress * 100).toInt()}%"
        is UpdateUi.Ready -> "下载完成，等待安装"
        else -> "当前版本 $versionName"
    }

    PageScaffold(
        title = "常规",
        subtitle = "检查更新与其它",
        onBack = onBack,
    ) {
        CardSurface {
            SwitchRow(
                title = "自动检查更新",
                subtitle = "启动时在后台检查一次",
                checked = state.settings.autoCheckUpdates,
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(autoCheckUpdates = checked) }
                },
            )
            SettingRow(
                title = if (checking) "检查中…" else "检查更新",
                subtitle = statusText,
                glyph = Glyph.Import,
                onClick = { if (!checking) onCheckUpdate() },
                trailing = { Chevron() },
            )
            val update = (updateUi as? UpdateUi.Found)?.update
            if (update != null) {
                Spacer(Modifier.height(6.dp))
                PrimaryButton(
                    text = if (update.downloadUrl != null) "下载并安装 ${update.version}" else "打开下载页",
                    onClick = { onDownloadUpdate(update) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        CardSurface {
            SettingRow(title = "当前版本", subtitle = versionName, glyph = Glyph.Info)
        }
    }
}
