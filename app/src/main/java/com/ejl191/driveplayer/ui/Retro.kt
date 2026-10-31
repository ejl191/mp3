package com.ejl191.driveplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Late-90s desktop media player look: gunmetal chrome, bevelled buttons, green-on-black LCD. */
object Retro {
    val Chrome = Color(0xFF2C2C3E)
    val ChromeDark = Color(0xFF1C1C28)
    val BevelLight = Color(0xFF6A6A88)
    val BevelDark = Color(0xFF0C0C14)

    val TitleBg = Color(0xFF1A1A26)
    val TitleStripe = Color(0xFFB8952E)
    val TitleText = Color(0xFFF2E6B8)

    val ButtonFace = Color(0xFFBDBDCB)
    val ButtonLight = Color(0xFFF4F4FA)
    val ButtonDark = Color(0xFF55556A)
    val Glyph = Color(0xFF1A1A24)
    val GlyphDisabled = Color(0xFF85859A)

    val Lcd = Color(0xFF000000)
    val LcdGreen = Color(0xFF00E000)
    val LcdGhost = Color(0xFF0B300B)
    val PlText = Color(0xFF00FF00)
    val PlDim = Color(0xFF00A000)
    val PlCurrent = Color(0xFFFFFFFF)
    val PlSelected = Color(0xFF0000C6)
    val Error = Color(0xFFFF4040)
    val Label = Color(0xFFD8D8E8)

    val VizGreen = Color(0xFF18D018)
    val VizYellow = Color(0xFFE8E800)
    val VizRed = Color(0xFFE82000)

    val small = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    val body = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
}

/** Draws a 3D edge around the content: light top-left and dark bottom-right when raised, the reverse when sunken. */
fun Modifier.bevel(
    raised: Boolean = true,
    light: Color = Retro.BevelLight,
    dark: Color = Retro.BevelDark,
    width: Dp = 2.dp,
): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val topLeft = if (raised) light else dark
    val bottomRight = if (raised) dark else light
    drawRect(topLeft, Offset.Zero, Size(size.width, w))
    drawRect(topLeft, Offset.Zero, Size(w, size.height))
    drawRect(bottomRight, Offset(0f, size.height - w), Size(size.width, w))
    drawRect(bottomRight, Offset(size.width - w, 0f), Size(w, size.height))
}.padding(width)

@Composable
fun RetroPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(Retro.Chrome).bevel().padding(6.dp), content = content)
}

/** Sunken black display area. */
@Composable
fun LcdBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(Retro.Lcd).bevel(raised = false).padding(horizontal = 6.dp, vertical = 4.dp), content = content)
}

@Composable
fun RetroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .background(Retro.ButtonFace)
            .bevel(raised = !(pressed || active), light = Retro.ButtonLight, dark = Retro.ButtonDark)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) Retro.Glyph else Retro.GlyphDisabled) {
            content()
        }
    }
}

@Composable
fun RetroIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    iconSize: Dp = 22.dp,
) {
    RetroButton(onClick, modifier, enabled, active) {
        Icon(icon, contentDescription, Modifier.size(iconSize))
    }
}

@Composable
fun RetroTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    RetroButton(onClick, modifier, enabled, active) {
        Text(text, style = Retro.small, color = LocalContentColor.current, maxLines = 1)
    }
}

/** Gold-ribbed title strip with the title in the middle, like an old skinned window. */
@Composable
fun StripeBar(title: String, modifier: Modifier = Modifier) {
    Box(modifier.height(14.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val step = 3.dp.toPx()
            val line = 1.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                drawRect(Retro.TitleStripe, Offset(0f, y), Size(size.width, line))
                y += step
            }
        }
        Text(
            title.uppercase(),
            Modifier.background(Retro.TitleBg).padding(horizontal = 8.dp),
            style = Retro.small,
            color = Retro.TitleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RetroTitleBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Retro.TitleBg)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RetroIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, iconSize = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        StripeBar(title, Modifier.weight(1f))
        actions()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(text: String, modifier: Modifier = Modifier, color: Color = Retro.LcdGreen, style: TextStyle = Retro.body) {
    Text(
        text,
        modifier.basicMarquee(iterations = Int.MAX_VALUE),
        color = color,
        style = style,
        maxLines = 1,
    )
}

/** Big LCD digits with unlit "8" segments showing faintly behind them. */
@Composable
fun LcdTime(text: String, modifier: Modifier = Modifier) {
    // Shrink for long times like "-1:23:45" so they still fit the display.
    val size = when {
        text.length <= 5 -> 30.sp
        text.length <= 7 -> 23.sp
        else -> 20.sp
    }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = size)
    Box(modifier) {
        Text(text.map { if (it.isDigit()) '8' else it }.joinToString(""), style = style, color = Retro.LcdGhost)
        Text(text, style = style, color = Retro.LcdGreen)
    }
}

