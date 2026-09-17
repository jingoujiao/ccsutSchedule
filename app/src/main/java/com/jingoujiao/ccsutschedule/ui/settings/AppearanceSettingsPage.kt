package com.jingoujiao.ccsutschedule.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.BackgroundPresets
import com.jingoujiao.ccsutschedule.data.CourseCardStyle
import com.jingoujiao.ccsutschedule.data.PalettePresets
import com.jingoujiao.ccsutschedule.data.SCHEDULE_FONT_ALPHA_RANGE
import com.jingoujiao.ccsutschedule.data.SCHEDULE_FONT_SCALE_RANGE
import com.jingoujiao.ccsutschedule.data.ThemeMode
import com.jingoujiao.ccsutschedule.ui.CardSurface
import com.jingoujiao.ccsutschedule.ui.CcsutSlider
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.GlyphIcon
import com.jingoujiao.ccsutschedule.ui.PillChip
import com.jingoujiao.ccsutschedule.ui.SecondaryButton
import com.jingoujiao.ccsutschedule.ui.SettingRow
import com.jingoujiao.ccsutschedule.ui.SwitchRow
import com.jingoujiao.ccsutschedule.ui.decodeScaledResource
import com.jingoujiao.ccsutschedule.ui.theme.GlassSurface
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.buildColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppearanceSettingsPage(
    state: AppStateData,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
) {
    val settings = state.settings
    PageScaffold(
        title = "外观",
        subtitle = "主题、配色与背景",
        onBack = onBack,
    ) {
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

        CardSurface {
            SettingRow(
                title = "配色方案",
                subtitle = PalettePresets.of(settings.paletteHue).name,
                glyph = Glyph.Palette,
            )
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
                                    color = if (settings.paletteHue == preset.hue) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        color
                                    },
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

        CardSurface {
            SettingRow(
                title = "内置背景",
                subtitle = if (settings.backgroundImagePath.isNotBlank()) {
                    "当前用的是自定义图片"
                } else {
                    BackgroundPresets.label(settings.backgroundPreset)
                },
                glyph = Glyph.Image,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BackgroundThumb(
                    label = "自动配色",
                    selected = settings.backgroundImagePath.isBlank() &&
                        settings.backgroundPreset == BackgroundPresets.NONE,
                    preset = null,
                    hue = settings.paletteHue,
                    onClick = {
                        onUpdateSettings {
                            it.copy(backgroundPreset = BackgroundPresets.NONE, backgroundImagePath = "")
                        }
                    },
                )
                BackgroundPresets.all.forEach { preset ->
                    BackgroundThumb(
                        label = preset.label,
                        selected = settings.backgroundImagePath.isBlank() &&
                            settings.backgroundPreset == preset.id,
                        preset = preset,
                        hue = settings.paletteHue,
                        onClick = {
                            onUpdateSettings {
                                it.copy(backgroundPreset = preset.id, backgroundImagePath = "")
                            }
                        },
                    )
                }
            }
        }

        CardSurface {
            SettingRow(
                title = "自定义背景",
                subtitle = when {
                    settings.backgroundImagePath.isNotBlank() -> "已设置，可调透明度（优先于内置壁纸）"
                    else -> "选一张自己的图片，会盖过上面的内置壁纸"
                },
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
                Text(
                    "背景不透明度 ${(settings.backgroundAlpha * 100).toInt()}%",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CcsutSlider(
                    value = settings.backgroundAlpha,
                    onValueChange = { value -> onUpdateSettings { it.copy(backgroundAlpha = value) } },
                    valueRange = 0.05f..0.75f,
                )
            }
        }

        CardSurface {
            SettingRow(
                title = "玻璃模糊度",
                subtitle = "${(settings.glassFrost * 100).toInt()}%　" +
                    "数字越大，课程卡片/底部导航/卡片与弹层越白越厚（越像磨砂玻璃）",
                glyph = Glyph.Palette,
            )
            Spacer(Modifier.height(6.dp))
            CcsutSlider(
                value = settings.glassFrost,
                onValueChange = { value -> onUpdateSettings { it.copy(glassFrost = value) } },
                valueRange = 0f..1f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "很透" to 0.35f,
                    "适中" to 0.65f,
                    "磨砂" to 0.9f,
                    "最厚" to 1f,
                ).forEach { (label, value) ->
                    PillChip(
                        text = label,
                        selected = kotlin.math.abs(settings.glassFrost - value) < 0.02f,
                        onClick = { onUpdateSettings { it.copy(glassFrost = value) } },
                    )
                }
            }
        }

        CardSurface {
            SettingRow(
                title = "课程卡片风格",
                subtitle = CourseCardStyle.label(settings.courseCardStyle) +
                    if (settings.courseCardStyle == CourseCardStyle.GLASS) {
                        "：课程本体和按钮一样，是统一的透明磨砂玻璃"
                    } else {
                        "：每门课有自己的颜色，毛玻璃上再叠一层课程色"
                    },
                glyph = Glyph.Palette,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CourseCardStyle.all.forEach { style ->
                    PillChip(
                        text = CourseCardStyle.label(style),
                        selected = settings.courseCardStyle == style,
                        onClick = { onUpdateSettings { it.copy(courseCardStyle = style) } },
                    )
                }
            }
        }

        CardSurface {
            SettingRow(
                title = "课表字号",
                subtitle = "${(settings.scheduleFontScale * 100).toInt()}%　" +
                    "课程名、教室与表头日期一起缩放",
                glyph = Glyph.Clock,
            )
            Spacer(Modifier.height(6.dp))
            CcsutSlider(
                value = settings.scheduleFontScale,
                onValueChange = { value -> onUpdateSettings { it.copy(scheduleFontScale = value) } },
                valueRange = SCHEDULE_FONT_SCALE_RANGE,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "小" to 0.85f,
                    "标准" to 1f,
                    "大" to 1.2f,
                    "特大" to 1.45f,
                ).forEach { (label, value) ->
                    PillChip(
                        text = label,
                        selected = kotlin.math.abs(settings.scheduleFontScale - value) < 0.02f,
                        onClick = { onUpdateSettings { it.copy(scheduleFontScale = value) } },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SettingRow(
                title = "课表文字不透明度",
                subtitle = "${(settings.scheduleFontAlpha * 100).toInt()}%　" +
                    "嫌字太淡就拉满，想更透一点就往下调",
                glyph = Glyph.Info,
            )
            Spacer(Modifier.height(6.dp))
            CcsutSlider(
                value = settings.scheduleFontAlpha,
                onValueChange = { value -> onUpdateSettings { it.copy(scheduleFontAlpha = value) } },
                valueRange = SCHEDULE_FONT_ALPHA_RANGE,
            )
        }

        CardSurface {
            SwitchRow(
                title = "在课表里显示非本周课程",
                subtitle = "非当前周的课程以淡色显示",
                checked = settings.showOtherWeeks,
                onCheckedChange = { checked -> onUpdateSettings { it.copy(showOtherWeeks = checked) } },
            )
        }
    }
}

/** 一张背景缩略图：有图就画图，没图（自动配色）就画当前配色的渐变。 */
@Composable
private fun BackgroundThumb(
    label: String,
    selected: Boolean,
    preset: BackgroundPresets.Preset?,
    hue: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, preset?.resId) {
        value = if (preset == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeScaledResource(context.resources, preset.resId, maxWidth = 180, maxHeight = 320)
            }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    // 整列都可点：只点缩略图的话，点下面的文字没反应，手感很怪
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 88.dp)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumb
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val scheme = buildColorScheme(hue, dark)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(scheme.primary.copy(alpha = 0.85f), scheme.tertiary.copy(alpha = 0.7f))
                            )
                        )
                )
            }
            if (selected) {
                GlassSurface(
                    modifier = Modifier.size(22.dp).align(Alignment.TopEnd).padding(3.dp),
                    shape = CircleShape,
                    tint = MaterialTheme.colorScheme.primary,
                    tintAlpha = 0.95f,
                ) {
                    GlyphIcon(
                        Glyph.Check,
                        MaterialTheme.colorScheme.onPrimary,
                        size = 11.dp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
        Text(
            label,
            fontSize = 10.5.sp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

