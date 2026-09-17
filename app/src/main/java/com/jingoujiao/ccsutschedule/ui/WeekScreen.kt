package com.jingoujiao.ccsutschedule.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.Course
import com.jingoujiao.ccsutschedule.data.MAX_OVERLAP
import com.jingoujiao.ccsutschedule.data.OverlapRules
import com.jingoujiao.ccsutschedule.data.PeriodTime
import com.jingoujiao.ccsutschedule.data.SECTION_BREAKS
import com.jingoujiao.ccsutschedule.data.WeekUtils
import com.jingoujiao.ccsutschedule.ui.theme.GlassSurface
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.courseColor
import com.jingoujiao.ccsutschedule.ui.theme.glassColors
import com.jingoujiao.ccsutschedule.ui.theme.liquidGlass
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val PERIOD_COLUMN_WIDTH = 44.dp

/** 表头（星期 + 日期）的高度；拖动时要用它把手指坐标换算回节次。 */
private val HEADER_HEIGHT = 52.dp

/** 拖动时手指离上下边缘这么近就开始自动滚动课表。 */
private val DRAG_EDGE_SCROLL_ZONE = 76.dp

/**
 * 周课表：一周七天一屏显示（不横向滚动），纵向可滚动。
 *
 * 交互（对齐 example 里的参考视频）：
 * - 左右滑动切换上下周：走 [HorizontalPager]，跟手、松手自动吸附，顶部标题同时滑动；
 * - 长按课程卡片可以拖到别的星期 / 节次，拖动时卡片浮起来跟着手指，落点有虚框提示；
 * - 同一时段最多 [MAX_OVERLAP] 门课，超了拖不过去、也存不下。
 */
