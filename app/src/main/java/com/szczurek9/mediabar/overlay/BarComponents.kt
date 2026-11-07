package com.szczurek9.mediabar.overlay

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** Tekst z płynnym przejściem przy zmianie zawartości (zmiana utworu). [marquee] = automatyczne przewijanie, gdy tekst się nie mieści. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwapText(
    text: String,
    animated: Boolean,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    maxLines: Int,
    modifier: Modifier = Modifier,
    marquee: Boolean = false,
) {
    val content: @Composable (String) -> Unit = { t ->
        Text(
            text = t,
            modifier = if (marquee) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 1500,
                    repeatDelayMillis = 1500,
                    velocity = 30.dp,
                )
            } else Modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = if (marquee) 1 else maxLines,
            softWrap = !marquee,
            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            letterSpacing = 0.1.sp,
            lineHeight = fontSize * 1.2f,
        )
    }
    if (animated) {
        AnimatedContent(
            targetState = text,
            modifier = modifier,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "swapText",
        ) { t -> content(t) }
    } else {
        Box(modifier) { content(text) }
    }
}

/**
 * Okładka. [size] i [radius] to lambdy czytane dopiero w fazie layout/draw (animacje bez rekomponowania).
 * Zmiana okładki: nowy obraz wjeżdża alfą NAD starym, który zostaje w pełni kryjący pod spodem. Klasyczny Crossfade
 * rysuje obie warstwy półprzezroczyście, więc w środku przejścia prześwitywało tło – to było to "mruganie".
 * Gdy okładka na moment znika (zmiana utworu), zostaje poprzednia, a dopiero po chwili pokazujemy zastępnik.
 */
@Composable
fun AlbumArt(
    art: Bitmap?,
    palette: BarPalette,
    size: () -> Dp,
    radius: () -> Dp,
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(art) }
    var under by remember { mutableStateOf<Bitmap?>(null) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(art) {
        if (art === shown) return@LaunchedEffect
        if (art == null) delay(900) // nowa okładka zwykle dociera chwilę po metadanych
        under = shown
        shown = art
        if (animated) {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(300))
        } else {
            fade.snapTo(1f)
        }
        under = null
    }

    Box(
        modifier
            .layout { measurable, _ ->
                val px = size().roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(Constraints.fixed(px, px))
                layout(px, px) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                shape = RoundedCornerShape(radius().coerceAtLeast(0.dp))
                clip = true
            }
            .background(palette.track),
        contentAlignment = Alignment.Center,
    ) {
        under?.let { bmp ->
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val current = shown
        if (current != null) {
            val image = remember(current) { current.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade.value },
            )
        } else {
            val note = size().value > 12f
            if (note) {
                Text(
                    "♪",
                    color = palette.textSecondary,
                    fontSize = (size().value * 0.45f).sp,
                    modifier = Modifier.graphicsLayer { alpha = fade.value },
                )
            }
        }
    }
}

