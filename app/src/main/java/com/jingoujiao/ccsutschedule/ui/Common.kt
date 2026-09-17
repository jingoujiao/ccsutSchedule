package com.jingoujiao.ccsutschedule.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.jingoujiao.ccsutschedule.data.AppSettings
import com.jingoujiao.ccsutschedule.ui.theme.GlassSurface
import com.jingoujiao.ccsutschedule.ui.theme.LocalDarkTheme
import com.jingoujiao.ccsutschedule.ui.theme.hsl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 背景：底色（或自定义图片）+ 上下柔和蒙层，保证玻璃面板与文字都读得清。
 *
 * 全部用一次 [drawBehind] 画完：底色光斑 → 背景图（等比裁切铺满）→ 蒙层。
 * 这样玻璃面板是「真的浮在图片上」，而不是浮在一个不透明的色块上。
 */
@Composable
fun AppBackground(settings: AppSettings, content: @Composable BoxScope.() -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, settings.backgroundImagePath) {
        val path = settings.backgroundImagePath
        value = if (path.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeScaledBitmap(path) }
        }
    }
    val dark = LocalDarkTheme.current
    val hue = settings.paletteHue
    val alpha = settings.backgroundAlpha.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(if (dark) hsl(hue.toFloat(), 0.22f, 0.08f) else hsl(hue.toFloat(), 0.34f, 0.97f))
                drawAurora(hue, dark)
                val image = bitmap
                if (image != null) drawImageCropped(image, alpha)
                // 顶部/底部蒙层：文字颜色是跟着主题走的，所以蒙层也用主题底色——
                // 浅色主题配深色照片、深色主题配浅色照片都能读清，只把壁纸压成一层雾。
                val scrim = if (dark) {
                    Color.Black.copy(alpha = 0.46f)
                } else {
                    hsl(hue.toFloat(), 0.30f, 0.97f).copy(alpha = 0.62f)
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to scrim,
                        0.22f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to scrim.copy(alpha = scrim.alpha * 0.9f),
                    ),
                    size = size,
                )
            }
    ) {
        content()
    }
}

