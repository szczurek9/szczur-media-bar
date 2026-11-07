package com.szczurek9.mediabar

import android.app.Application
import com.szczurek9.mediabar.overlay.MusicOverlayController
import com.szczurek9.mediabar.settings.PreferencesRepository

class MusicBarApp : Application() {

    val preferences: PreferencesRepository by lazy { PreferencesRepository(this) }

    /** Ustawiany przez MusicNotificationListener, dopóki serwis jest połączony. */
    @Volatile
    var overlayController: MusicOverlayController? = null
}
