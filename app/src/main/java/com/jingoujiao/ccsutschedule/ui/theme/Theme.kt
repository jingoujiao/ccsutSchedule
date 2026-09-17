package com.jingoujiao.ccsutschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.data.COURSE_COLOR_COUNT
import com.jingoujiao.ccsutschedule.data.ThemeMode
import kotlin.math.abs

/** 当前是否深色主题，供课程配色推导使用。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * HSL → Compose Color。整套配色都由「一个主色相」推导出来，
 * 这样换配色方案只要换一个数字，深浅色两套自动跟着走。
 */
fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

fun buildColorScheme(hue: Int, dark: Boolean): ColorScheme {
    val h = hue.toFloat()
    return if (dark) {
        darkColorScheme(
            primary = hsl(h, 0.55f, 0.74f),
            onPrimary = hsl(h, 0.60f, 0.16f),
            primaryContainer = hsl(h, 0.42f, 0.32f),
            onPrimaryContainer = hsl(h, 0.70f, 0.92f),
            inversePrimary = hsl(h, 0.58f, 0.46f),
            secondary = hsl(h + 16f, 0.28f, 0.78f),
            onSecondary = hsl(h + 16f, 0.30f, 0.18f),
            secondaryContainer = hsl(h + 16f, 0.22f, 0.30f),
            onSecondaryContainer = hsl(h + 16f, 0.34f, 0.90f),
            tertiary = hsl(h - 42f, 0.40f, 0.78f),
            onTertiary = hsl(h - 42f, 0.40f, 0.18f),
            tertiaryContainer = hsl(h - 42f, 0.26f, 0.32f),
            onTertiaryContainer = hsl(h - 42f, 0.44f, 0.90f),
            background = hsl(h, 0.18f, 0.07f),
            onBackground = hsl(h, 0.10f, 0.93f),
            surface = hsl(h, 0.16f, 0.09f),
            onSurface = hsl(h, 0.08f, 0.94f),
            surfaceVariant = hsl(h, 0.14f, 0.19f),
            onSurfaceVariant = hsl(h, 0.10f, 0.73f),
            surfaceTint = hsl(h, 0.55f, 0.74f),
            surfaceBright = hsl(h, 0.14f, 0.16f),
            surfaceDim = hsl(h, 0.16f, 0.07f),
            surfaceContainerLowest = hsl(h, 0.18f, 0.05f),
            surfaceContainerLow = hsl(h, 0.16f, 0.10f),
            surfaceContainer = hsl(h, 0.15f, 0.12f),
            surfaceContainerHigh = hsl(h, 0.14f, 0.15f),
            surfaceContainerHighest = hsl(h, 0.13f, 0.18f),
            inverseSurface = hsl(h, 0.10f, 0.92f),
            inverseOnSurface = hsl(h, 0.16f, 0.12f),
            outline = hsl(h, 0.10f, 0.46f),
            outlineVariant = hsl(h, 0.12f, 0.26f),
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = hsl(h, 0.58f, 0.46f),
            onPrimary = Color.White,
            primaryContainer = hsl(h, 0.72f, 0.92f),
            onPrimaryContainer = hsl(h, 0.62f, 0.20f),
            inversePrimary = hsl(h, 0.55f, 0.74f),
            secondary = hsl(h + 16f, 0.34f, 0.42f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h + 16f, 0.44f, 0.92f),
            onSecondaryContainer = hsl(h + 16f, 0.40f, 0.20f),
            tertiary = hsl(h - 42f, 0.44f, 0.44f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(h - 42f, 0.50f, 0.92f),
            onTertiaryContainer = hsl(h - 42f, 0.46f, 0.20f),
            background = hsl(h, 0.30f, 0.975f),
            onBackground = hsl(h, 0.28f, 0.13f),
            surface = hsl(h, 0.30f, 0.988f),
            onSurface = hsl(h, 0.28f, 0.13f),
            surfaceVariant = hsl(h, 0.22f, 0.93f),
            onSurfaceVariant = hsl(h, 0.14f, 0.36f),
            surfaceTint = hsl(h, 0.58f, 0.46f),
            surfaceBright = hsl(h, 0.30f, 0.99f),
            surfaceDim = hsl(h, 0.24f, 0.90f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.32f, 0.97f),
            surfaceContainer = hsl(h, 0.28f, 0.945f),
            surfaceContainerHigh = hsl(h, 0.26f, 0.92f),
            surfaceContainerHighest = hsl(h, 0.24f, 0.89f),
            inverseSurface = hsl(h, 0.20f, 0.18f),
            inverseOnSurface = hsl(h, 0.20f, 0.94f),
            outline = hsl(h, 0.12f, 0.60f),
            outlineVariant = hsl(h, 0.16f, 0.85f),
            scrim = Color.Black,
        )
    }
}

/** 课程卡片配色：与主色相无关的 12 个固定色相，保证相邻课程颜色区分明显。 */
fun courseColor(key: Int, dark: Boolean): Color {
    val index = key.mod(COURSE_COLOR_COUNT)
    val hue = index * (360f / COURSE_COLOR_COUNT) + 8f
    val saturation = if (dark) 0.52f else 0.62f
    val lightness = if (dark) 0.62f else 0.52f
    return hsl(hue, saturation, lightness)
}

@Composable
fun CcsutTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = buildColorScheme(settings.paletteHue, dark)

    // 状态栏/导航栏图标颜色跟着主题走，否则浅色时白底白字看不见时间电量
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides dark,
        LocalGlassFrost provides settings.glassFrost,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 判断某个颜色上应该配深色还是浅色文字。 */
fun Color.contentColorFor(): Color =
    if (luminance() > 0.55f) Color(0xFF12161C) else Color.White
