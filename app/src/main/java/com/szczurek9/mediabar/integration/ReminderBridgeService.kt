package com.szczurek9.mediabar.integration

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import com.szczurek9.mediabar.MusicBarApp

/**
 * Wejście dla Szczur Reminder, chronione uprawnieniem signature.
 * Nie renderuje niczego sam – przekazuje kartę do ReminderOverlayController, który żyje w MusicNotificationListener.
 * Gdy listener nie jest połączony, odpowiada STATUS_UNAVAILABLE, a Reminder pokazuje zwykłe powiadomienie.
 */
class ReminderBridgeService : Service() {

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val app = application as MusicBarApp
            val controller = app.reminderController
            when (msg.what) {
                ReminderProtocol.MSG_HELLO -> {
                    val reason = controller?.unavailableReason() ?: "listener_not_connected"
                    reply(msg.replyTo, ReminderProtocol.MSG_HELLO_REPLY, Bundle().apply {
                        putInt(ReminderProtocol.K_PROTO, ReminderProtocol.PROTOCOL_VERSION)
                        putLong(ReminderProtocol.K_MB_VERSION, versionCode())
                        putBoolean(ReminderProtocol.K_CAN_SHOW, reason == null)
                        putString(ReminderProtocol.K_REASON, reason)
                    })
                }
                ReminderProtocol.MSG_SHOW -> {
                    val card = msg.data?.toReminderCard()
                    val replyTo = msg.replyTo
                    if (card == null) {
                        sendResult(replyTo, -1L, ReminderProtocol.STATUS_REJECTED, "bad_payload")
                    } else if (controller == null) {
                        sendResult(replyTo, card.id, ReminderProtocol.STATUS_UNAVAILABLE, "listener_not_connected")
                    } else {
                        controller.show(card) { status, reason -> sendResult(replyTo, card.id, status, reason) }
                    }
                }
                ReminderProtocol.MSG_HIDE -> {
                    val id = msg.data?.getLong(ReminderProtocol.K_ID, Long.MIN_VALUE) ?: Long.MIN_VALUE
                    if (id != Long.MIN_VALUE) controller?.hide(id)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun versionCode(): Long = try {
        packageManager.getPackageInfo(packageName, 0).longVersionCode
    } catch (e: Exception) {
        0L
    }

    private fun sendResult(to: Messenger?, id: Long, status: Int, reason: String?) {
        reply(to, ReminderProtocol.MSG_SHOW_RESULT, Bundle().apply {
            putLong(ReminderProtocol.K_ID, id)
            putInt(ReminderProtocol.K_STATUS, status)
            putString(ReminderProtocol.K_REASON, reason)
        })
    }

    private fun reply(to: Messenger?, what: Int, data: Bundle) {
        if (to == null) return
        try {
            to.send(Message.obtain(null, what).also { it.data = data })
        } catch (e: RemoteException) {
            Log.w(TAG, "Klient odpiął się przed odpowiedzią", e)
        }
    }

    private companion object {
        const val TAG = "ReminderBridge"
    }
}
