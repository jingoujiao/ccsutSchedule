package com.jingoujiao.ccsutschedule.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.AppStateData
import com.jingoujiao.ccsutschedule.data.PalettePresets
import com.jingoujiao.ccsutschedule.data.ThemeMode
import com.jingoujiao.ccsutschedule.ui.CardSurface
import com.jingoujiao.ccsutschedule.ui.Glyph
import com.jingoujiao.ccsutschedule.ui.GlyphIcon
import com.jingoujiao.ccsutschedule.ui.PillChip
import com.jingoujiao.ccsutschedule.ui.SecondaryButton
import com.jingoujiao.ccsutschedule.ui.SettingRow
import com.jingoujiao.ccsutschedule.ui.SwitchRow
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.buildColorScheme

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
                Text(
                    "背景不透明度 ${(settings.backgroundAlpha * 100).toInt()}%",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.backgroundAlpha,
                    onValueChange = { value -> onUpdateSettings { it.copy(backgroundAlpha = value) } },
                    valueRange = 0.05f..0.75f,
                )
            }
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

