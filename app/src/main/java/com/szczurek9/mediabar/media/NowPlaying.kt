package com.szczurek9.mediabar.media

import android.graphics.Bitmap
import android.media.session.PlaybackState
import android.os.SystemClock

/** Tryb powtarzania odczytany (lub odgadnięty) z własnej akcji sesji. */
enum class RepeatMode { OFF, ALL, ONE }

/** Niezmienny snapshot tego, co aktualnie gra. Niezależny od konkretnej aplikacji muzycznej. */
data class NowPlaying(
    val hasSession: Boolean = false,
    val packageName: String? = null,
    val title: String = "",
    val artist: String = "",
    val art: Bitmap? = null,
    val durationMs: Long = 0L,
    /** Pozycja w chwili [positionUpdatedAt] (zegar elapsedRealtime). */
    val positionMs: Long = 0L,
    val positionUpdatedAt: Long = 0L,
    val speed: Float = 1f,
    val isPlaying: Boolean = false,
    val canSeek: Boolean = false,
    /** Ikona aplikacji, z której gra muzyka (forma Slim). */
    val appIcon: Bitmap? = null,
    /** Własne akcje sesji rozpoznane jako shuffle / repeat (null = aplikacja ich nie udostępnia). */
    val shuffleAction: PlaybackState.CustomAction? = null,
    val repeatAction: PlaybackState.CustomAction? = null,
    /** Stan shuffle / repeat wywnioskowany z nazwy akcji; null = nie da się ustalić (UI użyje lokalnego odgadnięcia). */
    val shuffleOn: Boolean? = null,
    val repeatMode: RepeatMode? = null,
    /** Wszystkie własne akcje sesji ("id — nazwa") – do ekranu diagnostycznego. */
    val customActionInfo: List<String> = emptyList(),
) {
    val hasDuration: Boolean get() = durationMs > 0

    /** Zmienia się tylko przy zmianie utworu (nie przy zmianie pozycji ani okładki). */
    val trackKey: String get() = "$packageName|$title|$artist"

    fun estimate(basePos: Long, baseAt: Long, now: Long = SystemClock.elapsedRealtime()): Long {
        var p = basePos
        if (isPlaying && baseAt > 0) p += ((now - baseAt) * speed).toLong()
        return if (hasDuration) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }

    fun currentPosition(now: Long = SystemClock.elapsedRealtime()): Long =
        estimate(positionMs, positionUpdatedAt, now)
}
