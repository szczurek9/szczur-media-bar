package com.szczurek9.mediabar.permissions

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.szczurek9.mediabar.media.MusicNotificationListener

data class PermissionStatus(
    val overlay: Boolean,
    val notificationAccess: Boolean,
    val batteryUnrestricted: Boolean,
    val listenerConnected: Boolean,
    val usageAccess: Boolean,
) {
    /** Minimum do działania paska. */
    val ready: Boolean get() = overlay && notificationAccess
}

object PermissionManager {

    fun check(context: Context): PermissionStatus {
        val pm = context.getSystemService(PowerManager::class.java)
        return PermissionStatus(
            overlay = Settings.canDrawOverlays(context),
            notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
            listenerConnected = MusicNotificationListener.isConnected,
            usageAccess = hasUsageAccess(context),
        )
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun notificationAccessIntent(context: Context): Intent {
        val cn = ComponentName(context, MusicNotificationListener::class.java)
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
    }

    fun notificationAccessListIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Tu jest "Zezwól na ustawienia z ograniczeniami" (menu ⋮) oraz Bateria -> Bez ograniczeń. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun batteryIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            try {
                val fallback = if (intent.action == Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS) {
                    notificationAccessListIntent()
                } else {
                    appDetailsIntent(context)
                }
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                // Nic więcej nie da się zrobić.
            }
        }
    }
}
