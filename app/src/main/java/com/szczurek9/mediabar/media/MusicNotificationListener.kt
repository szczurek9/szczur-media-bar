package com.szczurek9.mediabar.media

import android.content.res.Configuration
import android.service.notification.NotificationListenerService
import com.szczurek9.mediabar.MusicBarApp
import com.szczurek9.mediabar.overlay.MusicOverlayController

/**
 * Serwis nasłuchu powiadomień – system przyznaje mu dostęp do MediaSessionManager.getActiveSessions().
 * Utrzymuje też nakładkę: żyje dokładnie tak długo, jak Notification Access jest przyznany,
 * a po restarcie telefonu system podpina go automatycznie.
 */
class MusicNotificationListener : NotificationListenerService() {

    private var controller: MusicOverlayController? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        if (controller != null) return
        val app = application as MusicBarApp
        val c = MusicOverlayController(this, app.preferences)
        controller = c
        app.overlayController = c
        c.start()
    }

    override fun onListenerDisconnected() {
        teardown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    private fun teardown() {
        isConnected = false
        controller?.destroy()
        controller = null
        (application as? MusicBarApp)?.overlayController = null
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
