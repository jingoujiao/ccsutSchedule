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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 当前设置的玻璃模糊度，0..1，由「设置 → 外观 → 玻璃模糊度」控制。
 *
 * 它现在直接决定 Haze 的真实模糊半径（见 [hazeBlurRadius]）和噪点强度（见 [hazeNoise]），
 * 同时也会影响白膜浓度：越模糊，膜越厚、课程色被冲得越淡。
 */
val LocalGlassFrost = staticCompositionLocalOf { DEFAULT_GLASS_FROST }

/**
 * 当前屏幕上的「模糊素材」。
 *
 * 壁纸那一层（见 `AppBackground`）用 `Modifier.hazeSource(state)` 把自己登记成素材，
 * 之后画在它上面的每一块玻璃再用 `Modifier.hazeEffect(state)` 去**真的**采样、模糊它。
 * 这就是 Haze 的 backdrop blur：玻璃后面的像素被真正高斯模糊过，
 * 而不是像以前那样靠「半透明白 + 高光」假装。
 *
 * null 表示当前没有可用素材（弹层是独立窗口，见下），此时玻璃自动退化成静态磨砂。
 *
 * 为什么弹层里要传 null：Compose 的 `Popup` / `Dialog` 各自是一个独立的 Window，
 * Haze 采样用的是「相对本窗口」的坐标，跨窗口取到的位置是错的，会糊出一块错位的图。
 * 所以 `CcsutSheet` / `CcsutDialog` 里会把这里设成 null，改用「遮罩 + 静态磨砂」。
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

@Immutable
data class GlassColors(
    val fillTop: Color,
    val fillBottom: Color,
    val strokeTop: Color,
    val strokeBottom: Color,
    val sheen: Color,
    val shadow: Color,
    /**
     * 拿不到模糊素材时的兜底染色（`LocalHazeState == null`，也就是弹层窗口里；
     * 以及系统不支持模糊时）。保证那时还是一片磨砂色，而不是一块全透明玻璃。
     */
    val fallback: Color,
)

/** 当前主题 + 当前模糊度的玻璃配色。 */
@Composable
fun glassColors(): GlassColors {
    val dark = LocalDarkTheme.current
    val frost = LocalGlassFrost.current.coerceIn(0f, 1f)
    return remember(dark, frost) { glassColorsOf(dark, frost) }
}

/**
 * 磨砂度 → 真实模糊半径。
 *
 * 设置里的「玻璃模糊度」现在直接就是 Haze 的 [dev.chrisbanes.haze.HazeEffectScope.blurRadius]，
 * 拖到 0 是几乎不糊的清玻璃，拖到 1 是厚重的毛玻璃。
 */
fun hazeBlurRadius(frost: Float): Dp = (2f + 18f * frost.coerceIn(0f, 1f)).dp

/** 磨砂度 → 颗粒噪点强度。真实磨砂玻璃一定有细微颗粒，纯色渐变看着就会「假」。 */
fun hazeNoise(frost: Float): Float = 0.03f + 0.09f * frost.coerceIn(0f, 1f)

/** 纯函数版本，方便按需推导（拖动浮层等）。 */
fun glassColorsOf(dark: Boolean, frost: Float): GlassColors {
    val f = frost.coerceIn(0f, 1f)
    return if (dark) {
        GlassColors(
            // 有了真实模糊之后，白膜只负责「质感」，厚度交给模糊本身。
            // 膜太厚就会把壁纸盖死，看起来是磨砂塑料而不是玻璃。
            fillTop = Color.White.copy(alpha = 0.06f + 0.10f * f),
            fillBottom = Color.White.copy(alpha = 0.02f + 0.07f * f),
            strokeTop = Color.White.copy(alpha = 0.18f + 0.24f * f),
            strokeBottom = Color.White.copy(alpha = 0.02f + 0.05f * f),
            sheen = Color.White.copy(alpha = 0.04f + 0.09f * f),
            shadow = Color.Black.copy(alpha = 0.55f),
            // 弹层窗口里没有模糊素材，这一层就是它全部的家底。
            // 好在弹层打开时下面的课表已经整体糊掉了，所以不用做到全不透明也能看清字。
            fallback = Color(0xFF1B1E24).copy(alpha = 0.56f + 0.24f * f),
        )
    } else {
        GlassColors(
            fillTop = Color.White.copy(alpha = 0.09f + 0.13f * f),
            fillBottom = Color.White.copy(alpha = 0.04f + 0.09f * f),
            strokeTop = Color.White.copy(alpha = 0.28f + 0.32f * f),
            strokeBottom = Color.White.copy(alpha = 0.08f + 0.12f * f),
            sheen = Color.White.copy(alpha = 0.05f + 0.10f * f),
            shadow = Color.Black.copy(alpha = 0.20f),
            fallback = Color.White.copy(alpha = 0.50f + 0.24f * f),
        )
    }
}

