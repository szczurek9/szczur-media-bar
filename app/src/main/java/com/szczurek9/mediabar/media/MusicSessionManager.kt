package com.szczurek9.mediabar.media

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wykrywa aktywne sesje multimedialne (YouTube Music, Spotify, Tidal... – bez różnicy),
 * wybiera tę właściwą i udostępnia jej stan jako [nowPlaying]. Steruje przez MediaController.
 * Wszystko dzieje się na głównym wątku.
 */
class MusicSessionManager(
    private val context: Context,
    private val repository: MusicMetadataRepository = MusicMetadataRepository(context),
) {
    private class Bound(val controller: MediaController, val callback: MediaController.Callback)

    private val mediaSessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MusicNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private var bound: List<Bound> = emptyList()
    private var active: MediaController? = null
    private var started = false

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> bind(list.orEmpty()) }

    fun start() {
        if (started) return
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            started = true
            bind(mediaSessionManager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // Brak dostępu do powiadomień – sesje niedostępne.
            Log.w(TAG, "Brak Notification Access", e)
        }
    }

    fun stop() {
        if (started) {
            runCatching { mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        }
        started = false
        unbindAll()
        active = null
        _nowPlaying.value = NowPlaying()
    }

    // ---- Sterowanie ----

    fun playPause() {
        val c = active ?: return
        if (_nowPlaying.value.isPlaying) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        active?.transportControls?.skipToNext()
    }

    fun previous() {
        active?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        active?.transportControls?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun sendCustomAction(action: PlaybackState.CustomAction) {
        active?.transportControls?.sendCustomAction(action, null)
    }

    /** Otwiera aplikację, z której gra muzyka (activity sesji, a w razie braku zwykły launcher aplikacji). */
    fun openPlayerApp(): Boolean {
        val c = active ?: return false
        val options = ActivityOptions.makeBasic()
        options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        val pi = c.sessionActivity
        if (pi != null) {
            try {
                pi.send(context, 0, null, null, null, null, options.toBundle())
                return true
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "sessionActivity anulowane", e)
            }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch, options.toBundle())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się otworzyć aplikacji", e)
            false
        }
    }

    // ---- Wewnętrzne ----

    private fun unbindAll() {
        bound.forEach { runCatching { it.controller.unregisterCallback(it.callback) } }
        bound = emptyList()
    }

    private fun bind(controllers: List<MediaController>) {
        unbindAll()
        bound = controllers.map { c ->
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    refresh()
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    refresh()
                }

                override fun onSessionDestroyed() {
                    // Lista sesji zaktualizuje się przez sessionsListener; odśwież na wszelki wypadek.
                    handler.post { runCatching { bind(mediaSessionManager.getActiveSessions(listenerComponent)) } }
                }
            }
            c.registerCallback(cb, handler)
            Bound(c, cb)
        }
        refresh()
    }

    private fun refresh() {
        val best = pickBest()
        active = best
        _nowPlaying.value = if (best == null) NowPlaying() else repository.build(best)
    }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
    }

    private fun isUsable(c: MediaController): Boolean {
        val s = c.playbackState?.state
        return c.metadata != null || s == PlaybackState.STATE_PLAYING ||
            s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING
    }

    /**
     * 1) gra coś -> preferowany pakiet (YT Music), inaczej pierwsze grające,
     * 2) nic nie gra -> pierwsza użyteczna sesja (system sortuje od ostatnio aktywnej).
     */
    private fun pickBest(): MediaController? {
        val usable = bound.map { it.controller }.filter { isUsable(it) }
        val playing = usable.filter { isPlaying(it) }
        return playing.firstOrNull { it.packageName == PREFERRED_PACKAGE }
            ?: playing.firstOrNull()
            ?: usable.firstOrNull()
    }

    companion object {
        private const val TAG = "MusicSessionManager"
        const val PREFERRED_PACKAGE = "com.google.android.apps.youtube.music"
    }
}
