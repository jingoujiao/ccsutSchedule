package com.jingoujiao.ccsutschedule

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.CourseFactory
import com.jingoujiao.ccsutschedule.data.ScheduleData
import com.jingoujiao.ccsutschedule.data.ScheduleRepository
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.data.XskbParser
import com.jingoujiao.ccsutschedule.ui.AppBackground
import com.jingoujiao.ccsutschedule.ui.CourseDetailSheet
import com.jingoujiao.ccsutschedule.ui.CourseEditorScreen
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.GlyphIcon
import com.jingoujiao.ccsutschedule.ui.ImportScreen
import com.jingoujiao.ccsutschedule.ui.SettingsScreen
import com.jingoujiao.ccsutschedule.ui.TodayScreen
import com.jingoujiao.ccsutschedule.ui.WeekScreen
import com.jingoujiao.ccsutschedule.ui.theme.CcsutTheme
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as CcsutApp).repository
        setContent { AppRoot(repository) }
    }
}

private sealed interface Overlay {
    data object Import : Overlay
    data object Settings : Overlay
    data class Editor(val courseId: String?) : Overlay
}

private data class ImportUi(
    val fileName: String = "",
    val parsing: Boolean = false,
    val error: String? = null,
    val result: XskbParser.Result? = null,
)

@Composable
private fun AppRoot(repo: ScheduleRepository) {
    val state by repo.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var selectedWeek by remember { mutableStateOf(-1) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var detailCourseId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var importUi by remember { mutableStateOf(ImportUi()) }
    var pendingSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val today = LocalDate.now()
    val termStart = WeekUtils.parseIso(state.settings.termStartDate)
    val currentWeek = WeekUtils.weekOf(today, termStart)
    val week = if (selectedWeek <= 0) (if (currentWeek > 0) currentWeek else 1) else selectedWeek

    fun toast(text: String) {
        message = text
    }

    val xskbPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context.contentResolver, uri) ?: "xskb.xlsx"
        importUi = ImportUi(fileName = name, parsing = true)
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        XskbParser.parse(stream, name)
                    } ?: throw IllegalStateException("无法读取文件内容")
                }
            }
            importUi = outcome.fold(
                onSuccess = { result -> ImportUi(fileName = name, result = result) },
                onFailure = { error -> ImportUi(fileName = name, error = error.message ?: error.toString()) },
            )
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { copyToPrivateStorage(context, uri) }
            if (saved != null) {
                repo.updateSettings { it.copy(backgroundImagePath = saved) }
                toast("背景已更新")
            } else {
                toast("背景图片读取失败")
            }
        }
    }

    CcsutTheme(state.settings) {
        AppBackground(state.settings) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> WeekScreen(
                                state = state,
                                selectedWeek = week,
                                today = today,
                                onSelectWeek = { selectedWeek = it },
                                onCourseClick = { detailCourseId = it.id },
                                onAddCourse = { weekday, period ->
                                    pendingSlot = weekday to period
                                    overlay = Overlay.Editor(null)
                                },
                                onOpenImport = { overlay = Overlay.Import },
                                onOpenSettings = { overlay = Overlay.Settings },
                                onAddManual = {
                                    pendingSlot = null
                                    overlay = Overlay.Editor(null)
                                },
                            )

                            else -> TodayScreen(
                                state = state,
                                today = today,
                                onCourseClick = { detailCourseId = it.id },
                                onOpenWeek = { tab = 0 },
                                onOpenSettings = { overlay = Overlay.Settings },
                            )
                        }
                    }
                    BottomBar(
                        selected = tab,
                        onSelect = { tab = it },
                    )
                }

                // 全屏覆盖页：导入 / 设置 / 课程编辑
                val current = overlay
                AnimatedVisibility(
                    visible = current != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (current) {
                            Overlay.Import -> ImportScreen(
                                state = state,
                                fileName = importUi.fileName,
                                isParsing = importUi.parsing,
                                error = importUi.error,
                                result = importUi.result,
                                onPickFile = { xskbPicker.launch(arrayOf("*/*")) },
                                onConfirm = { replace ->
                                    val result = importUi.result
                                    if (result != null) {
                                        val label = "${importUi.fileName} · " +
                                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                                                .format(java.util.Date()) + " 导入"
                                        if (replace) {
                                            repo.replaceSchedule(
                                                ScheduleData(
                                                    title = result.title,
                                                    ownerLabel = result.ownerLabel,
                                                    importSource = label,
                                                    courses = result.courses,
                                                )
                                            )
                                        } else {
                                            repo.appendCourses(result.courses, label)
                                        }
                                        toast("已导入 ${result.courses.size} 个课程块")
                                    }
                                    importUi = ImportUi()
                                    overlay = null
                                },
                                onBack = {
                                    importUi = ImportUi()
                                    overlay = null
                                },
                            )

                            Overlay.Settings -> SettingsScreen(
                                state = state,
                                onUpdateSettings = { block -> repo.updateSettings(block) },
                                onUpdateTitle = { title -> repo.updateTitle(title) },
                                onOpenImport = { overlay = Overlay.Import },
                                onAddCourse = {
                                    pendingSlot = null
                                    overlay = Overlay.Editor(null)
                                },
                                onClearCourses = {
                                    repo.clearCourses()
                                    toast("课表已清空")
                                },
                                onPickBackground = { backgroundPicker.launch(arrayOf("image/*")) },
                                onClearBackground = {
                                    val old = state.settings.backgroundImagePath
                                    repo.updateSettings { it.copy(backgroundImagePath = "") }
                                    if (old.isNotBlank()) runCatching { File(old).delete() }
                                    toast("背景已清除")
                                },
                                onBack = { overlay = null },
                            )

                            is Overlay.Editor -> {
                                val editing = current.courseId?.let { id ->
                                    state.schedule.courses.firstOrNull { it.id == id }
                                }
                                val slot = pendingSlot
                                val draft = editing ?: CourseFactory.blank(
                                    courses = state.schedule.courses,
                                    weekday = slot?.first ?: 1,
                                    period = slot?.second ?: 1,
                                )
                                CourseEditorScreen(
                                    original = editing?.copy() ?: draft,
                                    allCourses = state.schedule.courses,
                                    settings = state.settings,
                                    isNew = editing == null,
                                    onSave = { course ->
                                        repo.saveCourse(course)
                                        toast(if (editing == null) "课程已添加" else "课程已更新")
                                        overlay = null
                                        pendingSlot = null
                                    },
                                    onDelete = { course ->
                                        repo.deleteCourse(course.id)
                                        toast("课程已删除")
                                        overlay = null
                                        pendingSlot = null
                                    },
                                    onBack = {
                                        overlay = null
                                        pendingSlot = null
                                    },
                                )
                            }

                            null -> Unit
                        }
                    }
                }

                // 课程详情弹层
                val detail = detailCourseId?.let { id -> state.schedule.courses.firstOrNull { it.id == id } }
                if (detail != null) {
                    CourseDetailSheet(
                        course = detail,
                        settings = state.settings,
                        conflictText = conflictTextFor(detail, state.schedule.courses),
                        onEdit = {
                            detailCourseId = null
                            overlay = Overlay.Editor(detail.id)
                        },
                        onDelete = { pendingDelete = detail },
                        onDismiss = { detailCourseId = null },
                    )
                }

                if (pendingDelete != null) {
                    val target = pendingDelete!!
                    com.jingoujiao.ccsutschedule.ui.ConfirmDialog(
                        title = "删除课程",
                        message = "确定删除「${target.name.ifBlank { "未命名课程" }}」？",
                        confirmText = "删除",
                        onConfirm = {
                            repo.deleteCourse(target.id)
                            pendingDelete = null
                            detailCourseId = null
                            toast("课程已删除")
                        },
                        onDismiss = { pendingDelete = null },
                    )
                }

                // 轻提示
                AnimatedVisibility(
                    visible = message != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Box(
                        Modifier
                            .padding(bottom = 92.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.inverseSurface)
                            .padding(horizontal = 18.dp, vertical = 11.dp)
                    ) {
                        Text(
                            message.orEmpty(),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 13.5.sp,
                        )
                    }
                }
                LaunchedEffect(message) {
                    if (message != null) {
                        delay(2200)
                        message = null
                    }
                }
            }
        }
    }

    BackHandler(enabled = overlay != null || detailCourseId != null || tab != 0) {
        when {
            overlay != null -> {
                overlay = null
                pendingSlot = null
            }

            detailCourseId != null -> detailCourseId = null
            else -> tab = 0
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        TabItem("课表", Glyph.Calendar, selected == 0) { onSelect(0) }
        Spacer(Modifier.width(12.dp))
        TabItem("今日", Glyph.Today, selected == 1) { onSelect(1) }
    }
}

@Composable
private fun TabItem(label: String, glyph: Glyph, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, color, size = 19.dp)
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 14.sp, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun conflictTextFor(course: Course, all: List<Course>): String? {
    val conflicts = all.filter { other ->
        other.id != course.id &&
            other.weekday == course.weekday &&
            other.periods.any { it in course.periods } &&
            (other.weeks.isEmpty() || course.weeks.isEmpty() || other.weeks.any { it in course.weeks })
    }
    if (conflicts.isEmpty()) return null
    return "与「${conflicts.joinToString("、") { it.name }}」时间重叠"
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull()

/** 把选中的背景图复制到 App 私有目录，避免用户之后删掉原图导致背景丢失。 */
private fun copyToPrivateStorage(context: android.content.Context, uri: Uri): String? = runCatching {
    val dir = File(context.filesDir, "background").apply { mkdirs() }
    val target = File(dir, "custom-bg.img")
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    target.absolutePath
}.getOrNull()
