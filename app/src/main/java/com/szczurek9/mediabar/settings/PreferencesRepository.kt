package com.szczurek9.mediabar.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.musicBarStore: DataStore<Preferences> by preferencesDataStore(name = "music_bar_settings")

object SettingKeys {
    val ENABLED = booleanPreferencesKey("enabled")
    val START_EXPANDED = booleanPreferencesKey("start_expanded")
    val SCALE = intPreferencesKey("scale_percent")
    val OPACITY = intPreferencesKey("opacity_percent")
    val THEME = stringPreferencesKey("theme")
    val ANIMATIONS = booleanPreferencesKey("animations")
    val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
    val FORM = stringPreferencesKey("form")
    val ANCHOR = stringPreferencesKey("anchor")
    val V_ANCHOR = stringPreferencesKey("v_anchor")
    val LOCK = booleanPreferencesKey("lock_position")
    val IN_STATUS_BAR = booleanPreferencesKey("in_status_bar")
    val HIDE_IN_PLAYER = booleanPreferencesKey("hide_in_player")
    val OFFSET_X = intPreferencesKey("offset_x")
    val OFFSET_Y = intPreferencesKey("offset_y")
    val AUTO_SHOW = booleanPreferencesKey("auto_show")
    val AFTER_STOP = stringPreferencesKey("after_stop")
    val HIDE_DELAY = intPreferencesKey("hide_delay_sec")
    val ACCEPT_REMINDERS = booleanPreferencesKey("accept_reminders")
}

class PreferencesRepository(context: Context) {

    private val store = context.applicationContext.musicBarStore

    val settings: Flow<BarSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    suspend fun setPosition(x: Int, y: Int) {
        store.edit {
            it[SettingKeys.OFFSET_X] = x
            it[SettingKeys.OFFSET_Y] = y
        }
    }

    /** Wybiera pole siatki pozycji i zeruje przesunięcia (pasek staje dokładnie w tym polu). */
    suspend fun setAnchors(anchor: Anchor, vAnchor: VAnchor) {
        store.edit {
            it[SettingKeys.ANCHOR] = anchor.name
            it[SettingKeys.V_ANCHOR] = vAnchor.name
            it[SettingKeys.OFFSET_X] = 0
            it[SettingKeys.OFFSET_Y] = 0
        }
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

private fun Preferences.toSettings(): BarSettings {
    val d = BarSettings()
    return BarSettings(
        enabled = this[SettingKeys.ENABLED] ?: d.enabled,
        form = enumOr(this[SettingKeys.FORM], d.form),
        startExpanded = this[SettingKeys.START_EXPANDED] ?: d.startExpanded,
        scalePercent = (this[SettingKeys.SCALE] ?: d.scalePercent).coerceIn(80, 130),
        opacityPercent = (this[SettingKeys.OPACITY] ?: d.opacityPercent).coerceIn(40, 100),
        theme = enumOr(this[SettingKeys.THEME], d.theme),
        animations = this[SettingKeys.ANIMATIONS] ?: d.animations,
        dynamicColors = this[SettingKeys.DYNAMIC_COLORS] ?: d.dynamicColors,
        anchor = enumOr(this[SettingKeys.ANCHOR], d.anchor),
        vAnchor = enumOr(this[SettingKeys.V_ANCHOR], d.vAnchor),
        lockPosition = this[SettingKeys.LOCK] ?: d.lockPosition,
        inStatusBar = this[SettingKeys.IN_STATUS_BAR] ?: d.inStatusBar,
        hideInPlayerApp = this[SettingKeys.HIDE_IN_PLAYER] ?: d.hideInPlayerApp,
        offsetX = this[SettingKeys.OFFSET_X] ?: d.offsetX,
        offsetY = this[SettingKeys.OFFSET_Y] ?: d.offsetY,
        autoShow = this[SettingKeys.AUTO_SHOW] ?: d.autoShow,
        afterStop = enumOr(this[SettingKeys.AFTER_STOP], d.afterStop),
        hideDelaySec = (this[SettingKeys.HIDE_DELAY] ?: d.hideDelaySec).coerceIn(5, 120),
        acceptReminders = this[SettingKeys.ACCEPT_REMINDERS] ?: d.acceptReminders,
    )
}
