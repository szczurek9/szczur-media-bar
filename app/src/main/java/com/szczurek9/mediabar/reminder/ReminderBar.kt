package com.szczurek9.mediabar.reminder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.szczurek9.mediabar.integration.ReminderCard
import com.szczurek9.mediabar.overlay.BarIcon
import com.szczurek9.mediabar.overlay.BarPalette
import com.szczurek9.mediabar.overlay.IconTap
import com.szczurek9.mediabar.overlay.rememberBarPalette
import com.szczurek9.mediabar.settings.BarForm
import com.szczurek9.mediabar.settings.ThemeMode
import com.szczurek9.mediabar.settings.VAnchor

/** Rozmiary (dp) kompaktowej karty przypomnienia – stałe, jak CompactSize dla paska muzycznego. */
object ReminderSize {
    const val STANDARD_W = 236
    const val STANDARD_H = 44
    const val SLIM_W = 200
    const val SLIM_H = 28
}

@Composable
fun ReminderBarRoot(ui: ReminderUiState, actions: ReminderActions) {
    val s = ui.settings
    val dark = when (s.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // Karta z tekstem musi być czytelna: przezroczystość z ustawień jest tu mocno ograniczona
    // (88% w Media Bar daje ok. 97% na karcie przypomnienia).
    val base = s.opacityPercent / 100f
    val opacity = (base + (1f - base) * 0.75f).coerceIn(0f, 1f)
    val palette = rememberBarPalette(dark, null, false, opacity, s.animations)

    val baseDensity = LocalDensity.current
    val scaled = remember(baseDensity, s.scalePercent) { Density(baseDensity.density * s.scalePercent / 100f, 1f) }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(ui.visible) { shown = ui.visible }

    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            val card = ui.card
            AnimatedVisibility(
                visible = shown && card != null,
                enter = if (s.animations) {
                    fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.88f, transformOrigin = TransformOrigin(0.5f, 0f))
                } else EnterTransition.None,
                exit = if (s.animations) {
                    fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.96f, transformOrigin = TransformOrigin(0.5f, 0f))
                } else ExitTransition.None,
            ) {
                if (card != null) ReminderSurface(card, ui, s.form == BarForm.SLIM, s.animations, palette, actions)
            }
        }
    }
}

/**
 * Płynne rozwijanie bez zmiany rozmiaru okna w trakcie animacji: w stanie "windowLarge" okno ma stały rozmiar
 * rozwiniętej karty, a kompaktowa i rozwinięta treść są rysowane w jednej powierzchni, której prostokąt
 * (położenie, rozmiar, zaokrąglenie) jest interpolowany wyłącznie w fazie rysowania. Postęp animacji jest czytany tylko w
 * lambdach draw/layout/graphicsLayer, więc w trakcie animacji nie ma rekompozycji.
 */
@Composable
private fun ReminderSurface(
    card: ReminderCard,
    ui: ReminderUiState,
    slim: Boolean,
    animations: Boolean,
    palette: BarPalette,
    actions: ReminderActions,
) {
    val d = LocalDensity.current
    val large = ui.windowLarge
    val expandedW = with(d) { (ui.screenWidthPx - 2 * ui.sideMarginPx).coerceAtLeast(0).toDp() }
    val padTop = with(d) { ui.padTopPx.toDp() }
    val vAnchor = ui.settings.vAnchor

    val compactWPx = with(d) { (if (slim) ReminderSize.SLIM_W else ReminderSize.STANDARD_W).dp.toPx() }
    val compactHPx = with(d) { (if (slim) ReminderSize.SLIM_H else ReminderSize.STANDARD_H).dp.toPx() }
    val compactCornerPx = with(d) { (if (slim) 13.dp else 22.dp).toPx() }
    val expandedCornerPx = with(d) { 28.dp.toPx() }
    val borderPx = with(d) { 0.75.dp.toPx() }

    val progress = animateFloatAsState(
        targetValue = if (ui.expanded) 1f else 0f,
        animationSpec = if (animations) tween(340, easing = FastOutSlowInEasing) else snap(),
        label = "reminderExpand",
    )
    // Kompaktową treść zdejmujemy dopiero po zakończeniu animacji (nie zasłania przycisków rozwiniętej karty).
    val compactVisible by remember { derivedStateOf { !ui.expanded || progress.value < 0.98f } }

    Box(
        Modifier
            .then(if (large) Modifier.width(expandedW) else Modifier)
            .drawWithContent {
                val p = if (large) progress.value else 0f
                val finalTop = ui.padTopPx.toFloat()
                val left: Float
                val top: Float
                val w: Float
                val h: Float
                if (large) {
                    val startTop = when (vAnchor) {
                        VAnchor.TOP -> 0f
                        VAnchor.BOTTOM -> size.height - compactHPx
                        VAnchor.MIDDLE -> (size.height - compactHPx) / 2f
                    }
                    left = lerp(ui.compactOffsetX.toFloat(), 0f, p)
                    top = lerp(startTop, finalTop, p)
                    w = lerp(compactWPx, size.width, p)
                    h = lerp(compactHPx, size.height - finalTop, p)
                } else {
                    left = 0f; top = 0f; w = size.width; h = size.height
                }
                val r = lerp(compactCornerPx, expandedCornerPx, p).coerceAtMost(minOf(w, h) / 2f)
                val path = Path().apply { addRoundRect(RoundRect(left, top, left + w, top + h, CornerRadius(r))) }
                clipPath(path) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(palette.surfaceTop, palette.surfaceBottom),
                            startY = top,
                            endY = top + h,
                        ),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                    )
                    this@drawWithContent.drawContent()
                }
                drawPath(path, palette.border, style = Stroke(borderPx))
            },
    ) {
        if (large) {
            Box(Modifier.graphicsLayer { alpha = ((progress.value - 0.35f) / 0.65f).coerceIn(0f, 1f) }) {
                ReminderExpanded(card, palette, expandedW, padTop, actions)
            }
        }
        if (compactVisible) {
            Box(
                Modifier
                    .align(
                        when (vAnchor) {
                            VAnchor.TOP -> Alignment.TopStart
                            VAnchor.MIDDLE -> Alignment.CenterStart
                            VAnchor.BOTTOM -> Alignment.BottomStart
                        },
                    )
                    .offset { IntOffset(if (large) (ui.compactOffsetX * (1f - progress.value)).roundToInt() else 0, 0) }
                    .graphicsLayer { alpha = (1f - progress.value * 2.5f).coerceIn(0f, 1f) },
            ) {
                if (slim) ReminderSlim(card, palette, actions, enabled = !ui.expanded)
                else ReminderCompact(card, palette, actions, enabled = !ui.expanded)
            }
        }
    }
}

