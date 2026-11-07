package com.szczurek9.mediabar.overlay

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.szczurek9.mediabar.media.RepeatMode
import com.szczurek9.mediabar.settings.BarSettings

/** Stan UI nakładki obserwowany przez Compose. */
class OverlayUiState {
    var visible by mutableStateOf(false)
    var expanded by mutableStateOf(false)

    /** Okno systemowe ma stały, duży rozmiar na czas rozwinięcia i animacji (bez zmiany rozmiaru okna w każdej klatce). */
    var windowLarge by mutableStateOf(false)

    /** Szerokość ekranu i boczny margines (px) – rozwinięty panel zajmuje całą szerokość minus margines. */
    var screenWidthPx by mutableIntStateOf(0)
    var sideMarginPx by mutableIntStateOf(0)

    /** Poziome położenie paska kompaktowego względem lewej krawędzi dużego okna (px). */
    var compactOffsetX by mutableIntStateOf(0)

    /** Dodatkowy odstęp od góry dużego okna (px) – gdy pasek kompaktowy leży w linii paska statusu. */
    var padTopPx by mutableIntStateOf(0)

    var settings by mutableStateOf(BarSettings())

    /** Lokalne odgadnięcie stanu, gdy odtwarzacz nie zdradza go w nazwie akcji (przełączane przy kliknięciu). */
    /** Kolor akcentu z okładki trzymany tutaj, żeby przetrwał ponowne utworzenie okna (bez mignięcia kolorem). */
    var accent by mutableStateOf<Color?>(null)

    var shuffleGuess by mutableStateOf(false)
    var repeatGuess by mutableStateOf(RepeatMode.OFF)
}

/** Akcje wywoływane z interfejsu paska. */
interface BarActions {
    fun toggleExpanded()
    fun hide()
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun openApp()
    fun toggleShuffle()
    fun toggleRepeat()

    /** Obszar paska postępu (px, względem okna) – przeciąganie okna nie może się z nim kłócić. */
    fun setSeekBounds(bounds: Rect?)
}