@Composable
fun WeekScreen(
    state: AppStateData,
    selectedWeek: Int,
    today: LocalDate,
    onSelectWeek: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    onAddCourse: (Int, Int) -> Unit,
    onMoveCourse: (Course, Int, Int) -> Unit,
    onNotify: (String) -> Unit,
    onOpenToday: () -> Unit,
    onOpenImport: () -> Unit,
    onAddManual: () -> Unit,
    onClearCourses: () -> Unit,
) {
    val settings = state.settings
    val courses = state.schedule.courses
    val totalWeeks = settings.totalWeeks.coerceIn(1, 60)
    val week = selectedWeek.coerceIn(1, totalWeeks)
    val firstMonday = WeekUtils.firstWeekMonday(settings.firstWeekMonday)
    val todayWeek = WeekUtils.weekOf(today, firstMonday)
    val isCurrentWeek = todayWeek > 0 && week == todayWeek

    val maxPeriod = remember(settings.periods, courses) {
        val fromCourses = courses.maxOfOrNull { it.endPeriod } ?: 0
        maxOf(settings.periods.size, fromCourses).coerceIn(1, 14)
    }
    val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
    val currentPeriod = remember(settings.periods, isCurrentWeek, nowMinutes) {
        if (!isCurrentWeek) -1
        else settings.periods.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }?.index ?: -1
    }

    var menuVisible by remember { mutableStateOf(false) }
    // 拖动状态放在 State 里，只有浮层会读它——避免每帧重算整张课表
    val dragState = remember { mutableStateOf<CourseDrag?>(null) }
    val landingState = remember { mutableStateOf<Landing?>(null) }

    val pagerState = rememberPagerState(initialPage = week - 1, pageCount = { totalWeeks })
    // 每个周页各自记住纵向滚动位置（拖动时要用当前页的偏移量换算节次）
    val scrollStates = remember(totalWeeks) { mutableMapOf<Int, ScrollState>() }

    // 外部改周（箭头 / 周次选择器 / 「本周」）→ 动画滚到那一页
    LaunchedEffect(week) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != week - 1) {
            pagerState.animateScrollToPage(week - 1)
        }
    }
    // 手势滑动落定 → 通知外部，让顶部标题、周次胶囊一起更新。
    // 回调必须取最新引用，否则这个长期存活的协程会一直用第一次组合时的旧闭包，
    // 导致「往回滑」时标题不跟着回去。
    val selectWeek by rememberUpdatedState(onSelectWeek)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> selectWeek(page + 1) }
    }
    LaunchedEffect(landingState) {
        if (landingState.value != null) {
            delay(620)
            landingState.value = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopHeader(
            week = week,
            today = today,
            isCurrentWeek = isCurrentWeek,
            weekMonday = WeekUtils.mondayOfWeek(week, firstMonday),
            onOpenToday = onOpenToday,
            onEdit = onAddManual,
            onMore = { menuVisible = true },
        )

        WeekSwitcher(
            week = week,
            totalWeeks = totalWeeks,
            currentWeek = todayWeek,
            firstMonday = firstMonday,
            onSelectWeek = onSelectWeek,
        )

        // 日期对不上时只留一句必要的提醒
        if (firstMonday == null) {
            StatusLine("未设置「课表第 1 周的周一」，表头日期不准")
        } else if (todayWeek == 0) {
            StatusLine("还没到第 1 周（${WeekUtils.formatMonthDay(firstMonday)} 开始）")
        } else if (todayWeek > settings.totalWeeks) {
            StatusLine("第 ${settings.totalWeeks} 周已结束")
        }

        if (courses.isEmpty()) {
            EmptyState(
                title = "还没有课表",
                subtitle = "点右上角铅笔手动加课，\n或从「…」导入 xskb.xlsx",
                glyph = Glyph.Import,
            )
        } else {
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val density = LocalDensity.current
                val haptics = LocalHapticFeedback.current
                val periodColPx = with(density) { PERIOD_COLUMN_WIDTH.toPx() }
                val headerPx = with(density) { HEADER_HEIGHT.toPx() }
                val areaWidthPx = with(density) { maxWidth.toPx() }
                val areaHeightPx = with(density) { maxHeight.toPx() }
                // 节次行按可视高度均分，正好铺到底部
                val cellHeight = ((maxHeight - HEADER_HEIGHT) / maxPeriod).coerceIn(44.dp, 120.dp)
                val cellPx = with(density) { cellHeight.toPx() }
                val colPx = ((areaWidthPx - periodColPx) / 7f).coerceAtLeast(1f)

                fun weekdayAt(x: Float): Int = ((x - periodColPx) / colPx).toInt().coerceIn(0, 6) + 1

                fun periodAt(y: Float, scroll: Int): Int =
                    ((y - headerPx + scroll) / cellPx).toInt().coerceIn(0, maxPeriod - 1) + 1

                fun pageCourses(pageWeek: Int): List<Course> =
                    if (settings.showOtherWeeks) courses else courses.filter { it.activeInWeek(pageWeek) }

                // 只在这两个值真的变化时才让课表重组（拖动中每帧变化的是 x/y，不在这里读）
                val dragging by remember { derivedStateOf { dragState.value != null } }
                val draggingId by remember { derivedStateOf { dragState.value?.course?.id } }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !dragging,
                ) { page ->
                    val pageWeek = page + 1
                    val pageList = remember(courses, pageWeek, settings.showOtherWeeks) { pageCourses(pageWeek) }
                    val dimmed = remember(courses, pageWeek, settings.showOtherWeeks) {
                        if (settings.showOtherWeeks) {
                            courses.filterNot { it.activeInWeek(pageWeek) }.map { it.id }.toSet()
                        } else {
                            emptySet()
                        }
                    }
                    val scrollState = scrollStates.getOrPut(page) { ScrollState(0) }
                    // 手势必须挂在「滚动内容」这一层：Compose 的 Main 阶段是内层先拿到事件，
                    // 挂在外层的话第一次 MOVE 就被 verticalScroll 抢走，长按拖动会被取消。
                    val dragGesture = Modifier.pointerInput(pageWeek, cellPx, colPx, maxPeriod, pageList) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                val scroll = scrollState.value
                                val weekday = weekdayAt(local.x)
                                val period = (local.y / cellPx).toInt().coerceIn(0, maxPeriod - 1) + 1
                                val hit = pageList
                                    .filter { it.weekday == weekday && period in it.periods }
                                    .minByOrNull { it.span }
                                if (hit == null) {
                                    dragState.value = null
                                } else {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // 手势坐标是「滚动内容」坐标系，浮层画在外层，这里换算一次
                                    val x = local.x
                                    val y = local.y + headerPx - scroll
                                    val cardLeft = periodColPx + (weekday - 1) * colPx
                                    val cardTop = headerPx + (hit.startPeriod - 1) * cellPx - scroll
                                    landingState.value = null
                                    dragState.value = CourseDrag(
                                        course = hit,
                                        grabX = x - cardLeft,
                                        grabY = y - cardTop,
                                        x = x,
                                        y = y,
                                        targetWeekday = weekday,
                                        targetPeriod = hit.startPeriod,
                                        allowed = true,
                                    )
                                }
                            },
                            onDragEnd = {
                                val current = dragState.value
                                dragState.value = null
                                if (current != null) {
                                    val moved = current.targetWeekday != current.course.weekday ||
                                        current.targetPeriod != current.course.startPeriod
                                    when {
                                        !moved -> Unit
                                        !current.allowed ->
                                            onNotify("同一时段最多 $MAX_OVERLAP 门课，这里放不下")
                                        else -> {
                                            onMoveCourse(
                                                current.course,
                                                current.targetWeekday,
                                                current.targetPeriod,
                                            )
                                            landingState.value = Landing(
                                                current.targetWeekday,
                                                current.targetPeriod,
                                                current.course.span,
                                            )
                                        }
                                    }
                                }
                            },
                            onDragCancel = { dragState.value = null },
                            onDrag = { change, delta ->
                                change.consume()
                                val current = dragState.value ?: return@detectDragGesturesAfterLongPress
                                val scroll = scrollState.value
                                val x = current.x + delta.x
                                val y = current.y + delta.y
                                val maxStart = (maxPeriod - current.course.span + 1).coerceAtLeast(1)
                                val targetWeekday = weekdayAt(x)
                                val targetPeriod = periodAt(y, scroll).coerceIn(1, maxStart)
                                val moved = current.course.copy(
                                    weekday = targetWeekday,
                                    periods = (targetPeriod until targetPeriod + current.course.span).toList(),
                                )
                                dragState.value = current.copy(
                                    x = x,
                                    y = y,
                                    targetWeekday = targetWeekday,
                                    targetPeriod = targetPeriod,
                                    allowed = OverlapRules.rejectReason(moved, courses) == null,
                                )
                            },
                        )
                    }
                    Column(Modifier.fillMaxSize()) {
                        GridHeaderRow(
                            monday = WeekUtils.mondayOfWeek(pageWeek, firstMonday),
                            today = today,
                            highlightToday = pageWeek == todayWeek,
                        )
                        GridBody(
                            settings = settings,
                            courses = pageList,
                            dimmedIds = dimmed,
                            hiddenCourseId = draggingId,
                            maxPeriod = maxPeriod,
                            cellHeight = cellHeight,
                            highlightPeriod = if (pageWeek == todayWeek) currentPeriod else -1,
                            scrollState = scrollState,
                            dragModifier = dragGesture,
                            onCourseClick = onCourseClick,
                            onAddCourse = onAddCourse,
                        )
                    }
                }

                // 浮起的卡片 / 落点虚框 / 落地闪光：单独一层，只跟着手指重组
                DragLayer(
                    dragState = dragState,
                    landingState = landingState,
                    periodColPx = periodColPx,
                    headerPx = headerPx,
                    colPx = colPx,
                    cellHeight = cellHeight,
                    cellPx = cellPx,
                    maxPeriod = maxPeriod,
                    areaHeightPx = areaHeightPx,
                    scrollOffset = { scrollStates[pagerState.currentPage]?.value ?: 0 },
                    currentScrollState = { scrollStates[pagerState.currentPage] },
                )
            }
        }
    }

    if (menuVisible) {
        ActionSheet(
            visible = true,
            title = "课表操作",
            actions = listOf(
                "今日课程" to onOpenToday,
                "导入 xskb.xlsx 课表" to onOpenImport,
                "手动添加课程" to onAddManual,
                "清空课表" to onClearCourses,
            ),
            onDismiss = { menuVisible = false },
        )
    }
}