private fun Modifier.tapToExpand(actions: ReminderActions, enabled: Boolean): Modifier =
    if (!enabled) this
    else pointerInput(Unit) { detectTapGestures(onTap = { actions.expand() }, onLongPress = { actions.dismiss() }) }

@Composable
private fun ReminderCompact(card: ReminderCard, palette: BarPalette, actions: ReminderActions, enabled: Boolean) {
    Row(
        Modifier
            .width(ReminderSize.STANDARD_W.dp)
            .height(ReminderSize.STANDARD_H.dp)
            .padding(start = 6.dp, end = 14.dp)
            .tapToExpand(actions, enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            BellIcon(palette.accent, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(card.title, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.whenLabel, color = palette.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReminderSlim(card: ReminderCard, palette: BarPalette, actions: ReminderActions, enabled: Boolean) {
    Row(
        Modifier
            .width(ReminderSize.SLIM_W.dp)
            .height(ReminderSize.SLIM_H.dp)
            .padding(start = 8.dp, end = 12.dp)
            .tapToExpand(actions, enabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BellIcon(palette.accent, Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(card.title, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReminderExpanded(
    card: ReminderCard,
    palette: BarPalette,
    width: androidx.compose.ui.unit.Dp,
    topPad: androidx.compose.ui.unit.Dp,
    actions: ReminderActions,
) {
    Column(
        Modifier
            .width(width)
            .padding(start = 18.dp, end = 12.dp, top = 12.dp + topPad, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(palette.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                BellIcon(palette.accent, Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Przypomnienie", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconTap(BarIcon.Close, palette.textSecondary, 36.dp, 16.dp, { actions.dismiss() })
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.clickable(enabled = card.open != null) { actions.open() }) {
            Text(card.title, color = palette.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(card.whenLabel, color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        if (card.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                MarkdownText(card.description, palette.text, baseSize = 14f)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (card.done != null) {
            PillButton("Oznacz jako wykonane", palette.buttonBg, palette.buttonFg, Modifier.fillMaxWidth()) { actions.done() }
        }
        if (card.snoozes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Przypomnij później", color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                card.snoozes.forEachIndexed { index, option ->
                    PillButton(option.label, palette.text.copy(alpha = 0.12f), palette.text) { actions.snooze(index) }
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Dzwonek rysowany ręcznie – bez zależności od material-icons. */
@Composable
fun BellIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val body = Path().apply {
            moveTo(5f * u, 17f * u)
            cubicTo(7f * u, 15.5f * u, 7f * u, 11f * u, 7f * u, 10f * u)
            cubicTo(7f * u, 7f * u, 9.2f * u, 5.5f * u, 12f * u, 5.5f * u)
            cubicTo(14.8f * u, 5.5f * u, 17f * u, 7f * u, 17f * u, 10f * u)
            cubicTo(17f * u, 11f * u, 17f * u, 15.5f * u, 19f * u, 17f * u)
            close()
        }
        drawPath(body, tint)
        drawCircle(tint, radius = 1.8f * u, center = Offset(12f * u, 19.8f * u))
        drawLine(tint, Offset(12f * u, 3.5f * u), Offset(12f * u, 5.5f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
    }
}
