package com.jingoujiao.ccsutschedule.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 水滴形。
 *
 * 几何上就是「下面一个圆 + 上面收成一个尖」：
 * 圆的直径取宽度（所以底部永远是一个完整的圆），尖的高度 = 高度 − 宽度。
 * 因此 **高度要大于宽度** 才会出现尖；高度等于宽度时退化成正圆。
 *
 * @param tipRoundness 尖端的圆润程度 0..1。0 接近针尖，1 是个圆头。
 *   0.34 左右最像一滴挂在玻璃上的水（再钝就像鹅卵石了）。
 */
class WaterDropShape(
    private val tipRoundness: Float = 0.34f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(waterDropPath(size, tipRoundness))

    override fun toString(): String = "WaterDropShape(tipRoundness=$tipRoundness)"
}

/**
 * 按给定尺寸生成水滴路径（单独抽出来，方便预览 / 复用）。
 *
 * 路径 = 「尖端 → 圆的右侧」三次贝塞尔 + 「圆的右下 → 左下」半圆弧 + 「圆的左侧 → 尖端」三次贝塞尔。
 * 两侧曲线在圆上的入点方向取竖直向下，所以和下半圆是平滑相切的，不会出现折角。
 */
fun waterDropPath(size: Size, tipRoundness: Float = 0.34f): Path {
    val path = Path()
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return path

    // 底部那个圆：半径取宽度的一半，保证圆是「满」的
    val r = w / 2f
    val cx = w / 2f
    val cy = h - r
    val tipH = h - w // 尖端的高度；<= 0 说明没有尖，是个正圆

    if (tipH <= 1f) {
        path.addOval(Rect(0f, h - w, w, h))
        return path
    }

    val round = tipRoundness.coerceIn(0f, 1f)
    // 尖端两侧控制点的水平伸出量：越小越尖
    val kx = r * (0.10f + 0.62f * round)
    // 曲线在圆上「竖直方向」的入点长度：越长，脖子越修长
    val ky = r * (0.35f + 0.50f * round)

    path.moveTo(cx, 0f)
    // 右侧：尖端 → 圆的右端
    path.cubicTo(cx + kx, tipH * 0.42f, w, cy - ky, w, cy)
    // 下半圆：右端顺时针绕到左端
    path.arcTo(
        rect = Rect(cx - r, cy - r, cx + r, cy + r),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    // 左侧：圆的左端 → 尖端
    path.cubicTo(0f, cy - ky, cx - kx, tipH * 0.42f, cx, 0f)
    path.close()
    return path
}