/** 长按拖动中的状态。 */
private data class CourseDrag(
    val course: Course,
    /** 手指相对卡片左上角的偏移，保证卡片不会「跳」到手指下面。 */
    val grabX: Float,
    val grabY: Float,
    val x: Float,
    val y: Float,
    val targetWeekday: Int,
    val targetPeriod: Int,
    val allowed: Boolean,
)

private data class Landing(val weekday: Int, val period: Int, val span: Int)

/**
 * 拖动浮层。**只在这里读 [dragState]**，这样拖动时每帧重组的只有这一层，
 * 课表网格本身一帧都不会重画。
 */
@Composable
private fun DragLayer(
    dragState: State<CourseDrag?>,
    landingState: State<Landing?>,
    periodColPx: Float,
    headerPx: Float,
    colPx: Float,
    cellHeight: Dp,
    cellPx: Float,
    maxPeriod: Int,
    areaHeightPx: Float,
    scrollOffset: () -> Int,
    currentScrollState: () -> ScrollState?,
) {
    val density = LocalDensity.current
    val dark = LocalDarkTheme.current
    val drag by dragState
    val landing by landingState
    val blockWidth = with(density) { (colPx - 4f).toDp() }

    // 拖到上下边缘时自动滚课表：只在「靠边状态」变化时重启协程，不是每帧
    val edgeStep by remember(areaHeightPx) {
        derivedStateOf {
            val y = dragState.value?.y ?: return@derivedStateOf 0f
            val zone = with(density) { DRAG_EDGE_SCROLL_ZONE.toPx() }
            val fromTop = y - headerPx
            val fromBottom = areaHeightPx - y
            when {
                fromTop < zone -> -(((zone - fromTop) / zone).coerceIn(0f, 1f))
                fromBottom < zone -> ((zone - fromBottom) / zone).coerceIn(0f, 1f)
                else -> 0f
            }
        }
    }
    LaunchedEffect(edgeStep) {
        if (edgeStep == 0f) return@LaunchedEffect
        val state = currentScrollState() ?: return@LaunchedEffect
        while (isActive) {
            state.scrollBy(edgeStep * 18f)
            delay(16)
        }
    }

    if (drag != null) {
        val current = drag!!
        val maxStart = (maxPeriod - current.course.span + 1).coerceAtLeast(1)
        val targetPeriod = current.targetPeriod.coerceIn(1, maxStart)
        val scroll = scrollOffset()
        val targetTop = headerPx + (targetPeriod - 1) * cellPx - scroll
        val targetLeft = periodColPx + (current.targetWeekday - 1) * colPx
        val accent = courseColor(current.course.colorKey, dark)
        val blockHeight = cellHeight * current.course.span - 4.dp

        // 落点虚框
        GlassSurface(
            modifier = Modifier
                .offset { IntOffset(targetLeft.roundToInt(), targetTop.roundToInt()) }
                .width(blockWidth)
                .height(blockHeight),
            shape = RoundedCornerShape(12.dp),
            tint = if (current.allowed) accent else MaterialTheme.colorScheme.error,
            tintAlpha = 0.26f,
            borderWidth = 1.5.dp,
        ) {}

        // 浮起的卡片
        GlassSurface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (current.x - current.grabX).roundToInt(),
                        (current.y - current.grabY).roundToInt(),
                    )
                }
                .scale(1.05f)
                .width(blockWidth)
                .height(blockHeight),
            shape = RoundedCornerShape(12.dp),
            tint = accent,
            tintAlpha = 0.72f,
            elevation = 12.dp,
        ) {
            CourseCellContent(course = current.course, modifier = Modifier.fillMaxSize())
        }
    } else if (landing != null) {
        val settled = landing!!
        val glow = remember(settled) { Animatable(0.85f) }
        LaunchedEffect(settled) { glow.animateTo(0f, tween(560)) }
        val top = headerPx + (settled.period - 1) * cellPx - scrollOffset()
        val left = periodColPx + (settled.weekday - 1) * colPx
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(blockWidth)
                .height(cellHeight * settled.span - 4.dp)
                .alpha(glow.value)
                .liquidGlass(
                    shape = RoundedCornerShape(12.dp),
                    colors = glassColors(),
                    tint = MaterialTheme.colorScheme.primary,
                    tintAlpha = 0.45f,
                )
        )
    }
}

