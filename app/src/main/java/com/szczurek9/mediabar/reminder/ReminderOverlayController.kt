package com.szczurek9.mediabar.reminder

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.PixelFormat
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.szczurek9.mediabar.integration.ReminderCard
import com.szczurek9.mediabar.integration.ReminderProtocol
import com.szczurek9.mediabar.overlay.DragContainer
import com.szczurek9.mediabar.settings.Anchor
import com.szczurek9.mediabar.settings.BarForm
import com.szczurek9.mediabar.settings.BarSettings
import com.szczurek9.mediabar.settings.PreferencesRepository
import com.szczurek9.mediabar.settings.VAnchor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

/** Stan UI paska przypomnień obserwowany przez Compose. */
class ReminderUiState {
    var visible by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    var card by mutableStateOf<ReminderCard?>(null)
    var settings by mutableStateOf(BarSettings())
    var screenWidthPx by mutableIntStateOf(0)
    var sideMarginPx by mutableIntStateOf(0)

    /** Okno ma już stały, duży rozmiar (na czas rozwinięcia); animacja dzieje się wyłącznie wewnątrz Compose. */
    var windowLarge by mutableStateOf(false)
    /** Odstęp (px) lewej krawędzi paska kompaktowego od lewej krawędzi dużego okna. */
    var compactOffsetX by mutableIntStateOf(0)
    /** Dodatkowy odstęp od góry (px) przy formie „w pasku statusu”, żeby karta zeszła pod pasek. */
    var padTopPx by mutableIntStateOf(0)
}

interface ReminderActions {
    fun expand()
    fun dismiss()
    fun done()
    fun snooze(index: Int)
    fun open()
}

/**
 * Osobne okno nakładki dla przypomnień ze Szczur Reminder. Używa tej samej palety, ustawień (rozmiar, motyw,
 * przezroczystość, pozycja) i klasy DragContainer co pasek muzyczny, ale nie dotyka jego okna ani logiki sesji.
 * Na czas pokazywania przypomnienia pasek muzyczny jest chowany (callback [onActiveChanged]).
 * Żyje w MusicNotificationListener (główny wątek).
 */
