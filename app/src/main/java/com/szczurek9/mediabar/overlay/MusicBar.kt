package com.szczurek9.mediabar.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.szczurek9.mediabar.media.NowPlaying
import com.szczurek9.mediabar.settings.BarForm
import com.szczurek9.mediabar.settings.ThemeMode
import com.szczurek9.mediabar.settings.VAnchor
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private enum class BarMode { Standard, Slim, Bubble, Expanded }

private fun modeOf(expanded: Boolean, form: BarForm): BarMode =
    if (expanded) BarMode.Expanded else when (form) {
        BarForm.STANDARD -> BarMode.Standard
        BarForm.SLIM -> BarMode.Slim
        BarForm.BUBBLE -> BarMode.Bubble
    }

/** Korzeń Compose nakładki: skala, motyw, kolory z okładki, animacje pojawiania się i przejścia między formami. */
@Composable
fun MusicBarRoot(
    ui: OverlayUiState,
    nowPlayingFlow: StateFlow<NowPlaying>,
    actions: BarActions,
) {
    val np by nowPlayingFlow.collectAsState()
    val s = ui.settings

    val dark = when (s.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Gdy okładka na chwilę znika (zmiana utworu), zostawiamy poprzedni kolor – bez migania tła.
    LaunchedEffect(np.art) { extractAccent(np.art)?.let { ui.accent = it } }
    val palette = rememberBarPalette(dark, ui.accent, s.dynamicColors, s.opacityPercent / 100f, s.animations)

    val baseDensity = LocalDensity.current
    val scaled = remember(baseDensity, s.scalePercent) {
        Density(baseDensity.density * s.scalePercent / 100f, 1f)
    }

    // Pierwsza kompozycja zaczyna od "false", dzięki czemu wejście jest animowane.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(ui.visible) { shown = ui.visible }

    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            val d = LocalDensity.current
            val expandedW = with(d) { (ui.screenWidthPx - 2 * ui.sideMarginPx).coerceAtLeast(0).toDp() }
            val padTop = with(d) { ui.padTopPx.toDp() }
            val moveSpec: AnimationSpec<Float> =
                if (s.animations) spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) else snap<Float>()
            val p by animateFloatAsState(if (ui.expanded) 1f else 0f, moveSpec, label = "expandProgress")
            val vAlign = when (s.vAnchor) {
                VAnchor.TOP -> Alignment.TopStart
                VAnchor.MIDDLE -> Alignment.CenterStart
                VAnchor.BOTTOM -> Alignment.BottomStart
            }

            // Duże okno (rozwinięcie) ma stały rozmiar; pasek kompaktowy leży w nim dokładnie tam, gdzie był na ekranie,
            // i podczas rozwijania płynnie przesuwa się na pełną szerokość.
            Box(
                modifier = if (ui.windowLarge) Modifier.size(expandedW, EXPANDED_HEIGHT_DP.dp + padTop) else Modifier,
                contentAlignment = Alignment.TopStart,
            ) {
                Box(
                    Modifier
                        .align(vAlign)
                        .offset {
                            if (ui.windowLarge) {
                                IntOffset(((1f - p) * ui.compactOffsetX).roundToInt(), (p * ui.padTopPx).roundToInt())
                            } else IntOffset.Zero
                        },
                ) {
                    AnimatedVisibility(
                        visible = shown,
                        enter = if (s.animations) {
                            fadeIn(tween(220)) + scaleIn(
                                tween(260),
                                initialScale = 0.88f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                            )
                        } else EnterTransition.None,
                        exit = if (s.animations) {
                            fadeOut(tween(160)) + scaleOut(
                                tween(180),
                                targetScale = 0.92f,
                                transformOrigin = TransformOrigin(0.5f, 0f),
                            )
                        } else ExitTransition.None,
                    ) {
                        BarSurface(ui, np, palette, actions, expandedW)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarSurface(
    ui: OverlayUiState,
    np: NowPlaying,
    palette: BarPalette,
    actions: BarActions,
    expandedW: Dp,
) {
    val s = ui.settings
    val mode = modeOf(ui.expanded, s.form)
    val expanded = mode == BarMode.Expanded

    val cornerTarget = when (mode) {
        BarMode.Expanded -> 32.dp
        BarMode.Slim -> 13.dp
        else -> 22.dp
    }
    // Wartości animacji NIE są czytane w kompozycji (brak delegatu `by`), tylko w fazie layout/draw.
    // Dzięki temu w trakcie animacji nie rekomponuje się cały panel w każdej klatce – to był główny powód stutteru.
    val corner = animateDpAsState(
        targetValue = cornerTarget,
        animationSpec = if (s.animations) tween<Dp>(320) else snap<Dp>(),
        label = "corner",
    )

    // Jedna okładka dla wszystkich stanów: płynnie zmienia rozmiar i pozycję (bez migania przy przejściu).
    // W formie Slim okładki nie widać (zamiast niej jest ikona aplikacji).
    // Sprężyna z tłumieniem < 1 potrafi chwilowo zejść poniżej zera (np. 16.dp -> 0.dp przy przejściu do Slim);
    // ujemny promień rogu wywalał całą aplikację, dlatego wartości są obcinane do zera w AlbumArt.
    val artSpec: AnimationSpec<Dp> =
        if (s.animations) spring<Dp>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) else snap<Dp>()
    val artSize = animateDpAsState(
        when (mode) { BarMode.Expanded -> 96.dp; BarMode.Slim -> 0.dp; else -> 32.dp }, artSpec, label = "artSize",
    )
    val artRadius = animateDpAsState(
        when (mode) { BarMode.Expanded -> 22.dp; BarMode.Slim -> 0.dp; else -> 16.dp }, artSpec, label = "artRadius",
    )
    val artX = animateDpAsState(
        when (mode) { BarMode.Expanded -> 18.dp; BarMode.Slim -> 8.dp; else -> 6.dp }, artSpec, label = "artX",
    )
    val artY = animateDpAsState(
        when (mode) { BarMode.Expanded -> 16.dp; BarMode.Slim -> 13.dp; else -> 6.dp }, artSpec, label = "artY",
    )

    Box(
        Modifier
            .graphicsLayer {
                shape = RoundedCornerShape(corner.value.coerceAtLeast(0.dp))
                clip = true
            }
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(palette.surfaceTop, palette.surfaceBottom)))
                val sw = 0.75.dp.toPx()
                val r = (corner.value.toPx() - sw / 2f).coerceAtLeast(0f)
                drawRoundRect(
                    color = palette.border,
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size((size.width - sw).coerceAtLeast(0f), (size.height - sw).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(sw),
                )
            },
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                if (s.animations) {
                    (fadeIn(tween(220, delayMillis = 80)) togetherWith fadeOut(tween(100)))
                        .using(
                            SizeTransform(clip = true) { _, _ ->
                                spring<IntSize>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
                            },
                        )
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "barState",
        ) { m ->
            when (m) {
                BarMode.Expanded -> MusicBarExpanded(
                    np = np,
                    palette = palette,
                    animations = s.animations,
                    width = expandedW,
                    shuffleOn = np.shuffleOn ?: ui.shuffleGuess,
                    repeatMode = np.repeatMode ?: ui.repeatGuess,
                    onCollapse = { actions.toggleExpanded() },
                    onHide = { actions.hide() },
                    onPlayPause = { actions.playPause() },
                    onNext = { actions.next() },
                    onPrevious = { actions.previous() },
                    onShuffle = { actions.toggleShuffle() },
                    onRepeat = { actions.toggleRepeat() },
                    onOpenApp = { actions.openApp() },
                    onSeek = { actions.seekTo(it) },
                    onSeekBounds = { actions.setSeekBounds(it) },
                )
                BarMode.Standard -> MusicBarCompact(
                    np, palette, s.animations, { actions.toggleExpanded() }, { actions.hide() },
                )
                BarMode.Slim -> MusicBarSlim(
                    np, palette, s.animations, { actions.toggleExpanded() }, { actions.hide() },
                )
                BarMode.Bubble -> MusicBarBubble(
                    np, palette, s.animations, { actions.toggleExpanded() }, { actions.hide() },
                )
            }
        }
        AlbumArt(
            art = np.art,
            palette = palette,
            size = { artSize.value },
            radius = { artRadius.value },
            animated = s.animations,
            modifier = Modifier
                .offset { IntOffset(artX.value.roundToPx(), artY.value.roundToPx()) }
                .then(
                    if (expanded) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { actions.openApp() },
                        )
                    } else Modifier,
                ),
        )
    }
}
