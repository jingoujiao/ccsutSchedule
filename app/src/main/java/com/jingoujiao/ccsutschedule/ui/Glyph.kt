package com.jingoujiao.ccsutschedule.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自绘图标。不依赖 material-icons-extended（离线环境没有该依赖，也省安装包体积），
 * 全部图形都在 0..1 的归一化坐标里描述，按尺寸缩放。
 */
enum class Glyph {
    Calendar, Today, Settings, Plus, Back, Edit, Delete, Close, Check,
    Image, Clock, Import, Palette, ChevronLeft, ChevronRight, ChevronDown, More, Warning, Info, Trash,
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val w = s * 0.088f
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pen = Pen(this, s, tint, stroke)

        when (glyph) {
            Glyph.Calendar -> {
                pen.roundRect(0.10f, 0.20f, 0.90f, 0.88f, 0.14f)
                pen.line(0.10f, 0.38f, 0.90f, 0.38f)
                pen.line(0.32f, 0.10f, 0.32f, 0.26f)
                pen.line(0.68f, 0.10f, 0.68f, 0.26f)
                pen.dot(0.32f, 0.58f, 0.06f)
                pen.dot(0.52f, 0.58f, 0.06f)
                pen.dot(0.72f, 0.58f, 0.06f)
                pen.dot(0.32f, 0.76f, 0.06f)
                pen.dot(0.52f, 0.76f, 0.06f)
            }

            Glyph.Today -> {
                pen.circle(0.5f, 0.5f, 0.40f)
                pen.dot(0.5f, 0.5f, 0.13f)
            }

            Glyph.Settings -> {
                // 三条带滑块的横线，比齿轮更容易在小尺寸下辨认
                pen.line(0.14f, 0.26f, 0.86f, 0.26f)
                pen.line(0.14f, 0.50f, 0.86f, 0.50f)
                pen.line(0.14f, 0.74f, 0.86f, 0.74f)
                pen.dot(0.34f, 0.26f, 0.105f)
                pen.dot(0.66f, 0.50f, 0.105f)
                pen.dot(0.42f, 0.74f, 0.105f)
            }

            Glyph.Plus -> {
                pen.line(0.5f, 0.18f, 0.5f, 0.82f)
                pen.line(0.18f, 0.5f, 0.82f, 0.5f)
            }

            Glyph.Back -> {
                pen.line(0.80f, 0.5f, 0.22f, 0.5f)
                pen.line(0.46f, 0.22f, 0.18f, 0.5f)
                pen.line(0.46f, 0.78f, 0.18f, 0.5f)
            }

            Glyph.Edit -> {
                pen.line(0.20f, 0.80f, 0.72f, 0.28f)
                pen.line(0.62f, 0.18f, 0.82f, 0.38f)
                pen.line(0.20f, 0.80f, 0.40f, 0.74f)
            }

            Glyph.Delete, Glyph.Trash -> {
                pen.line(0.14f, 0.26f, 0.86f, 0.26f)
                pen.line(0.38f, 0.26f, 0.40f, 0.14f)
                pen.line(0.62f, 0.26f, 0.60f, 0.14f)
                pen.line(0.40f, 0.14f, 0.60f, 0.14f)
                pen.roundRectPath(0.22f, 0.26f, 0.78f, 0.86f, 0.08f)
                pen.line(0.42f, 0.42f, 0.42f, 0.72f)
                pen.line(0.58f, 0.42f, 0.58f, 0.72f)
            }

            Glyph.Close -> {
                pen.line(0.22f, 0.22f, 0.78f, 0.78f)
                pen.line(0.78f, 0.22f, 0.22f, 0.78f)
            }

            Glyph.Check -> {
                pen.line(0.16f, 0.54f, 0.40f, 0.78f)
                pen.line(0.40f, 0.78f, 0.84f, 0.24f)
            }

            Glyph.Image -> {
                pen.roundRect(0.10f, 0.16f, 0.90f, 0.84f, 0.14f)
                pen.dot(0.34f, 0.36f, 0.07f)
                val path = Path().apply {
                    moveTo(0.18f * s, 0.78f * s)
                    lineTo(0.42f * s, 0.50f * s)
                    lineTo(0.58f * s, 0.66f * s)
                    lineTo(0.72f * s, 0.52f * s)
                    lineTo(0.86f * s, 0.78f * s)
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.Clock -> {
                pen.circle(0.5f, 0.5f, 0.40f)
                pen.line(0.5f, 0.28f, 0.5f, 0.52f)
                pen.line(0.5f, 0.52f, 0.68f, 0.62f)
            }

            Glyph.Import -> {
                pen.line(0.5f, 0.12f, 0.5f, 0.60f)
                pen.line(0.28f, 0.40f, 0.5f, 0.62f)
                pen.line(0.72f, 0.40f, 0.5f, 0.62f)
                pen.line(0.16f, 0.72f, 0.16f, 0.86f)
                pen.line(0.16f, 0.86f, 0.84f, 0.86f)
                pen.line(0.84f, 0.72f, 0.84f, 0.86f)
            }

            Glyph.Palette -> {
                pen.circle(0.5f, 0.5f, 0.40f)
                pen.dot(0.34f, 0.36f, 0.07f)
                pen.dot(0.64f, 0.34f, 0.07f)
                pen.dot(0.66f, 0.62f, 0.07f)
                pen.dot(0.36f, 0.66f, 0.07f)
            }

            Glyph.ChevronLeft -> {
                pen.line(0.62f, 0.22f, 0.34f, 0.5f)
                pen.line(0.34f, 0.5f, 0.62f, 0.78f)
            }

            Glyph.ChevronRight -> {
                pen.line(0.38f, 0.22f, 0.66f, 0.5f)
                pen.line(0.66f, 0.5f, 0.38f, 0.78f)
            }

            Glyph.ChevronDown -> {
                pen.line(0.22f, 0.40f, 0.5f, 0.66f)
                pen.line(0.78f, 0.40f, 0.5f, 0.66f)
            }

            Glyph.More -> {
                pen.dot(0.24f, 0.5f, 0.075f)
                pen.dot(0.5f, 0.5f, 0.075f)
                pen.dot(0.76f, 0.5f, 0.075f)
            }

            Glyph.Warning -> {
                val path = Path().apply {
                    moveTo(0.5f * s, 0.12f * s)
                    lineTo(0.92f * s, 0.86f * s)
                    lineTo(0.08f * s, 0.86f * s)
                    close()
                }
                drawPath(path, tint, style = stroke)
                pen.line(0.5f, 0.40f, 0.5f, 0.62f)
                pen.dot(0.5f, 0.74f, 0.055f)
            }

            Glyph.Info -> {
                pen.circle(0.5f, 0.5f, 0.40f)
                pen.line(0.5f, 0.46f, 0.5f, 0.70f)
                pen.dot(0.5f, 0.32f, 0.055f)
            }
        }
    }
}

private class Pen(
    private val scope: DrawScope,
    private val s: Float,
    private val color: Color,
    private val stroke: Stroke,
) {
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        scope.drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), stroke.width, StrokeCap.Round)
    }

    fun dot(x: Float, y: Float, radius: Float) {
        scope.drawCircle(color, radius * s, Offset(x * s, y * s))
    }

    fun circle(cx: Float, cy: Float, radius: Float) {
        scope.drawCircle(color, radius * s, Offset(cx * s, cy * s), style = stroke)
    }

    fun roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        scope.drawRoundRect(
            color = color,
            topLeft = Offset(left * s, top * s),
            size = Size((right - left) * s, (bottom - top) * s),
            cornerRadius = CornerRadius(radius * s, radius * s),
            style = stroke,
        )
    }

    /** 只画左、右、下三边（垃圾桶桶身），上边由桶盖线代替。 */
    fun roundRectPath(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        val path = Path().apply {
            moveTo(left * s, top * s + radius * s)
            lineTo(left * s, bottom * s - radius * s)
            quadraticTo(left * s, bottom * s, (left + radius) * s, bottom * s)
            lineTo((right - radius) * s, bottom * s)
            quadraticTo(right * s, bottom * s, right * s, bottom * s - radius * s)
            lineTo(right * s, top * s)
        }
        scope.drawPath(path, color, style = stroke)
    }
}

/** 供布局里测量用的小工具：正方形区域。 */
fun squareRect(size: Float): Rect = Rect(Offset.Zero, Size(size, size))
