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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.CourseFactory
import com.jingoujiao.ccsutschedule.data.OverlapRules
import com.jingoujiao.ccsutschedule.data.ScheduleData
import com.jingoujiao.ccsutschedule.data.ScheduleRepository
import com.jingoujiao.ccsutschedule.data.UpdateChecker
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.data.XskbParser
import com.jingoujiao.ccsutschedule.ui.AppBackground
import com.jingoujiao.ccsutschedule.ui.ConfirmDialog
import com.jingoujiao.ccsutschedule.ui.CourseDetailSheet
import com.jingoujiao.ccsutschedule.ui.CourseEditorScreen
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.GlyphIcon
import com.jingoujiao.ccsutschedule.ui.ImportScreen
import com.jingoujiao.ccsutschedule.ui.TodayScreen
import com.jingoujiao.ccsutschedule.ui.UpdateDialog
import com.jingoujiao.ccsutschedule.ui.UpdateUi
import com.jingoujiao.ccsutschedule.ui.WeekScreen
import com.jingoujiao.ccsutschedule.ui.settings.SettingsPage
import com.jingoujiao.ccsutschedule.ui.settings.SettingsScreen
import com.jingoujiao.ccsutschedule.ui.theme.CcsutTheme
import com.jingoujiao.ccsutschedule.ui.theme.GlassSurface
import com.jingoujiao.ccsutschedule.ui.theme.glassColors
import com.jingoujiao.ccsutschedule.ui.theme.liquidGlass
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
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
    data object Today : Overlay
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
    val context = LocalContextCompat()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var selectedWeek by remember { mutableStateOf(-1) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    var updateUi by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var detailCourseId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var importUi by remember { mutableStateOf(ImportUi()) }
    var pendingSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val versionName = remember { UpdateChecker.currentVersionName(context) }

    val today = LocalDate.now()
    val firstMonday = WeekUtils.firstWeekMonday(state.settings.firstWeekMonday)
    val currentWeek = WeekUtils.weekOf(today, firstMonday)
    // 没设置开学日期时，优先停在「有课的最早一周」，避免导入后看到一片空白
    val firstWeekWithCourses = remember(state.schedule.courses) {
        state.schedule.courses.flatMap { it.weeks }.minOrNull() ?: 1
    }
    val week = when {
        selectedWeek > 0 -> selectedWeek
        currentWeek > 0 -> currentWeek
        else -> firstWeekWithCourses
    }

    fun toast(text: String) {
        message = text
    }

    fun checkUpdate(silent: Boolean) {
        scope.launch {
            updateUi = UpdateUi.Checking
            runCatching { UpdateChecker.checkLatest(versionName, state.settings.giteeRepo) }.fold(
                onSuccess = { update ->
                    updateUi = when {
                        update != null -> UpdateUi.Found(update)
                        silent -> UpdateUi.Idle
                        else -> UpdateUi.UpToDate
                    }
                },
                onFailure = { error ->
                    updateUi = when {
                        silent -> UpdateUi.Idle
                        error is UpdateChecker.NoReleasePublished -> UpdateUi.NoRelease
                        error is java.net.UnknownHostException ||
                            error is java.net.SocketTimeoutException ||
                            error is java.net.ConnectException -> UpdateUi.Failed("网络不可用，请稍后重试")
                        else -> UpdateUi.Failed("检查失败：${error.message ?: "网络不可用"}")
                    }
                },            )
        }
    }

    fun downloadUpdate(update: UpdateChecker.Update) {
        if (update.downloads.isEmpty()) {
            UpdateChecker.openUrl(context, update.pageUrl)
            updateUi = UpdateUi.Idle
            return
        }
        val sources = UpdateChecker.buildSources(
            downloads = update.downloads,
            source = state.settings.updateSource,
            customMirror = state.settings.customMirror,
        )
        scope.launch {
            updateUi = UpdateUi.Downloading(0f, probing = true)
            runCatching {
                UpdateChecker.downloadApk(
                    context = context,
                    sources = sources,
                    version = update.version,
                    onSourceLabel = { label -> updateUi = UpdateUi.Downloading(0f, sourceLabel = label) },
                    onProgress = { progress ->
                        val label = (updateUi as? UpdateUi.Downloading)?.sourceLabel.orEmpty()
                        updateUi = UpdateUi.Downloading(progress, sourceLabel = label, probing = false)
                    },
                )
            }.fold(
                onSuccess = { file -> updateUi = UpdateUi.Ready(file) },
                onFailure = { error -> updateUi = UpdateUi.Failed("下载失败：${error.message ?: "网络不可用"}") },
            )
        }
    }

    // 启动时自动检查一次（可在「设置 → 常规」关闭）
    LaunchedEffect(Unit) {
        if (state.settings.autoCheckUpdates) checkUpdate(silent = true)
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
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
                                onMoveCourse = { course, weekday, startPeriod ->
                                    val moved = course.copy(
                                        weekday = weekday,
                                        periods = (startPeriod until startPeriod + course.span).toList(),
                                    )
                                    val reason = OverlapRules.rejectReason(moved, state.schedule.courses)
                                    if (reason != null) {
                                        toast(reason)
                                    } else {
                                        repo.saveCourse(moved)
                                        toast("已移到${WeekUtils.weekdayLongLabel(weekday)}第 $startPeriod 节")
                                    }
                                },
                                onNotify = { text -> toast(text) },
                                onOpenToday = { overlay = Overlay.Today },
                                onOpenImport = { overlay = Overlay.Import },
                                onAddManual = {
                                    pendingSlot = null
                                    overlay = Overlay.Editor(null)
                                },
                                onClearCourses = { clearConfirm = true },
                            )

                            else -> SettingsScreen(
                                state = state,
                                page = settingsPage,
                                versionName = versionName,
                                updateUi = updateUi,
                                onNavigate = { settingsPage = it },
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
                                onCheckUpdate = { checkUpdate(silent = false) },
                                onDownloadUpdate = { update -> downloadUpdate(update) },
                                onOpenUrl = { url -> UpdateChecker.openUrl(context, url) },
                            )
                        }
                    }
                }

                // 悬浮胶囊导航：课程 / 设置
                PillNavigation(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 14.dp),
                )

                // 全屏覆盖页：导入 / 今日 / 课程编辑
                val current = overlay
                AnimatedVisibility(
                    visible = current != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // 半透明而不是实心：全屏页下面还能看到壁纸，和玻璃卡片是一套
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                            .windowInsetsPadding(WindowInsets.safeDrawing)
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

                            Overlay.Today -> TodayScreen(
                                state = state,
                                today = today,
                                onCourseClick = { detailCourseId = it.id },
                                onBack = { overlay = null },
                                onOpenSettings = {
                                    overlay = null
                                    tab = 1
                                },
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
                    ConfirmDialog(
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

                if (clearConfirm) {
                    ConfirmDialog(
                        title = "清空课表",
                        message = "会删除全部 ${state.schedule.courses.size} 个课程块，此操作不可撤销。设置、作息与背景会保留。",
                        confirmText = "清空",
                        onConfirm = {
                            repo.clearCourses()
                            clearConfirm = false
                            toast("课表已清空")
                        },
                        onDismiss = { clearConfirm = false },
                    )
                }

                // 更新提示
                UpdateDialog(
                    state = updateUi,
                    onDownload = { (updateUi as? UpdateUi.Found)?.update?.let { downloadUpdate(it) } },
                    onInstall = { (updateUi as? UpdateUi.Ready)?.let { UpdateChecker.installApk(context, it.file) } },
                    onOpenPage = {
                        (updateUi as? UpdateUi.Found)?.update?.let { UpdateChecker.openUrl(context, it.pageUrl) }
                        updateUi = UpdateUi.Idle
                    },
                    onCopyLink = {
                        (updateUi as? UpdateUi.Found)?.update?.downloadUrl?.let { url ->
                            UpdateChecker.copyToClipboard(context, "更新包地址", url)
                            toast("下载链接已复制")
                        }
                    },
                    onDismiss = { updateUi = UpdateUi.Idle },
                )

                // 轻提示
                AnimatedVisibility(
                    visible = message != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .padding(bottom = 100.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        shape = RoundedCornerShape(16.dp),
                        elevation = 10.dp,
                    ) {
                        Text(
                            message.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
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

    BackHandler(enabled = overlay != null || detailCourseId != null || tab != 0 || settingsPage != null) {
        when {
            overlay != null -> {
                overlay = null
                pendingSlot = null
            }

            detailCourseId != null -> detailCourseId = null
            settingsPage != null -> settingsPage = null
            else -> tab = 0
        }
    }
}

@Composable
private fun LocalContextCompat(): android.content.Context = androidx.compose.ui.platform.LocalContext.current

/**
 * 底部悬浮胶囊导航（课程 / 设置）。
 *
 * 选中块是一颗「水滴」：切换时从旧位置**滑**到新位置（弹簧），滑动过程中横向拉长、
 * 纵向压扁，到位后回弹——所以看起来像一滴水被拉过去再收回来。
 */
@Composable
private fun PillNavigation(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf("课程" to Glyph.Calendar, "设置" to Glyph.Settings)
    // 每一项的真实位置/大小（布局后测量），水滴就按它滑动
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    val target = bounds[selected]
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    val blobWidth = remember { Animatable(0f) }
    val blobHeight = remember { Animatable(0f) }

    LaunchedEffect(target) {
        val rect = target ?: return@LaunchedEffect
        if (blobWidth.value == 0f) {
            // 首次测量：直接就位，不要从左边滑出来
            offsetX.snapTo(rect.left)
            blobWidth.snapTo(rect.width)
            blobHeight.snapTo(rect.height)
            return@LaunchedEffect
        }
        blobHeight.animateTo(rect.height, spring(stiffness = Spring.StiffnessMedium))
        launch { blobWidth.animateTo(rect.width, spring(stiffness = Spring.StiffnessMedium)) }
        offsetX.animateTo(
            rect.left,
            spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
        )
    }

    // 离目标越远拉得越长（水滴被拉扯），到位后自然回弹
    val stretch = if (target != null && target.width > 0f) {
        ((abs(target.left - offsetX.value)) / target.width).coerceIn(0f, 1f) * 0.32f
    } else {
        0f
    }

    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        elevation = 12.dp,
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            val colors = glassColors()
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .size(
                        width = with(density) { blobWidth.value.toDp() },
                        height = with(density) { blobHeight.value.toDp() },
                    )
                    .graphicsLayer {
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.45f
                    }
                    .liquidGlass(
                        shape = RoundedCornerShape(22.dp),
                        colors = colors,
                        tint = MaterialTheme.colorScheme.primary,
                        tintAlpha = 0.92f,
                    )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { index, (label, glyph) ->
                    Box(
                        Modifier.onGloballyPositioned { coords ->
                            bounds[index] = coords.boundsInParent()
                        }
                    ) {
                        PillNavItem(label, glyph, selected == index) { onSelect(index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillNavItem(label: String, glyph: Glyph, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph = glyph, tint = color, size = 18.dp)
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = color,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

private fun conflictTextFor(course: Course, all: List<Course>): String? {
    val conflicts = OverlapRules.conflictsWith(course, all)
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
