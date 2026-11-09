package com.szczurek9.mediabar.integration

import android.app.PendingIntent
import android.os.Bundle

/**
 * Protokół mostu Szczur Reminder <-> Szczur Media Bar (Messenger + Bundle), wersja 1.
 * Stałe muszą być identyczne w obu aplikacjach (plik MediaBarProtocol.kt w Szczur Reminder).
 */
object ReminderProtocol {
    const val PROTOCOL_VERSION = 1

    const val PERMISSION = "com.szczurek9.mediabar.permission.REMINDER_BRIDGE"
    const val ACTION_BIND = "com.szczurek9.mediabar.action.REMINDER_BRIDGE"

    // Wiadomości (Message.what)
    const val MSG_HELLO = 1          // Reminder -> MB; odpowiedź MSG_HELLO_REPLY
    const val MSG_HELLO_REPLY = 2
    const val MSG_SHOW = 3           // Reminder -> MB; odpowiedź MSG_SHOW_RESULT
    const val MSG_SHOW_RESULT = 4
    const val MSG_HIDE = 5           // Reminder -> MB (bez odpowiedzi)

    // Klucze Bundle
    const val K_PROTO = "proto"
    const val K_MB_VERSION = "mb_version"
    const val K_CAN_SHOW = "can_show"
    const val K_REASON = "reason"
    const val K_ID = "id"
    const val K_TITLE = "title"
    const val K_DESCRIPTION = "description"
    const val K_WHEN = "when"
    const val K_DISPLAY_SEC = "display_sec"
    const val K_DONE_PI = "done_pi"
    const val K_OPEN_PI = "open_pi"
    const val K_SNOOZE_PIS = "snooze_pis"
    const val K_SNOOZE_LABELS = "snooze_labels"
    const val K_STATUS = "status"

    // Wynik MSG_SHOW_RESULT
    const val STATUS_SHOWN = 0
    const val STATUS_UNAVAILABLE = 1
    const val STATUS_REJECTED = 2

    const val MAX_TITLE = 200
    const val MAX_DESCRIPTION = 4000
    const val MAX_SNOOZES = 6
}

data class SnoozeOption(val label: String, val intent: PendingIntent)

/** Karta przypomnienia odebrana od Szczur Reminder. Akcje to PendingIntent-y Reminder – Media Bar tylko je wywołuje. */
data class ReminderCard(
    val id: Long,
    val title: String,
    val description: String,
    val whenLabel: String,
    val displayMs: Long,
    val done: PendingIntent?,
    val open: PendingIntent?,
    val snoozes: List<SnoozeOption>,
)

fun Bundle.toReminderCard(): ReminderCard? {
    val id = getLong(ReminderProtocol.K_ID, Long.MIN_VALUE)
    if (id == Long.MIN_VALUE) return null
    val title = getString(ReminderProtocol.K_TITLE)?.trim().orEmpty()
    if (title.isEmpty()) return null

    val pis = getParcelableArrayList(ReminderProtocol.K_SNOOZE_PIS, PendingIntent::class.java).orEmpty()
    val labels = getStringArrayList(ReminderProtocol.K_SNOOZE_LABELS).orEmpty()
    val snoozes = pis.zip(labels) { pi, label -> SnoozeOption(label.take(24), pi) }
        .take(ReminderProtocol.MAX_SNOOZES)

    val seconds = getInt(ReminderProtocol.K_DISPLAY_SEC, 5).coerceIn(3, 10)
    return ReminderCard(
        id = id,
        title = title.take(ReminderProtocol.MAX_TITLE),
        description = getString(ReminderProtocol.K_DESCRIPTION).orEmpty().take(ReminderProtocol.MAX_DESCRIPTION),
        whenLabel = getString(ReminderProtocol.K_WHEN).orEmpty().take(60),
        displayMs = seconds * 1000L,
        done = getParcelable(ReminderProtocol.K_DONE_PI, PendingIntent::class.java),
        open = getParcelable(ReminderProtocol.K_OPEN_PI, PendingIntent::class.java),
        snoozes = snoozes,
    )
}
