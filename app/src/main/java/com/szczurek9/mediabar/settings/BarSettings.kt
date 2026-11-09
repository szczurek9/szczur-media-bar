package com.szczurek9.mediabar.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Do której krawędzi poziomo "kotwiczy się" panel. */
enum class Anchor { LEFT, CENTER, RIGHT }

/** Do której krawędzi pionowo kotwiczy się panel (kierunek rozwijania: TOP w dół, BOTTOM w górę, MIDDLE w obie strony). */
enum class VAnchor { TOP, MIDDLE, BOTTOM }

enum class AfterStop { KEEP, HIDE_AFTER_DELAY, HIDE_NOW }

/** Forma widoku kompaktowego. */
enum class BarForm { STANDARD, SLIM, BUBBLE }

data class BarSettings(
    val enabled: Boolean = true,
    val form: BarForm = BarForm.STANDARD,
    val startExpanded: Boolean = false,
    val scalePercent: Int = 100,
    val opacityPercent: Int = 88,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val animations: Boolean = true,
    val dynamicColors: Boolean = true,
    val anchor: Anchor = Anchor.CENTER,
    val vAnchor: VAnchor = VAnchor.TOP,
    /** Przesunięcie poziome w px względem kotwicy (dodatnie = w prawo). */
    val offsetX: Int = 0,
    /** Przesunięcie pionowe w px względem kotwicy (dodatnie = w dół). */
    val offsetY: Int = 0,
    /** Blokada przeciągania: pasek stoi na wybranym polu siatki. */
    val lockPosition: Boolean = false,
    /** Góra ekranu: pastylka w linii paska statusu (najlepiej z formą Slim). */
    val inStatusBar: Boolean = false,
    val autoShow: Boolean = true,
    val afterStop: AfterStop = AfterStop.HIDE_AFTER_DELAY,
    val hideDelaySec: Int = 20,
    /** Ukrywaj pasek, gdy na ekranie jest aplikacja, z której gra muzyka (wymaga dostępu do danych o użyciu). */
    val hideInPlayerApp: Boolean = true,
    /** Przyjmuj przypomnienia ze Szczur Reminder (niezależnie od głównego przełącznika paska muzycznego). */
    val acceptReminders: Boolean = true,
)