/** Subtelny equalizer: animuje się tylko, gdy muzyka gra i animacje są włączone. */
@Composable
fun Equalizer(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val bars: List<State<Float>> = if (active) {
        val transition = rememberInfiniteTransition(label = "eq")
        listOf(420, 560, 360).mapIndexed { i, duration ->
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 120),
                ),
                label = "bar$i",
            )
        }
    } else {
        remember { List(3) { mutableFloatStateOf(0.3f) } }
    }
    Canvas(modifier.size(width = 13.dp, height = 16.dp)) {
        val barW = 3.dp.toPx()
        val gap = 2.dp.toPx()
        bars.forEachIndexed { i, state ->
            val h = size.height * state.value
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

enum class BarIcon { Play, Pause, Next, Previous, Collapse, Close, Shuffle, Repeat }

/** Ikony rysowane ręcznie – bez zależności od material-icons-extended. */
@Composable
fun BarIconView(icon: BarIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val p = Path().apply {
                moveTo(x1 * u, y1 * u)
                lineTo(x2 * u, y2 * u)
                lineTo(x3 * u, y3 * u)
                close()
            }
            drawPath(p, tint)
            drawPath(p, tint, style = stroke)
        }
        fun poly(vararg c: Float) {
            val p = Path()
            p.moveTo(c[0] * u, c[1] * u)
            var i = 2
            while (i < c.size) {
                p.lineTo(c[i] * u, c[i + 1] * u)
                i += 2
            }
            drawPath(p, tint, style = stroke)
        }
        when (icon) {
            BarIcon.Shuffle -> {
                poly(4f, 17f, 8f, 17f, 16f, 7f, 20f, 7f)
                poly(4f, 7f, 8f, 7f, 16f, 17f, 20f, 17f)
                poly(17.5f, 4.5f, 20f, 7f, 17.5f, 9.5f)
                poly(17.5f, 14.5f, 20f, 17f, 17.5f, 19.5f)
            }
            BarIcon.Repeat -> {
                poly(5f, 11f, 5f, 9.5f, 7.5f, 7f, 18f, 7f)
                poly(16f, 4.5f, 18.5f, 7f, 16f, 9.5f)
                poly(19f, 13f, 19f, 14.5f, 16.5f, 17f, 6f, 17f)
                poly(8f, 14.5f, 5.5f, 17f, 8f, 19.5f)
            }
            BarIcon.Play -> triangle(8f, 5.5f, 18.5f, 12f, 8f, 18.5f)
            BarIcon.Pause -> {
                drawRoundRect(tint, Offset(6f * u, 5f * u), Size(4f * u, 14f * u), CornerRadius(1.5f * u))
                drawRoundRect(tint, Offset(14f * u, 5f * u), Size(4f * u, 14f * u), CornerRadius(1.5f * u))
            }
            BarIcon.Next -> {
                triangle(6f, 6f, 15f, 12f, 6f, 18f)
                drawRoundRect(tint, Offset(16.5f * u, 5f * u), Size(2.5f * u, 14f * u), CornerRadius(1.2f * u))
            }
            BarIcon.Previous -> {
                triangle(18f, 6f, 9f, 12f, 18f, 18f)
                drawRoundRect(tint, Offset(5f * u, 5f * u), Size(2.5f * u, 14f * u), CornerRadius(1.2f * u))
            }
            BarIcon.Collapse -> {
                val p = Path().apply {
                    moveTo(6f * u, 15f * u)
                    lineTo(12f * u, 9f * u)
                    lineTo(18f * u, 15f * u)
                }
                drawPath(p, tint, style = stroke)
            }
            BarIcon.Close -> {
                drawLine(tint, Offset(7f * u, 7f * u), Offset(17f * u, 17f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
                drawLine(tint, Offset(17f * u, 7f * u), Offset(7f * u, 17f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun IconTap(
    icon: BarIcon,
    tint: Color,
    boxSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    background: Color? = null,
) {
    Box(
        Modifier
            .size(boxSize)
            .clip(CircleShape)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BarIconView(icon, tint, Modifier.size(iconSize))
    }
}

/** Ikona aplikacji odtwarzacza (forma Slim). */
@Composable
fun AppIcon(icon: Bitmap?, palette: BarPalette, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            val image = remember(icon) { icon.asImageBitmap() }
            Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text("♪", color = palette.textSecondary, fontSize = (size.value * 0.7f).sp)
        }
    }
}

/** Własny pasek postępu: dotknięcie i przeciąganie palcem. */
@Composable
fun SeekBar(
    progress: Float,
    enabled: Boolean,
    accent: Color,
    track: Color,
    thumb: Color,
    onPreview: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: progress).coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 7.dp.toPx()
                detectTapGestures { o ->
                    onSeek(((o.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val pad = 7.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val f = ((o.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        dragging = f
                        onPreview(f)
                    },
                    onDragEnd = {
                        dragging?.let(onSeek)
                        dragging = null
                        onPreview(null)
                    },
                    onDragCancel = {
                        dragging = null
                        onPreview(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val f = ((change.position.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                        dragging = f
                        onPreview(f)
                    },
                )
            },
    ) {
        val pad = 7.dp.toPx()
        val h = 4.dp.toPx()
        val w = size.width - 2 * pad
        val cy = size.height / 2f
        drawRoundRect(track, Offset(pad, cy - h / 2f), Size(w, h), CornerRadius(h / 2f))
        drawRoundRect(accent, Offset(pad, cy - h / 2f), Size(w * shown, h), CornerRadius(h / 2f))
        if (enabled) {
            val r = if (dragging != null) 7.dp.toPx() else 5.dp.toPx()
            drawCircle(thumb, r, Offset(pad + w * shown, cy))
        }
    }
}
