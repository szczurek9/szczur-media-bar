package com.szczurek9.mediabar.overlay

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Korzeń okna nakładki. Przechwytuje przeciągnięcie (po przekroczeniu progu ruchu), a zwykłe
 * dotknięcia zostawia Compose'owi. Pozycję liczy z rawX/rawY, więc ruch okna pod palcem
 * nie powoduje drgań. Przeciąganie nie startuje z obszaru paska postępu.
 */
class DragContainer(context: Context) : FrameLayout(context) {

    var dragExclusion: Rect? = null
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    /** Czy przeciąganie jest w tej chwili dozwolone (blokada pozycji, rozwinięty panel). */
    var dragEnabled: () -> Boolean = { true }

    /** Dotknięcie poza oknem (wymaga FLAG_WATCH_OUTSIDE_TOUCH) – dotknięcie nadal trafia do aplikacji pod spodem. */
    var onOutsideTouch: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var candidate = false
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return true
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                val r = dragExclusion
                candidate = dragEnabled() && (r == null || !r.contains(ev.x.toInt(), ev.y.toInt()))
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!dragging && candidate && hypot(dx, dy) > slop) {
                    dragging = true
                    onDragStart?.invoke()
                }
                if (dragging) onDrag?.invoke(dx, dy)
            }
            MotionEvent.ACTION_OUTSIDE -> onOutsideTouch?.invoke()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    onDragEnd?.invoke()
                }
            }
        }
    }
}
