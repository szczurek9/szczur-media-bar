package com.szczurek9.mediabar.overlay

import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.szczurek9.mediabar.media.NowPlaying
import com.szczurek9.mediabar.media.RepeatMode
import kotlinx.coroutines.delay

/** Wysokość rozwiniętego panelu (dp). */
const val EXPANDED_HEIGHT_DP = 256

/** Pełny panel na całą szerokość: okładka, tytuł, wykonawca, pasek postępu, czasy, shuffle / sterowanie / repeat. */
@Composable
fun MusicBarExpanded(
    np: NowPlaying,
    palette: BarPalette,
    animations: Boolean,
    width: Dp,
    shuffleOn: Boolean,
    repeatMode: RepeatMode,
    onCollapse: () -> Unit,
    onHide: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onOpenApp: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBounds: (Rect?) -> Unit,
) {
    // Lokalny zegar tylko gdy panel jest rozwinięty i muzyka gra (oszczędność baterii).
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.isPlaying) {
        now = SystemClock.elapsedRealtime()
        if (!np.isPlaying) return@LaunchedEffect
        while (true) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }

    // Po przewinięciu trzymamy docelową pozycję, dopóki odtwarzacz nie zgłosi nowego stanu.
    var seekOverride by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var preview by remember { mutableStateOf<Float?>(null) }

    val override = seekOverride?.takeIf { np.positionUpdatedAt <= it.second }
    val position = if (override != null) np.estimate(override.first, override.second, now) else np.estimate(np.positionMs, np.positionUpdatedAt, now)
    val progress = if (np.hasDuration) (position.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
    val shownPosition = preview?.let { (it * np.durationMs).toLong() } ?: position

    val density = LocalDensity.current.density
    val openApp = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onOpenApp,
    )

    DisposableEffect(Unit) {
        onDispose { onSeekBounds(null) }
    }

    Column(
        Modifier
            .width(width)
            .height(EXPANDED_HEIGHT_DP.dp)
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Spacer(Modifier.size(96.dp)) // okładkę rysuje wspólna warstwa w BarSurface (dotknięcie = otwórz aplikację)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconTap(BarIcon.Close, palette.textSecondary, 32.dp, 16.dp, onHide)
                    IconTap(BarIcon.Collapse, palette.textSecondary, 32.dp, 20.dp, onCollapse)
                }
                SwapText(np.title, animations, palette.text, 17.sp, FontWeight.SemiBold, 2, modifier = openApp)
                Spacer(Modifier.height(2.dp))
                SwapText(np.artist, animations, palette.textSecondary, 13.sp, FontWeight.Medium, 1)
            }
        }

        Spacer(Modifier.height(12.dp))

        SeekBar(
            progress = progress,
            enabled = np.canSeek,
            accent = palette.accent,
            track = palette.track,
            thumb = palette.text,
            onPreview = { preview = it },
            onSeek = { fraction ->
                val target = (fraction * np.durationMs).toLong()
                seekOverride = target to SystemClock.elapsedRealtime()
                preview = null
                onSeek(target)
            },
            modifier = Modifier.onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                val extra = 12 * density
                onSeekBounds(
                    Rect(
                        b.left.toInt(),
                        (b.top - extra).toInt(),
                        b.right.toInt(),
                        (b.bottom + extra).toInt(),
                    ),
                )
            },
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shownPosition), color = palette.textSecondary, fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            Text(
                if (np.hasDuration) formatTime(np.durationMs) else "--:--",
                color = palette.textSecondary,
                fontSize = 11.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideButton(BarIcon.Shuffle, np.shuffleAction != null, shuffleOn, false, palette, onShuffle)
            IconTap(BarIcon.Previous, palette.text, 48.dp, 26.dp, onPrevious)
            IconTapPlayPause(np.isPlaying, palette, onPlayPause)
            IconTap(BarIcon.Next, palette.text, 48.dp, 26.dp, onNext)
            SideButton(
                BarIcon.Repeat,
                np.repeatAction != null,
                repeatMode != RepeatMode.OFF,
                repeatMode == RepeatMode.ONE,
                palette,
                onRepeat,
            )
        }
    }
}

/**
 * Shuffle / repeat: przyciskane tylko, gdy aplikacja udostępnia taką akcję (inaczej przygaszone).
 * Włączone = kolor akcentu + kropka pod ikoną. Repeat „jeden utwór” dodatkowo ma plakietkę z cyfrą 1.
 */
@Composable
private fun SideButton(
    icon: BarIcon,
    available: Boolean,
    active: Boolean,
    badgeOne: Boolean,
    palette: BarPalette,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = when {
            !available -> palette.textSecondary.copy(alpha = 0.3f)
            active -> palette.accent
            else -> palette.textSecondary
        },
        animationSpec = tween(180),
        label = "sideTint",
    )
    val dot by animateFloatAsState(if (available && active) 1f else 0f, tween(180), label = "sideDot")
    val badge by animateFloatAsState(if (available && badgeOne) 1f else 0f, tween(180), label = "sideBadge")

    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = available, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BarIconView(icon, tint, Modifier.size(22.dp))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 3.dp)
                .size(4.dp)
                .graphicsLayer { alpha = dot }
                .background(tint, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 3.dp)
                .size(14.dp)
                .graphicsLayer {
                    alpha = badge
                    scaleX = 0.6f + 0.4f * badge
                    scaleY = 0.6f + 0.4f * badge
                }
                .background(palette.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "1",
                color = palette.buttonFg,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun IconTapPlayPause(isPlaying: Boolean, palette: BarPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(palette.buttonBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = isPlaying, animationSpec = tween<Float>(180), label = "playPause") { playing ->
            BarIconView(
                if (playing) BarIcon.Pause else BarIcon.Play,
                palette.buttonFg,
                Modifier.size(28.dp),
            )
        }
    }
}