/** 底色光斑，给玻璃提供「透出来」的颜色。静态绘制，不做动画。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAurora(hue: Int, dark: Boolean) {
    val h = hue.toFloat()
    val blobs = listOf(
        Triple(hsl(h + 34f, 0.62f, if (dark) 0.26f else 0.78f), Offset(0.12f, 0.06f), 0.72f),
        Triple(hsl(h - 52f, 0.58f, if (dark) 0.22f else 0.80f), Offset(0.94f, 0.20f), 0.62f),
        Triple(hsl(h + 96f, 0.50f, if (dark) 0.20f else 0.82f), Offset(0.30f, 0.98f), 0.70f),
    )
    val a = if (dark) 0.55f else 0.75f
    blobs.forEach { (color, anchor, radius) ->
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = a), Color.Transparent),
                center = Offset(size.width * anchor.x, size.height * anchor.y),
                radius = size.minDimension * radius,
            ),
            size = size,
        )
    }
}

/** 等比裁切（CenterCrop）画一张图，避免拉伸变形。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageCropped(
    image: ImageBitmap,
    alpha: Float,
) {
    if (alpha <= 0.01f) return
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val scale = maxOf(size.width / iw, size.height / ih)
    val dw = iw * scale
    val dh = ih * scale
    drawImage(
        image = image,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(image.width, image.height),
        dstOffset = androidx.compose.ui.unit.IntOffset(
            ((size.width - dw) / 2f).toInt(),
            ((size.height - dh) / 2f).toInt(),
        ),
        dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}

private fun decodeScaledBitmap(path: String): ImageBitmap? = try {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 2400) sample *= 2
    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    android.graphics.BitmapFactory.decodeFile(path, options)?.asImageBitmap()
} catch (_: Throwable) {
    null
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            actions()
        }
    }
}

@Composable
fun IconAction(glyph: Glyph, contentDescription: String, onClick: () -> Unit, tint: Color? = null) {
    CircleIconButton(
        glyph = glyph,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun CircleIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val tintColor = if (container == Color.Unspecified) null else container
    val borderTint = if (borderColor == Color.Unspecified) null else borderColor
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "circle-press")
    GlassSurface(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        tint = tintColor,
        tintAlpha = 0.5f,
        borderWidth = if (borderTint == null) 1.dp else 1.dp,
    ) {
        GlyphIcon(glyph, tint, size = size * 0.5f, modifier = Modifier.align(Alignment.Center))
    }
}

/** 「…」菜单：底部弹层式操作列表，比下拉菜单更好点。 */
@Composable
fun ActionSheet(
    visible: Boolean,
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    CcsutSheet(visible = visible, onDismiss = onDismiss) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        actions.forEach { (label, action) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onDismiss()
                        action()
                    }
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** 玻璃卡片。 */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        tint = if (color == Color.Unspecified) null else color,
        tintAlpha = 0.5f,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    glyph: Glyph? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            GlassSurface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(11.dp),
                tint = MaterialTheme.colorScheme.primary,
                tintAlpha = 0.30f,
            ) {
                GlyphIcon(
                    glyph,
                    MaterialTheme.colorScheme.primary,
                    size = 18.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** 自绘胶囊选择块，比 M3 的 FilterChip 更紧凑，且不受实验 API 影响。 */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val textColor = if (selected) {
        if (accent.luminance() > 0.6f) Color(0xFF10151B) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    GlassSurface(
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        tint = if (selected) accent else null,
        tintAlpha = 0.78f,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = textColor,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 13.dp),
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: Glyph? = null,
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    GlassSurface(
        modifier = modifier
            .height(46.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        tint = accent,
        tintAlpha = if (enabled) 0.92f else 0.35f,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (glyph != null) {
                GlyphIcon(glyph, contentColor, size = 17.dp)
                Spacer(Modifier.width(7.dp))
            }
            Text(text, color = contentColor, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
) {
    GlassSurface(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (glyph != null) {
                GlyphIcon(glyph, MaterialTheme.colorScheme.onSurfaceVariant, size = 17.dp)
                Spacer(Modifier.width(7.dp))
            }
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.5.sp)
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        },
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, glyph: Glyph = Glyph.Info) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassSurface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            tint = MaterialTheme.colorScheme.primary,
            tintAlpha = 0.22f,
        ) {
            GlyphIcon(
                glyph,
                MaterialTheme.colorScheme.primary,
                size = 28.dp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 手写底部弹层：避免使用实验性的 ModalBottomSheet。
 *
 * 用 [Popup] 起独立窗口，这样弹层一定盖在悬浮导航之上（同一窗口里兄弟节点会压住它）。
 * 玻璃面板 + 顶部圆角，浮在背景之上。
 */
@Composable
fun CcsutSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(onClick = onDismiss)
            )
            AnimatedVisibility(
                visible = appeared,
                enter = slideInVertically(tween(220)) { it },
                exit = slideOutVertically(tween(180)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    elevation = 18.dp,
                    borderWidth = 1.dp,
                ) {
                    Column(
                        Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 26.dp)
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(38.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(Modifier.height(16.dp))
                        content()
                    }
                }
            }
        }
    }
}

/** 玻璃对话框，替代 M3 的 AlertDialog（后者是实心色，压在壁纸背景上很突兀）。 */
@Composable
fun CcsutDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                elevation = 22.dp,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.heightIn(max = 430.dp), content = content)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = buttons,
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    CcsutDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            SecondaryButton(dismissText, onDismiss, modifier = Modifier.weight(1f))
            PrimaryButton(confirmText, onConfirm, modifier = Modifier.weight(1f))
        },
    ) {
        Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = placeholder?.let { { Text(it, fontSize = 14.sp) } },
        singleLine = singleLine,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
fun StepperRow(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    subtitle: String? = null,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton("−", onMinus)
                Text(
                    value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                StepperButton("+", onPlus)
            }
        },
    )
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
