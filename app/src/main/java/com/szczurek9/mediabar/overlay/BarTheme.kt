package com.szczurek9.mediabar.overlay

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class BarPalette(
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val buttonBg: Color,
    val buttonFg: Color,
    val border: Color,
    val track: Color,
)

private val DefaultTint = Color(0xFF7C5CFF)

@Composable
fun rememberBarPalette(
    dark: Boolean,
    art: Color?,
    dynamic: Boolean,
    opacity: Float,
    animated: Boolean,
): BarPalette {
    val base = if (dark) Color(0xFF15151B) else Color(0xFFF7F7FB)
    val tint = if (dynamic && art != null) art else DefaultTint
    val text = if (dark) Color(0xFFF2F2F7) else Color(0xFF15151B)
    val spec = if (animated) tween<Color>(500) else snap<Color>()

    val top by animateColorAsState(
        lerp(base, tint, if (dark) 0.34f else 0.24f).copy(alpha = opacity), spec, label = "top",
    )
    val bottom by animateColorAsState(
        lerp(base, tint, if (dark) 0.10f else 0.06f).copy(alpha = opacity), spec, label = "bottom",
    )
    val accentTarget = if (dark) lerp(tint, Color.White, 0.35f) else lerp(tint, Color.Black, 0.25f)
    val accent by animateColorAsState(accentTarget, spec, label = "accent")

    return BarPalette(
        surfaceTop = top,
        surfaceBottom = bottom,
        text = text,
        textSecondary = text.copy(alpha = 0.64f),
        accent = accent,
        buttonBg = text,
        buttonFg = base,
        border = text.copy(alpha = 0.14f),
        track = text.copy(alpha = 0.18f),
    )
}

/** Dominujący kolor okładki (poza głównym wątkiem). */
suspend fun extractAccent(bitmap: Bitmap?): Color? = withContext(Dispatchers.Default) {
    if (bitmap == null) return@withContext null
    try {
        val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        val palette = Palette.from(small).maximumColorCount(10).generate()
        val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        swatch?.let { Color(it.rgb) }
    } catch (e: Exception) {
        null
    }
}