/** 模糊度越高，课程色被玻璃「洗」得越淡。 */
fun tintAlphaFor(base: Float, frost: Float): Float =
    (base * (1f - 0.16f * frost.coerceIn(0f, 1f))).coerceIn(0f, 1f)

/**
 * 给任意组件套一层玻璃。
 *
 * 绘制顺序（从下到上）：
 *  1. 真实背景模糊（Haze，[hazeState] 不为空时）
 *  2. 磨砂白膜渐变
 *  3. 课程色染色
 *  4. 内圈折射亮边（上亮下暗，别当成描边——它是玻璃厚度的高光）
 *  5. 组件内容
 *  6. 顶部液面高光 + 左上角一点镜面反光
 *
 * @param tint 给玻璃染色（课程卡片用它上课程色），null 表示中性白玻璃。
 * @param tintAlpha 染色强度，越大越像实心色块。
 * @param elevation 阴影高度，只给「浮起来」的东西用（导航、弹层、拖动的卡片）。
 */
fun Modifier.liquidGlass(
    shape: Shape,
    colors: GlassColors,
    hazeState: HazeState? = null,
    frost: Float = DEFAULT_GLASS_FROST,
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
    // ① 真实背景模糊。Haze 会把素材层里「这块玻璃底下」的那一片采样过来做高斯模糊，
    //    低端机 / 弹层窗口没有素材时用 fallback 兜底（还是一片磨砂色，不会变成全透明）。
    .then(
        if (hazeState != null) {
            Modifier.hazeEffect(state = hazeState) {
                blurEnabled = true
                blurRadius = hazeBlurRadius(frost)
                noiseFactor = hazeNoise(frost)
                backgroundColor = Color.Transparent
                tints = emptyList()
                fallbackTint = HazeTint(colors.fallback)
                drawContentBehind = true
            }
        } else {
            Modifier.background(colors.fallback)
        }
    )
    // ② 磨砂白膜：玻璃本身的「乳白」，有真实模糊之后它只需要负责质感
    .background(Brush.verticalGradient(listOf(colors.fillTop, colors.fillBottom)))
    // ③ 课程色染色
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
    // ④ 内圈折射亮边
    .then(
        if (borderWidth > 0.dp) {
            Modifier.border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    0f to colors.strokeTop,
                    0.45f to colors.strokeTop.copy(alpha = colors.strokeTop.alpha * 0.22f),
                    1f to colors.strokeBottom,
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
                shape = shape,
            )
        } else {
            Modifier
        }
    )
    // ⑤ 内容之上：顶部液面高光 + 左上角镜面反光
    .drawWithContent {
        drawContent()
        val sheenHeight = size.height * 0.44f
        if (sheenHeight > 0.5f) {
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
        // 左上一小块柔和的镜面反光：这是「玻璃」和「塑料」最大的区别
        val specularRadius = size.minDimension * 0.75f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.sheen, Color.Transparent),
                center = Offset(size.width * 0.16f, size.height * 0.04f),
                radius = specularRadius,
            ),
            size = size,
        )
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
    val frost = LocalGlassFrost.current
    val colors = glassColors()
    val hazeState = LocalHazeState.current
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            colors = colors,
            hazeState = hazeState,
            frost = frost,
            tint = tint,
            tintAlpha = if (tint == null) 0f else tintAlphaFor(tintAlpha, frost),
            elevation = elevation,
            borderWidth = borderWidth,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
