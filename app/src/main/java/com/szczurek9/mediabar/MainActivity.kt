package com.szczurek9.mediabar

import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.szczurek9.mediabar.media.MusicNotificationListener
import com.szczurek9.mediabar.permissions.PermissionManager
import com.szczurek9.mediabar.settings.MusicBarSettingsRoot

class MainActivity : ComponentActivity() {

    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MusicBarApp
        setContent { MusicBarSettingsRoot(app.preferences, refreshTick) }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
        // Dostęp przyznany, ale serwis nie jest połączony (np. zabity przez system) -> poproś o ponowne podpięcie.
        val status = PermissionManager.check(this)
        if (status.notificationAccess && !MusicNotificationListener.isConnected) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(this, MusicNotificationListener::class.java))
            }
        }
    }
}
