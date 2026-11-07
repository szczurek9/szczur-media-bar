package com.szczurek9.mediabar.overlay

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.szczurek9.mediabar.media.NowPlaying

/** Szerokości i wysokości (dp) form kompaktowych – stałe, żeby okno nie zmieniało rozmiaru przy zmianie tytułu. */
object CompactSize {
    const val STANDARD_W = 216
    const val STANDARD_H = 44
    const val SLIM_W = 170
    const val SLIM_H = 26
    const val BUBBLE_W = 76
    const val BUBBLE_H = 44
}

private fun Modifier.tapToExpand(onExpand: () -> Unit, onHide: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }, onLongPress = { onHide() }) }

/** Standard: okładka, tytuł (przewijany), wykonawca, equalizer. Dotknięcie = rozwiń, długie przytrzymanie = ukryj. */
@Composable
fun MusicBarCompact(
    np: NowPlaying,
    palette: BarPalette,
    animations: Boolean,
    onExpand: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        Modifier
            .width(CompactSize.STANDARD_W.dp)
            .height(CompactSize.STANDARD_H.dp)
            .padding(start = 6.dp, end = 14.dp)
            .tapToExpand(onExpand, onHide),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Spacer(Modifier.size(32.dp)) // okładkę rysuje wspólna warstwa w BarSurface
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            SwapText(np.title, animations, palette.text, 13.sp, FontWeight.SemiBold, 1, marquee = animations)
            SwapText(np.artist, animations, palette.textSecondary, 11.sp, FontWeight.Medium, 1)
        }
        Spacer(Modifier.width(10.dp))
        Equalizer(active = np.isPlaying && animations, color = palette.accent)
    }
}

/** Slim: tylko mała ikona aplikacji, z której słuchasz, i tytuł. Kolor tła zmienia się jak zwykle. */
@Composable
fun MusicBarSlim(
    np: NowPlaying,
    palette: BarPalette,
    animations: Boolean,
    onExpand: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        Modifier
            .width(CompactSize.SLIM_W.dp)
            .height(CompactSize.SLIM_H.dp)
            .padding(start = 8.dp, end = 12.dp)
            .tapToExpand(onExpand, onHide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(np.appIcon, palette, 16.dp)
        Spacer(Modifier.width(7.dp))
        SwapText(
            np.title, animations, palette.text, 11.sp, FontWeight.SemiBold, 1,
            modifier = Modifier.weight(1f), marquee = animations,
        )
    }
}

/** Bubble: tylko okładka i equalizer. */
@Composable
fun MusicBarBubble(
    np: NowPlaying,
    palette: BarPalette,
    animations: Boolean,
    onExpand: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        Modifier
            .width(CompactSize.BUBBLE_W.dp)
            .height(CompactSize.BUBBLE_H.dp)
            .padding(start = 6.dp, end = 14.dp)
            .tapToExpand(onExpand, onHide),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(32.dp)) // okładkę rysuje wspólna warstwa w BarSurface
        Spacer(Modifier.width(10.dp))
        Equalizer(active = np.isPlaying && animations, color = palette.accent)
    }
}