@Composable
private fun StatusLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Info, MaterialTheme.colorScheme.tertiary, size = 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TopHeader(
    week: Int,
    today: LocalDate,
    isCurrentWeek: Boolean,
    weekMonday: LocalDate?,
    onOpenToday: () -> Unit,
    onEdit: () -> Unit,
    onMore: () -> Unit,
) {
    val bigText = when {
        isCurrentWeek -> "${today.monthValue}月${today.dayOfMonth}日 ${WeekUtils.weekdayLabel(today.dayOfWeek.value)}"
        weekMonday != null -> "${weekMonday.monthValue}月${weekMonday.dayOfMonth}日 ${WeekUtils.weekdayLabel(weekMonday.dayOfWeek.value)}"
        else -> "第 $week 周"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenToday)
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = if (isCurrentWeek) "本周 · 第 $week 周" else "第 $week 周",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = bigText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(Glyph.Edit, "添加课程", onEdit)
        Spacer(Modifier.width(10.dp))
        CircleIconButton(Glyph.More, "更多操作", onMore)
    }
}

/** 只保留「< 第 N 周 >」；点周数弹滚动选择器，右侧「本周」一键回到当前周。 */
@Composable
private fun WeekSwitcher(
    week: Int,
    totalWeeks: Int,
    currentWeek: Int,
    firstMonday: LocalDate?,
    onSelectWeek: (Int) -> Unit,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                glyph = Glyph.ChevronLeft,
                contentDescription = "上一周",
                onClick = { if (week > 1) onSelectWeek(week - 1) },
                size = 38.dp,
                tint = if (week > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
            )
            GlassSurface(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .clickable { pickerVisible = true },
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "第 $week 周",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    GlyphIcon(
                        Glyph.ChevronDown,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 15.dp,
                    )
                }
            }
            CircleIconButton(
                glyph = Glyph.ChevronRight,
                contentDescription = "下一周",
                onClick = { if (week < totalWeeks) onSelectWeek(week + 1) },
                size = 38.dp,
                tint = if (week < totalWeeks) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
            )
        }

        if (currentWeek > 0 && week != currentWeek) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                PillChip(
                    text = "本周",
                    selected = false,
                    onClick = { onSelectWeek(currentWeek) },
                    accent = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (pickerVisible) {
        WeekPickerSheet(
            totalWeeks = totalWeeks,
            selectedWeek = week,
            currentWeek = currentWeek,
            firstMonday = firstMonday,
            onSelect = {
                onSelectWeek(it)
                pickerVisible = false
            },
            onDismiss = { pickerVisible = false },
        )
    }
}