class ReminderOverlayController(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val onActiveChanged: (Boolean) -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner, ReminderActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    private val ui = ReminderUiState()

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
        title = "szczurReminderBar"
    }

    @Volatile
    private var settings = BarSettings()
    private var attached = false
    private var timerJob: Job? = null
    private var removeJob: Job? = null
    private var largeReady: CompletableDeferred<Unit>? = null

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)
        composeView.setContent { ReminderBarRoot(ui, this@ReminderOverlayController) }
        container.addView(
            composeView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        container.dragEnabled = { false }
        // Dotknięcie poza rozwiniętą kartą zamyka pasek; przypomnienie zostaje w centrum powiadomień.
        container.onOutsideTouch = { if (ui.expanded) dismiss() }
        // Duże okno jest „gotowe”, gdy system faktycznie je powiększył – dopiero wtedy startuje animacja.
        container.addOnLayoutChangeListener { _: View, l, t, r, b, _, _, _, _ ->
            if (ui.windowLarge && (r - l) >= ui.screenWidthPx - 2 * ui.sideMarginPx - 2 && (b - t) > 0) {
                largeReady?.complete(Unit)
            }
        }
    }

    fun start() {
        scope.launch {
            prefs.settings.collect {
                settings = it
                ui.settings = it
                if (attached) applyLayout()
            }
        }
    }

    fun destroy() {
        scope.cancel()
        removeWindow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        onActiveChanged(false)
    }

    /** null = można pokazać; w przeciwnym razie powód (wysyłany do Reminder, który wtedy pokazuje zwykłe powiadomienie). */
    fun unavailableReason(): String? {
        if (!settings.acceptReminders) return "disabled_in_media_bar"
        if (!Settings.canDrawOverlays(context)) return "no_overlay_permission"
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return "locked"
        if (!context.getSystemService(PowerManager::class.java).isInteractive) return "screen_off"
        return null
    }

    /** Wywoływane na głównym wątku przez ReminderBridgeService. */
    fun show(card: ReminderCard, onResult: (status: Int, reason: String?) -> Unit) {
        val reason = unavailableReason()
        if (reason != null) {
            onResult(ReminderProtocol.STATUS_UNAVAILABLE, reason)
            return
        }
        removeJob?.cancel()
        timerJob?.cancel()
        if (attached && (ui.windowLarge || ui.expanded)) removeWindow() // poprzednia karta była rozwinięta
        resetWindowState()
        ui.card = card
        if (!attached) {
            applyLayoutParams()
            try {
                windowManager.addView(container, params)
                attached = true
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się dodać okna przypomnienia", e)
                ui.card = null
                onResult(ReminderProtocol.STATUS_UNAVAILABLE, "window_add_failed")
                return
            }
        } else {
            applyLayout()
        }
        ui.visible = true
        onActiveChanged(true)
        startTimer(card.displayMs)
        onResult(ReminderProtocol.STATUS_SHOWN, null)
    }

    fun hide(id: Long) {
        if (ui.card?.id == id) dismiss()
    }

    // ---- ReminderActions ----

    /**
     * Rozwijanie: najpierw okno dostaje stały, duży rozmiar (jednorazowa zmiana w WindowManager), potem – gdy system
     * ją wykona – startuje animacja w Compose. Dzięki temu w trakcie animacji okno nie zmienia już rozmiaru.
     */
    override fun expand() {
        if (ui.expanded || ui.windowLarge || ui.card == null || !attached) return
        timerJob?.cancel() // rozwiniętą kartę zamyka użytkownik
        val sw = windowManager.currentWindowMetrics.bounds.width()
        val side = dp(12)
        val (cw, _) = compactSize()
        val screenLeft = when (settings.anchor) {
            Anchor.CENTER -> (sw - cw) / 2 + params.x
            Anchor.LEFT -> params.x
            Anchor.RIGHT -> sw - cw - params.x
        }
        ui.screenWidthPx = sw
        ui.sideMarginPx = side
        ui.compactOffsetX = screenLeft - side
        ui.padTopPx = if (settings.vAnchor == VAnchor.TOP && settings.inStatusBar) {
            max(0, topInset() + dp(4) - params.y)
        } else 0
        largeReady = CompletableDeferred()
        ui.windowLarge = true
        applyLayout()
        scope.launch {
            withTimeoutOrNull(250L) { largeReady?.await() }
            delay(16)
            ui.expanded = true
        }
    }

    override fun dismiss() {
        timerJob?.cancel()
        if (!attached || !ui.visible) return
        ui.visible = false // stan "rozwinięty" zostaje do usunięcia okna – karta znika tak, jak stała
        onActiveChanged(false)
        removeJob?.cancel()
        removeJob = scope.launch {
            delay(if (settings.animations) 300L else 0L)
            removeWindow()
            resetWindowState()
            ui.card = null
        }
    }

    override fun done() {
        val card = ui.card ?: return
        send(card.done)
        dismiss()
    }

    override fun snooze(index: Int) {
        val card = ui.card ?: return
        send(card.snoozes.getOrNull(index)?.intent)
        dismiss()
    }

    override fun open() {
        val card = ui.card ?: return
        send(card.open)
        dismiss()
    }

    private fun send(pi: PendingIntent?) {
        if (pi == null) return
        try {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            pi.send(context, 0, null, null, null, null, options.toBundle())
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "PendingIntent anulowany", e)
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się wysłać PendingIntent", e)
        }
    }

    private fun startTimer(ms: Long) {
        timerJob = scope.launch {
            delay(ms)
            // Po czasie pasek tylko znika – przypomnienie nie jest oznaczane jako wykonane ani usuwane.
            dismiss()
        }
    }

    // ---- Okno ----

    private fun resetWindowState() {
        ui.expanded = false
        ui.windowLarge = false
        ui.compactOffsetX = 0
        ui.padTopPx = 0
    }

    private fun removeWindow() {
        if (!attached) return
        try {
            windowManager.removeViewImmediate(container)
        } catch (e: Exception) {
            Log.w(TAG, "removeView", e)
        }
        attached = false
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

    private fun density(): Float = context.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density()).roundToInt()

    private fun inset(type: Int): android.graphics.Insets =
        windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(type)

    private fun topInset(): Int = inset(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
    private fun bottomBase(): Int = inset(WindowInsets.Type.navigationBars()).bottom + dp(8)

    private fun compactSize(): Pair<Int, Int> {
        val scale = settings.scalePercent / 100f
        val (w, h) = if (settings.form == BarForm.SLIM) {
            ReminderSize.SLIM_W to ReminderSize.SLIM_H
        } else {
            ReminderSize.STANDARD_W to ReminderSize.STANDARD_H
        }
        return (w * density() * scale).roundToInt() to (h * density() * scale).roundToInt()
    }

    private fun applyLayoutParams() {
        val bounds = windowManager.currentWindowMetrics.bounds
        val sw = bounds.width()
        val sh = bounds.height()
        val side = dp(12)
        ui.screenWidthPx = sw
        ui.sideMarginPx = side

        val s = settings
        val large = ui.windowLarge
        val (cw, ch) = compactSize()
        val w = if (large) max(0, sw - 2 * side) else cw
        params.width = if (large) w else WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT

        val free = max(0, sw - w)
        val margin = dp(8)
        val hGravity: Int
        if (large) {
            hGravity = Gravity.CENTER_HORIZONTAL
            params.x = 0
        } else when (s.anchor) {
            Anchor.CENTER -> {
                hGravity = Gravity.CENTER_HORIZONTAL
                params.x = s.offsetX.coerceIn(-free / 2, free / 2)
            }
            Anchor.LEFT -> {
                hGravity = Gravity.START
                params.x = (margin + s.offsetX).coerceIn(0, free)
            }
            Anchor.RIGHT -> {
                hGravity = Gravity.END
                params.x = (margin - s.offsetX).coerceIn(0, free)
            }
        }

        // Pionowo zawsze liczymy po rozmiarze kompaktowym – duże okno zaczyna dokładnie tam, gdzie był pasek.
        val room = max(0, sh - ch)
        val vGravity: Int
        when (s.vAnchor) {
            VAnchor.TOP -> {
                vGravity = Gravity.TOP
                val base = if (s.inStatusBar) max(0, (topInset() - ch) / 2) else topInset() + dp(4)
                params.y = (base + s.offsetY).coerceIn(0, room)
            }
            VAnchor.MIDDLE -> {
                vGravity = Gravity.CENTER_VERTICAL
                params.y = s.offsetY.coerceIn(-room / 2, room / 2)
            }
            VAnchor.BOTTOM -> {
                vGravity = Gravity.BOTTOM
                params.y = (bottomBase() - s.offsetY).coerceIn(0, room)
            }
        }
        params.gravity = hGravity or vGravity
    }

    private companion object {
        const val TAG = "ReminderOverlay"
    }
}
