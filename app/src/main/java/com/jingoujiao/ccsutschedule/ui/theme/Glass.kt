package com.jingoujiao.ccsutschedule.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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

/**
 * 「液态玻璃」视觉令牌。
 *
 * 说明：这里**不做真实背景模糊**（backdrop blur）——Compose 里做背景模糊要么靠
 * `RenderEffect`（Android 12+，每个玻璃面板一层离屏纹理，一屏几十个课程格子必然掉帧），
 * 要么自己把下层内容画两遍。参考视频里的玻璃感其实来自四件事：
 * 半透明填充 + 顶部高光 + 一圈渐变描边 + 柔和外阴影，
 * 这四样在 Compose 里都是**一次绘制调用**，几乎不花钱，所以这里按这个思路实现。
 */
@Immutable
data class GlassColors(
    val fillTop: Color,
    val fillBottom: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val sheen: Color,
    val shadow: Color,
)

/** 浅色下的玻璃：偏白、通透。 */
private val LightGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.80f),
    fillBottom = Color.White.copy(alpha = 0.64f),
    strokeTop = Color.White.copy(alpha = 0.98f),
    strokeBottom = Color.White.copy(alpha = 0.50f),
    sheen = Color.White.copy(alpha = 0.36f),
    shadow = Color.Black.copy(alpha = 0.20f),
)

/** 深色下的玻璃：不是「黑玻璃」，而是低透明度的白，才有磨砂感。 */
private val DarkGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.24f),
    fillBottom = Color.White.copy(alpha = 0.13f),
    strokeTop = Color.White.copy(alpha = 0.38f),
    strokeBottom = Color.White.copy(alpha = 0.10f),
    sheen = Color.White.copy(alpha = 0.14f),
    shadow = Color.Black.copy(alpha = 0.55f),
)

/** 当前主题的玻璃配色。 */
@Composable
fun glassColors(): GlassColors {
    val dark = LocalDarkTheme.current
    return remember(dark) { if (dark) DarkGlass else LightGlass }
}

/**
 * 给任意组件套一层玻璃：填充渐变 + 描边渐变 + 顶部高光（可选外阴影）。
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
    borderWidth: Dp = 1.dp,
): Modifier = this
    .then(
        if (elevation > 0.dp) {
            Modifier.shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
        } else {
            Modifier
        }
    )
    .clip(shape)
    .background(
        if (tint != null) {
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = (tintAlpha + 0.10f).coerceAtMost(1f)),
                    tint.copy(alpha = (tintAlpha - 0.06f).coerceAtLeast(0f)),
                )
            )
        } else {
            Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom))
        }
    )
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(listOf(colors.strokeTop, colors.strokeBottom)),
        shape = shape,
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
    borderWidth: Dp = 1.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = glassColors()
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            colors = colors,
            tint = tint,
            tintAlpha = tintAlpha,
            elevation = elevation,
            borderWidth = borderWidth,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

