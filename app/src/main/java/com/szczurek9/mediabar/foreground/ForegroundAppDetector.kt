package com.szczurek9.mediabar.foreground

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Wykrywa aplikację na pierwszym planie przez UsageStatsManager (wymaga "Dostępu do danych o użyciu").
 * Odpytywana tylko wtedy, gdy jest taka potrzeba (pasek widoczny i włączone auto-ukrywanie).
 */
class ForegroundAppDetector(private val context: Context) {

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var current: String? = null
    private var lastQuery = 0L

    fun reset() {
        current = null
        lastQuery = 0L
    }

    /** Zwraca pakiet aplikacji na pierwszym planie (ostatnie zdarzenie ACTIVITY_RESUMED). */
    fun poll(): String? {
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - 10 * 60_000L else lastQuery - 1_000L
        val events = try {
            usm.queryEvents(from, now)
        } catch (e: SecurityException) {
            null
        } ?: return current
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) current = e.packageName
        }
        lastQuery = now
        return current
    }
}