/** Decorative spectrum-style bars; they bounce while playing and fall when paused. */
@Composable
fun Visualizer(playing: Boolean, modifier: Modifier = Modifier) {
    val bars = remember { FloatArray(BAR_COUNT) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        while (true) {
            var anyUp = false
            for (i in bars.indices) {
                val target = if (playing) {
                    // Speech sits in the lower-middle bands, so weight those higher.
                    val shape = 1f - kotlin.math.abs(i - BAR_COUNT * 0.35f) / BAR_COUNT
                    (Random.nextFloat() * Random.nextFloat() * 1.6f * shape).coerceAtMost(1f)
                } else {
                    0f
                }
                bars[i] = maxOf(target, bars[i] - 0.07f).coerceAtLeast(0f)
                if (bars[i] > 0f) anyUp = true
            }
            frame++
            if (!playing && !anyUp) break
            delay(60)
        }
    }
    Canvas(modifier) {
        frame // redraw on every animation step
        val segment = 2.dp.toPx()
        val gap = 1.dp.toPx()
        val barWidth = (size.width - gap * (BAR_COUNT - 1)) / BAR_COUNT
        val segments = (size.height / (segment + gap)).toInt().coerceAtLeast(1)
        for (i in bars.indices) {
            val lit = (bars[i] * segments).toInt()
            val x = i * (barWidth + gap)
            for (s in 0 until lit) {
                val level = s.toFloat() / segments
                val color = when {
                    level > 0.8f -> Retro.VizRed
                    level > 0.55f -> Retro.VizYellow
                    else -> Retro.VizGreen
                }
                val y = size.height - (s + 1) * (segment + gap) + gap
                drawRect(color, Offset(x, y), Size(barWidth, segment))
            }
        }
    }
}

private const val BAR_COUNT = 19

/** Groove with a bevelled thumb. [onPreview] reports the drag position (null when released). */
@Composable
fun RetroSeekBar(
    fraction: Float,
    enabled: Boolean,
    onPreview: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableIntStateOf(1) }
    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onSeek((it.x / width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        last = (it.x / width).coerceIn(0f, 1f)
                        onPreview(last)
                    },
                    onDragEnd = {
                        onPreview(null)
                        onSeek(last)
                    },
                    onDragCancel = { onPreview(null) },
                ) { change, _ ->
                    last = (change.position.x / width).coerceIn(0f, 1f)
                    onPreview(last)
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val px = 1.dp.toPx()
            val grooveH = 6.dp.toPx()
            val y = (size.height - grooveH) / 2
            drawRect(Retro.BevelDark, Offset(0f, y), Size(size.width, grooveH))
            drawRect(Retro.BevelLight, Offset(0f, y + grooveH - px), Size(size.width, px))
            val thumbW = 28.dp.toPx()
            val x = (size.width - thumbW) * fraction.coerceIn(0f, 1f)
            drawRect(Retro.LcdGhost, Offset(px, y + px), Size(x, grooveH - 2 * px))
            if (!enabled) return@Canvas
            drawRect(Retro.ButtonFace, Offset(x, 0f), Size(thumbW, size.height))
            drawRect(Retro.ButtonLight, Offset(x, 0f), Size(thumbW, 2 * px))
            drawRect(Retro.ButtonLight, Offset(x, 0f), Size(2 * px, size.height))
            drawRect(Retro.ButtonDark, Offset(x, size.height - 2 * px), Size(thumbW, 2 * px))
            drawRect(Retro.ButtonDark, Offset(x + thumbW - 2 * px, 0f), Size(2 * px, size.height))
            for (k in -1..1) {
                drawRect(Retro.ButtonDark, Offset(x + thumbW / 2 + k * 4 * px, size.height * 0.3f), Size(px, size.height * 0.4f))
            }
        }
    }
}

/** One line in a playlist-editor style list: green on black, current item white on blue. */
@Composable
fun PlaylistRow(
    text: String,
    modifier: Modifier = Modifier,
    number: Int? = null,
    trailing: String? = null,
    secondary: String? = null,
    current: Boolean = false,
    onClick: (() -> Unit)? = null,
    end: @Composable RowScope.() -> Unit = {},
) {
    val color = if (current) Retro.PlCurrent else Retro.PlText
    Row(
        modifier
            .fillMaxWidth()
            .background(if (current) Retro.PlSelected else Color.Transparent)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    (number?.let { "$it. " } ?: "") + text,
                    Modifier.weight(1f),
                    style = Retro.body,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing?.let { Text(" $it", style = Retro.body, color = color, maxLines = 1) }
            }
            secondary?.let {
                Text(
                    it,
                    style = Retro.small,
                    color = if (current) Retro.PlCurrent.copy(alpha = 0.75f) else Retro.PlDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        end()
    }
}

/** Small green-on-black readout, like the bitrate boxes on old players. */
@Composable
fun InfoBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Retro.Lcd).bevel(raised = false, width = 1.dp).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(text, style = Retro.small, color = Retro.LcdGreen, maxLines = 1)
    }
}