/** 点周数后弹出的滚动选择器。 */
@Composable
private fun WeekPickerSheet(
    totalWeeks: Int,
    selectedWeek: Int,
    currentWeek: Int,
    firstMonday: LocalDate?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val weeks = (1..totalWeeks.coerceIn(1, 60)).toList()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedWeek - 3).coerceAtLeast(0))
    CcsutSheet(visible = true, onDismiss = onDismiss) {
        Text("选择周次", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            items(weeks.size) { index ->
                val item = weeks[index]
                val isSelected = item == selectedWeek
                val isCurrent = item == currentWeek
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                        )
                        .clickable { onSelect(item) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "第 $item 周",
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(84.dp),
                    )
                    Text(
                        text = WeekUtils.weekRangeLabel(item, firstMonday).orEmpty(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCurrent) {
                        Text("本周", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** 表头：一条玻璃胶囊里放「节次 | 周一(日期) … 周日(日期)」，今天整列高亮。 */
@Composable
private fun GridHeaderRow(
    monday: LocalDate?,
    today: LocalDate,
    highlightToday: Boolean,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(HEADER_HEIGHT - 4.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(PERIOD_COLUMN_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "节次",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (weekday in 1..7) {
                val date = monday?.plusDays((weekday - 1).toLong())
                val isToday = highlightToday && date == today
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isToday) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            } else {
                                Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = WeekUtils.weekdayLabel(weekday),
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (date != null) {
                            Text(
                                text = "${date.monthValue}/${date.dayOfMonth}",
                                fontSize = 10.sp,
                                color = if (isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

private sealed interface Slot {
    data class Block(val startPeriod: Int, val span: Int, val courses: List<Course>) : Slot
    data class Empty(val period: Int) : Slot
}

/** 把一列课程排成「从第几节开始、跨几节」的槽位；空格次给出可点击的占位。 */
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
        } else if (courses.any { period in it.periods }) {
            period += 1
        } else {
            slots.add(Slot.Empty(period))
            period += 1
        }
    }
    val leftovers = courses.filterNot { it.id in rendered }
    if (leftovers.isNotEmpty()) {
        slots.add(Slot.Block(maxPeriod, 1, leftovers))
    }
    return slots
}

@Composable
private fun GridBody(
    settings: AppSettings,
    courses: List<Course>,
    dimmedIds: Set<String>,
    hiddenCourseId: String?,
    maxPeriod: Int,
    cellHeight: Dp,
    highlightPeriod: Int,
    scrollState: ScrollState,
    dragModifier: Modifier = Modifier,
    onCourseClick: (Course) -> Unit,
    onAddCourse: (Int, Int) -> Unit,
) {
    val dark = LocalDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        Box(Modifier.fillMaxWidth().then(dragModifier)) {
            Row(Modifier.fillMaxWidth()) {
                // 节次列：也做成一条玻璃条，压在照片上时数字与时间才读得清
                GlassSurface(
                    modifier = Modifier
                        .width(PERIOD_COLUMN_WIDTH)
                        .height(cellHeight * maxPeriod),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.width(PERIOD_COLUMN_WIDTH)) {
                        for (period in 1..maxPeriod) {
                            PeriodCell(
                                period = period,
                                time = settings.periods.firstOrNull { it.index == period },
                                highlighted = period == highlightPeriod,
                                cellHeight = cellHeight,
                            )
                        }
                    }
                }
                // 周一 … 周日（各占等宽，一屏放下）
                for (weekday in 1..7) {
                    val daySlots = remember(courses, weekday, maxPeriod) {
                        layoutColumn(courses.filter { it.weekday == weekday }, maxPeriod)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        daySlots.forEach { slot ->
                            when (slot) {
                                is Slot.Block -> {
                                    // 一格最多并排 3 门，多的折成 +N（正常情况下并存不下来）
                                    val shown = slot.courses.take(MAX_OVERLAP)
                                    val overflow = slot.courses.size - shown.size
                                    Row(
                                        Modifier
                                            .height(cellHeight * slot.span - 2.dp)
                                            .fillMaxWidth()
                                            .padding(1.dp)
                                    ) {
                                        shown.forEach { course ->
                                            CourseCell(
                                                course = course,
                                                dark = dark,
                                                dimmed = course.id in dimmedIds,
                                                hidden = course.id == hiddenCourseId,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxSize(),
                                                onClick = { onCourseClick(course) },
                                            )
                                        }
                                        if (overflow > 0) {
                                            OverflowCell(
                                                count = overflow,
                                                modifier = Modifier
                                                    .weight(0.55f)
                                                    .fillMaxSize(),
                                            )
                                        }
                                    }
                                }

                                is Slot.Empty -> {
                                    Box(
                                        Modifier
                                            .height(cellHeight)
                                            .fillMaxWidth()
                                            .padding(1.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onAddCourse(weekday, slot.period) }
                                    )
                                }
                            }
                        }
                    }
                }
                Box(Modifier.width(2.dp).height(cellHeight * maxPeriod))
            }
            // 上午 / 下午 / 晚上的分界线（只画线不占高度，保证各列节次仍对齐）
            SECTION_BREAKS.forEach { afterPeriod ->
                if (afterPeriod < maxPeriod) {
                    SectionDivider(offsetY = cellHeight * afterPeriod)
                }
            }
        }
        // 悬浮导航会压住最后几行，留一点可滚动空间让用户把课拉出来
        Spacer(Modifier.height(84.dp))
    }
}

/** 跨整行的一条细分界线，用来区分上午 / 下午 / 晚上。 */
@Composable
private fun SectionDivider(offsetY: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun PeriodCell(
    period: Int,
    time: PeriodTime?,
    highlighted: Boolean,
    cellHeight: Dp,
) {
    Column(
        modifier = Modifier
            .width(PERIOD_COLUMN_WIDTH)
            .height(cellHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent)
                .padding(horizontal = 7.dp, vertical = 1.dp),
        ) {
            Text(
                text = "$period",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (time != null) {
            Text(
                text = time.start,
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Text(
                text = time.end,
                fontSize = 8.5.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * 一个课程格子：毛玻璃卡片 + 课程色染色。
 *
 * 注意这里**不加外阴影**——一屏几十个格子每个都投影会明显掉帧，
 * 玻璃感由填充渐变 + 顶部高光 + 描边提供就够了。
 */
@Composable
private fun CourseCell(
    course: Course,
    dark: Boolean,
    dimmed: Boolean,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = courseColor(course.colorKey, dark)
    GlassSurface(
        modifier = modifier
            .alpha(if (hidden) 0.25f else if (dimmed) 0.45f else 1f)
            .padding(1.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        tint = accent,
        tintAlpha = if (dark) 0.34f else 0.30f,
    ) {
        CourseCellContent(course = course, modifier = Modifier.fillMaxSize())
    }
}

/** 课程卡片里的内容（网格里和拖动浮层共用，保证拖动时长得一模一样）。 */
@Composable
private fun CourseCellContent(
    course: Course,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = course.name,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (course.span >= 3) 6 else if (course.span == 2) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (course.location.isNotBlank() && course.span >= 2) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = course.location,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 一格塞不下时显示的「+N」。 */
@Composable
private fun OverflowCell(count: Int, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.padding(1.dp),
        shape = RoundedCornerShape(11.dp),
        tint = MaterialTheme.colorScheme.onSurface,
        tintAlpha = 0.16f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "+$count",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
