package com.szczurek9.mediabar.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.szczurek9.mediabar.foreground.ForegroundAppDetector
import com.szczurek9.mediabar.media.MusicSessionManager
import com.szczurek9.mediabar.media.NowPlaying
import com.szczurek9.mediabar.media.RepeatMode
import com.szczurek9.mediabar.permissions.PermissionManager
import com.szczurek9.mediabar.settings.AfterStop
import com.szczurek9.mediabar.settings.Anchor
import com.szczurek9.mediabar.settings.BarForm
import com.szczurek9.mediabar.settings.BarSettings
import com.szczurek9.mediabar.settings.PreferencesRepository
import com.szczurek9.mediabar.settings.SettingKeys
import com.szczurek9.mediabar.settings.VAnchor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Zarządza oknem nakładki (WindowManager, TYPE_APPLICATION_OVERLAY) i jego widocznością.
 * W formie kompaktowej okno ma rozmiar WRAP_CONTENT (nie przechwytuje dotyku poza panelem),
 * na czas rozwinięcia dostaje stały, duży rozmiar. Żyje w MusicNotificationListener (główny wątek).
 */
class MusicOverlayController(
    private val context: Context,
    private val prefs: PreferencesRepository,
) : LifecycleOwner, SavedStateRegistryOwner, BarActions {

    // ---- Lifecycle potrzebny Compose'owi poza Activity ----
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private val sessions = MusicSessionManager(context)
    private val detector = ForegroundAppDetector(context)
    private val ui = OverlayUiState()

    /** Dla ekranu diagnostycznego w ustawieniach. */
    val nowPlaying: StateFlow<NowPlaying> get() = sessions.nowPlaying

    private val container = DragContainer(context)
    private val composeView = ComposeView(context)
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        title = "szczurMediaBar"
    }

    private var attached = false
    private var hiddenByUser = false
    private var pauseExpired = false
    private var pauseJob: Job? = null
    private var removeJob: Job? = null
    private var collapseJob: Job? = null

    private var np = NowPlaying()
    private var settings = BarSettings()
    private var settingsLoaded = false
    private var foregroundPkg: String? = null

    private var offsetX = 0
    private var offsetY = 0
    private var dragging = false
    private var dragStartX = 0
    private var dragStartY = 0
    /** Kończy się, gdy system faktycznie powiększył okno (przed startem animacji rozwijania). */
    private var largeReady: CompletableDeferred<Unit>? = null

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        composeView.setContent { MusicBarRoot(ui, sessions.nowPlaying, this@MusicOverlayController) }
        container.addView(
            composeView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )

        container.dragEnabled = { !settings.lockPosition && !ui.expanded && !ui.windowLarge }
        container.onOutsideTouch = { if (ui.expanded) setExpanded(false) }
        container.onDragStart = {
            dragging = true
            dragStartX = offsetX
            dragStartY = offsetY
        }
        container.onDrag = { dx, dy -> onDrag(dx, dy) }
        container.onDragEnd = {
            dragging = false
            scope.launch { prefs.setPosition(offsetX, offsetY) }
        }
        // Rozmiar formy kompaktowej liczymy deterministycznie (patrz compactWidthPx), więc listener nie
        // aktualizuje już pozycji w każdej klatce animacji. Służy tylko do wykrycia, że duże okno jest gotowe.
        container.addOnLayoutChangeListener { _: View, l, t, r, b, _, _, _, _ ->
            val w = r - l
            if (ui.windowLarge && w >= ui.screenWidthPx - 2 * ui.sideMarginPx - 2 && (b - t) > 0) {
                largeReady?.complete(Unit)
            }
        }
    }

    // ---- Cykl życia ----

    fun start() {
        sessions.start()
        scope.launch { prefs.settings.collect { onSettings(it) } }
        scope.launch { sessions.nowPlaying.collect { onNowPlaying(it) } }
        scope.launch { pollForegroundApp() }
    }

    fun destroy() {
        scope.cancel()
        sessions.stop()
        removeWindow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    fun onConfigurationChanged() {
        container.post { applyLayout() }
    }

    // ---- Dla kafelka i ekranu ustawień ----

    val isEnabledAndShownToUser: Boolean get() = settings.enabled && !hiddenByUser

    fun unhide() {
        hiddenByUser = false
        evaluate()
    }

    fun toggleUserVisibility() {
        if (!settings.enabled) {
            hiddenByUser = false
            scope.launch { prefs.put(SettingKeys.ENABLED, true) }
            return
        }
        if (hiddenByUser) unhide() else hide()
    }

    // ---- BarActions ----

    override fun toggleExpanded() {
        setExpanded(!ui.expanded)
    }

    /**
     * Okno WindowManager zmienia rozmiar tylko w punktach granicznych, a sama animacja dzieje się wewnątrz Compose.
     * Rozwijanie: najpierw duże okno, chwilę później animacja. Zwijanie: animacja, potem małe okno.
     */
    private fun setExpanded(expand: Boolean) {
        collapseJob?.cancel()
        container.animate().cancel()
        container.alpha = 1f
        if (expand) {
            val needPrepare = !ui.windowLarge
            if (needPrepare) {
                largeReady = CompletableDeferred()
                prepareLarge()
            }
            collapseJob = scope.launch {
                if (needPrepare) {
                    // Czekamy aż system naprawdę powiększy okno (zamiast stałych 60 ms), inaczej pierwsze
                    // klatki animacji nakładają się na zmianę rozmiaru okna i się tną.
                    withTimeoutOrNull(250L) { largeReady?.await() }
                    delay(16)
                }
                ui.expanded = true
            }
        } else {
            // Zwijanie bez animacji: zdejmujemy duże okno i od razu dodajemy kompaktowe w docelowym miejscu.
            // Nowe okno startuje od zera (Compose bez stanu "rozwinięty"), więc nie ma czego animować ani przesuwać.
            container.dragExclusion = null
            ui.expanded = false
            if (ui.windowLarge) {
                ui.windowLarge = false
                ui.compactOffsetX = 0
                ui.padTopPx = 0
                recreateWindow()
            } else {
                applyLayout()
            }
        }
    }

    private fun recreateWindow() {
        if (!attached) return
        try {
            windowManager.removeViewImmediate(container)
        } catch (e: Exception) {
            Log.w(TAG, "removeView (recreate)", e)
        }
        container.dragExclusion = null
        applyLayoutParams()
        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się dodać nakładki (recreate)", e)
            attached = false
        }
    }

    /** Przygotowuje duże okno: zapamiętuje, gdzie na ekranie leży pasek kompaktowy, żeby nie "skoczył". */
    private fun prepareLarge() {
        val sw = windowManager.currentWindowMetrics.bounds.width()
        val w = compactWidthPx()
        val side = sideMarginPx()
        val screenLeft = when (settings.anchor) {
            Anchor.CENTER -> (sw - w) / 2 + params.x
            Anchor.LEFT -> params.x
            Anchor.RIGHT -> sw - w - params.x
        }
        ui.screenWidthPx = sw
        ui.sideMarginPx = side
        ui.compactOffsetX = if (w > 0) screenLeft - side else 0
        ui.padTopPx = if (settings.vAnchor == VAnchor.TOP && settings.inStatusBar) {
            max(0, defaultTop() - compactTopY())
        } else 0
        ui.windowLarge = true
        applyLayout()
    }

    override fun hide() {
        hiddenByUser = true
        evaluate()
    }

    override fun playPause() {
        sessions.playPause()
    }

    override fun next() {
        sessions.next()
    }

    override fun previous() {
        sessions.previous()
    }

    override fun seekTo(positionMs: Long) {
        sessions.seekTo(positionMs)
    }

    override fun openApp() {
        if (sessions.openPlayerApp()) setExpanded(false)
    }

    override fun toggleShuffle() {
        val action = np.shuffleAction ?: return
        if (np.shuffleOn == null) ui.shuffleGuess = !ui.shuffleGuess
        sessions.sendCustomAction(action)
    }

    override fun toggleRepeat() {
        val action = np.repeatAction ?: return
        if (np.repeatMode == null) {
            ui.repeatGuess = when (ui.repeatGuess) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        }
        sessions.sendCustomAction(action)
    }

    override fun setSeekBounds(bounds: Rect?) {
        container.dragExclusion = bounds
    }

    // ---- Reakcja na dane ----

    private fun onSettings(s: BarSettings) {
        val prev = settings
        val first = !settingsLoaded
        settings = s
        settingsLoaded = true
        ui.settings = s
        if (!dragging) {
            offsetX = s.offsetX
            offsetY = s.offsetY
        }
        if (!first && prev.startExpanded != s.startExpanded) setExpanded(s.startExpanded)
        applyLayout()
        evaluate()
    }

    private fun onNowPlaying(new: NowPlaying) {
        val prev = np
        np = new
        if (new.packageName != prev.packageName) {
            ui.shuffleGuess = false
            ui.repeatGuess = RepeatMode.OFF
        }

        val started = new.isPlaying && !prev.isPlaying
        val trackChanged = new.hasSession && new.trackKey != prev.trackKey
        if (settings.autoShow && (started || trackChanged)) hiddenByUser = false

        if (new.isPlaying || !new.hasSession) {
            pauseJob?.cancel()
            pauseJob = null
            pauseExpired = false
        } else if (pauseJob == null && !pauseExpired) {
            pauseJob = scope.launch {
                delay(settings.hideDelaySec * 1000L)
                pauseExpired = true
                pauseJob = null
                evaluate()
            }
        }
        if (settingsLoaded) evaluate()
    }

    /** Odpytuje aplikację na pierwszym planie tylko, gdy jest sesja muzyczna i włączone auto-ukrywanie. */
    private suspend fun pollForegroundApp() {
        while (true) {
            val active = settings.hideInPlayerApp && np.hasSession &&
                PermissionManager.hasUsageAccess(context)
            if (active) {
                val pkg = detector.poll()
                if (pkg != foregroundPkg) {
                    foregroundPkg = pkg
                    evaluate()
                }
                delay(1500)
            } else {
                if (foregroundPkg != null) {
                    foregroundPkg = null
                    evaluate()
                }
                detector.reset()
                delay(4000)
            }
        }
    }

    /** Jedno miejsce, które decyduje, czy pasek ma być widoczny. */
    private fun evaluate() {
        if (!settingsLoaded) return
        val s = settings
        val inPlayerApp = s.hideInPlayerApp && np.packageName != null && foregroundPkg == np.packageName
        val allowed = s.enabled && np.hasSession && !hiddenByUser && !inPlayerApp && when {
            np.isPlaying -> true
            else -> when (s.afterStop) {
                AfterStop.KEEP -> true
                AfterStop.HIDE_NOW -> false
                AfterStop.HIDE_AFTER_DELAY -> !pauseExpired
            }
        }
        if (allowed) showWindow() else hideWindow()
    }

    // ---- Okno ----

    private fun showWindow() {
        removeJob?.cancel()
        if (!attached) {
            if (!Settings.canDrawOverlays(context)) return
            collapseJob?.cancel()
            ui.expanded = settings.startExpanded
            if (ui.expanded) {
                prepareLarge()
            } else {
                ui.windowLarge = false
                ui.compactOffsetX = 0
                ui.padTopPx = 0
            }
            applyLayoutParams()
            try {
                windowManager.addView(container, params)
                attached = true
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się dodać nakładki", e)
                return
            }
        }
        ui.visible = true
    }

    private fun hideWindow() {
        ui.visible = false
        if (!attached) return
        removeJob?.cancel()
        removeJob = scope.launch {
            delay(if (settings.animations) 350L else 0L)
            removeWindow()
        }
    }

    private fun removeWindow() {
        if (!attached) return
        try {
            windowManager.removeViewImmediate(container)
        } catch (e: Exception) {
            Log.w(TAG, "removeView", e)
        }
        attached = false
        container.dragExclusion = null
    }

    // ---- Pozycjonowanie ----

    private fun inset(type: Int): android.graphics.Insets =
        windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(type)

    private fun topInset(): Int = inset(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top

    private fun density(): Float = context.resources.displayMetrics.density

    private fun marginPx(): Int = (8 * density()).roundToInt()

    private fun sideMarginPx(): Int = (12 * density()).roundToInt()

    private fun defaultTop(): Int = topInset() + (4 * density()).roundToInt()

    /** Górna krawędź paska kompaktowego (przed doliczeniem przesunięcia): pod paskiem statusu albo w jego linii. */
    private fun compactTopY(): Int =
        if (settings.inStatusBar) max(0, (topInset() - compactHeightPx()) / 2) else defaultTop()

    private fun bottomBase(): Int = inset(WindowInsets.Type.navigationBars()).bottom + marginPx()

    private fun compactWidthDp(): Int = when (settings.form) {
        BarForm.STANDARD -> CompactSize.STANDARD_W
        BarForm.SLIM -> CompactSize.SLIM_W
        BarForm.BUBBLE -> CompactSize.BUBBLE_W
    }

    private fun compactHeightDp(): Int = when (settings.form) {
        BarForm.STANDARD -> CompactSize.STANDARD_H
        BarForm.SLIM -> CompactSize.SLIM_H
        BarForm.BUBBLE -> CompactSize.BUBBLE_H
    }

    /** Docelowy rozmiar paska kompaktowego w px – stały dla danej formy i skali, niezależny od chwilowego pomiaru. */
    private fun compactWidthPx(): Int = (compactWidthDp() * density() * settings.scalePercent / 100f).roundToInt()

    private fun compactHeightPx(): Int = (compactHeightDp() * density() * settings.scalePercent / 100f).roundToInt()

    private fun largeHeightPx(): Int =
        (EXPANDED_HEIGHT_DP * density() * settings.scalePercent / 100f).roundToInt()

    private fun applyLayoutParams() {
        val bounds = windowManager.currentWindowMetrics.bounds
        val sw = bounds.width()
        val sh = bounds.height()
        val large = ui.windowLarge
        val side = sideMarginPx()
        ui.screenWidthPx = sw
        ui.sideMarginPx = side

        // Uwaga: dla formy kompaktowej NIE używamy container.width/height – przy zwijaniu to wciąż rozmiar
        // dużego okna, przez co przesunięcie było chwilowo ucinane (pasek skakał w bok i wracał).
        val w = if (large) max(0, sw - 2 * side) else compactWidthPx()
        val h = if (large) largeHeightPx() + ui.padTopPx else compactHeightPx()
        params.width = if (large) w else WindowManager.LayoutParams.WRAP_CONTENT
        params.height = if (large) h else WindowManager.LayoutParams.WRAP_CONTENT

        val free = max(0, sw - w)
        val margin = marginPx()
        val hGravity: Int
        if (large) {
            hGravity = Gravity.CENTER_HORIZONTAL
            params.x = 0
        } else {
            when (settings.anchor) {
                Anchor.CENTER -> {
                    hGravity = Gravity.CENTER_HORIZONTAL
                    params.x = offsetX.coerceIn(-free / 2, free / 2)
                }
                Anchor.LEFT -> {
                    hGravity = Gravity.START
                    params.x = (margin + offsetX).coerceIn(0, free)
                }
                Anchor.RIGHT -> {
                    hGravity = Gravity.END
                    params.x = (margin - offsetX).coerceIn(0, free)
                }
            }
        }

        val room = max(0, sh - h)
        val vGravity: Int
        when (settings.vAnchor) {
            VAnchor.TOP -> {
                vGravity = Gravity.TOP
                params.y = (compactTopY() + offsetY).coerceIn(0, room)
            }
            VAnchor.MIDDLE -> {
                vGravity = Gravity.CENTER_VERTICAL
                params.y = offsetY.coerceIn(-room / 2, room / 2)
            }
            VAnchor.BOTTOM -> {
                vGravity = Gravity.BOTTOM
                params.y = (bottomBase() - offsetY).coerceIn(0, room)
            }
        }
        params.gravity = hGravity or vGravity
    }

    private fun applyLayout() {
        if (!attached) return
        applyLayoutParams()
        try {
            windowManager.updateViewLayout(container, params)
        } catch (e: Exception) {
            Log.w(TAG, "updateViewLayout", e)
        }
    }

    private fun onDrag(dx: Float, dy: Float) {
        if (settings.lockPosition || ui.expanded || ui.windowLarge) return
        val bounds = windowManager.currentWindowMetrics.bounds
        val sw = bounds.width()
        val sh = bounds.height()
        val free = max(0, sw - container.width)
        val room = max(0, sh - container.height)
        val margin = marginPx()

        val nx = dragStartX + dx.roundToInt()
        val ny = dragStartY + dy.roundToInt()

        offsetX = when (settings.anchor) {
            Anchor.CENTER -> nx.coerceIn(-free / 2, free / 2)
            Anchor.LEFT -> nx.coerceIn(-margin, free - margin)
            Anchor.RIGHT -> nx.coerceIn(margin - free, margin)
        }
        offsetY = when (settings.vAnchor) {
            VAnchor.TOP -> {
                val base = compactTopY()
                ny.coerceIn(-base, max(-base, room - base))
            }
            VAnchor.MIDDLE -> ny.coerceIn(-room / 2, room / 2)
            VAnchor.BOTTOM -> {
                val base = bottomBase()
                ny.coerceIn(base - room, base)
            }
        }
        applyLayout()
    }

    private companion object {
        const val TAG = "MusicOverlay"
    }
}
