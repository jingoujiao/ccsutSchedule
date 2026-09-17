package com.jingoujiao.ccsutschedule.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jingoujiao.ccsutschedule.data.DEFAULT_GLASS_FROST

/**
 * 当前设置的玻璃模糊度，0..1，由「设置 → 外观 → 玻璃模糊度」控制。
 *
 * 说明：这里**不做真实背景模糊**（backdrop blur）——Compose 里做背景模糊要么靠
 * `RenderEffect`（Android 12+，每个玻璃面板一层离屏纹理，一屏几十个课程格子必然掉帧），
 * 要么自己把下层内容画两遍。参考视频里的玻璃感来自四件事：
 * 半透明磨砂填充 + 顶部高光 + 柔和外阴影 + 圆角，
 * 这四样在 Compose 里都是**一次绘制调用**，几乎不花钱。
 *
 * [glassFrost] 越大，填充越白越厚、课程色被冲得越淡、高光越明显——视觉上就是「更模糊」。
 */
val LocalGlassFrost = staticCompositionLocalOf { DEFAULT_GLASS_FROST }

@Immutable
data class GlassColors(
    val fillTop: Color,
    val fillBottom: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val sheen: Color,
    val shadow: Color,
)

/** 当前主题 + 当前模糊度的玻璃配色。 */
@Composable
fun glassColors(): GlassColors {
    val dark = LocalDarkTheme.current
    val frost = LocalGlassFrost.current.coerceIn(0f, 1f)
    return remember(dark, frost) { glassColorsOf(dark, frost) }
}

/** 纯函数版本，方便按需推导（拖动浮层等）。 */
fun glassColorsOf(dark: Boolean, frost: Float): GlassColors {
    val f = frost.coerceIn(0f, 1f)
    return if (dark) {
        GlassColors(
            fillTop = Color.White.copy(alpha = 0.16f + 0.30f * f),
            fillBottom = Color.White.copy(alpha = 0.08f + 0.26f * f),
            strokeTop = Color.White.copy(alpha = 0.10f + 0.20f * f),
            strokeBottom = Color.White.copy(alpha = 0.04f + 0.06f * f),
            sheen = Color.White.copy(alpha = 0.06f + 0.18f * f),
            shadow = Color.Black.copy(alpha = 0.55f),
        )
    } else {
        GlassColors(
            fillTop = Color.White.copy(alpha = 0.42f + 0.46f * f),
            fillBottom = Color.White.copy(alpha = 0.26f + 0.48f * f),
            strokeTop = Color.White.copy(alpha = 0.30f + 0.40f * f),
            strokeBottom = Color.White.copy(alpha = 0.16f + 0.20f * f),
            sheen = Color.White.copy(alpha = 0.10f + 0.28f * f),
            shadow = Color.Black.copy(alpha = 0.20f),
        )
    }
}

/** 模糊度越高，课程色被白色冲得越淡（磨砂玻璃会把后面的颜色「洗白」）。 */
fun tintAlphaFor(base: Float, frost: Float): Float =
    (base * (1f - 0.35f * frost.coerceIn(0f, 1f))).coerceIn(0f, 1f)

/**
 * 给任意组件套一层玻璃：磨砂填充（+ 可选的课程色染色）+ 顶部高光（+ 可选外阴影）。
 *
 * 默认**不描边**：那一圈白线在参考视频里并没有，看起来也最像「一块磨砂玻璃」。
 *
 * @param tint 给玻璃染色（课程卡片用它上课程色），null 表示中性白玻璃。
 * @param tintAlpha 染色强度，越大越像实心色块。
 * @param elevation 阴影高度，只给「浮起来」的东西用（导航、弹层、拖动的卡片）。
 */
fun Modifier.liquidGlass(
    shape: Shape,
    colors: GlassColors,
    tint: Color? = null,
    tintAlpha: Float = 0.34f,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 0.dp,
): Modifier = this
    .then(
        if (elevation > 0.dp) {
            Modifier.shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
        } else {
            Modifier
        }
    )
    .clip(shape)
    // 底色永远是磨砂白，课程色叠在上面——这样「染色」不会把磨砂感挤掉
    .background(Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom)))
    .then(
        if (tint != null && tintAlpha > 0.01f) {
            Modifier.background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = (tintAlpha + 0.08f).coerceAtMost(1f)),
                        tint.copy(alpha = (tintAlpha - 0.05f).coerceAtLeast(0f)),
                    )
                )
            )
        } else {
            Modifier
        }
    )
    .then(
        if (borderWidth > 0.dp) {
            Modifier.border(
                width = borderWidth,
                brush = Brush.verticalGradient(listOf(colors.strokeTop, colors.strokeBottom)),
                shape = shape,
            )
        } else {
            Modifier
        }
    )
    // 顶部一道高光，玻璃的「液」感基本就靠它
    .drawWithContent {
        drawContent()
        val sheenHeight = size.height * 0.42f
        if (sheenHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(colors.sheen, Color.Transparent),
                    startY = 0f,
                    endY = sheenHeight,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, sheenHeight),
            )
        }
    }

/** 中性玻璃面板。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color? = null,
    tintAlpha: Float = 0.34f,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 0.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val frost = LocalGlassFrost.current
    val colors = glassColors()
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            colors = colors,
            tint = tint,
            tintAlpha = if (tint == null) 0f else tintAlphaFor(tintAlpha, frost),
            elevation = elevation,
            borderWidth = borderWidth,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
