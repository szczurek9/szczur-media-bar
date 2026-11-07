package com.szczurek9.mediabar.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import com.szczurek9.mediabar.R

/** Zamienia MediaController (MediaMetadata + PlaybackState) na [NowPlaying] z bezpiecznymi wartościami zastępczymi. */
class MusicMetadataRepository(private val context: Context) {

    private var cachedUri: String? = null
    private var cachedUriBitmap: Bitmap? = null
    private var cachedIconPackage: String? = null
    private var cachedIcon: Bitmap? = null

    fun build(controller: MediaController): NowPlaying {
        val md = controller.metadata
        val ps = controller.playbackState

        val title = firstNotBlank(
            md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
        ) ?: context.getString(R.string.unknown_title)

        val artist = firstNotBlank(
            md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
        ) ?: context.getString(R.string.unknown_artist)

        val duration = (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)

        val state = ps?.state ?: PlaybackState.STATE_NONE
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        val position = ps?.position?.takeIf { it >= 0 } ?: 0L
        val updatedAt = ps?.lastPositionUpdateTime ?: 0L
        val speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        val canSeek = duration > 0 && ps != null && (ps.actions and PlaybackState.ACTION_SEEK_TO) != 0L
        val customActions = ps?.customActions.orEmpty()
        val shuffleAction = customActions.firstOrNull { it.matches("shuffle", "losow", "mieszaj") }
        val repeatAction = customActions.firstOrNull { it.matches("repeat", "loop", "powtarz", "zapęt") }

        return NowPlaying(
            hasSession = true,
            packageName = controller.packageName,
            title = title,
            artist = artist,
            art = loadArt(md),
            durationMs = duration,
            positionMs = position,
            positionUpdatedAt = updatedAt,
            speed = speed,
            isPlaying = playing,
            canSeek = canSeek,
            appIcon = iconFor(controller.packageName),
            shuffleAction = shuffleAction,
            repeatAction = repeatAction,
            // Dla shuffle nazwa akcji u Ciebie opisuje polecenie do wykonania (nie stan), więc wynik odwracamy.
            shuffleOn = shuffleAction?.let { inferOnOff(it)?.not() },
            repeatMode = repeatAction?.let { inferRepeat(it) },
            customActionInfo = customActions.map { "${it.action} — ${it.name}" },
        )
    }

    private fun PlaybackState.CustomAction.matches(vararg keys: String): Boolean {
        val hay = "$action ${name ?: ""}".lowercase()
        return keys.any { hay.contains(it) }
    }

    private fun PlaybackState.CustomAction.tokens(): List<String> =
        "$action ${name ?: ""}".lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

    /** Czy nazwa opisuje polecenie ("turn shuffle on", "włącz losowo"), a nie stan ("shuffle on")? */
    private fun List<String>.isCommand(): Boolean =
        any { it == "turn" || it == "set" || it == "switch" || it == "enable" || it == "disable" || it.startsWith("włącz") || it.startsWith("wyłącz") }

    /** true/false, gdy nazwa akcji jednoznacznie mówi o włączeniu/wyłączeniu; inaczej null. */
    private fun inferOnOff(a: PlaybackState.CustomAction): Boolean? {
        val t = a.tokens()
        val on = t.any { it == "on" || it == "enable" || it == "enabled" || it.startsWith("włącz") }
        val off = t.any { it == "off" || it == "disable" || it == "disabled" || it.startsWith("wyłącz") }
        if (on == off) return null
        // Polecenie "włącz" widzimy wtedy, gdy funkcja jest jeszcze wyłączona – i odwrotnie.
        return if (t.isCommand()) !on else on
    }

    private fun inferRepeat(a: PlaybackState.CustomAction): RepeatMode? {
        val t = a.tokens()
        val one = t.any { it == "one" || it == "track" || it == "song" || it == "single" || it == "1" || it.startsWith("jedn") || it.startsWith("utwór") }
        val all = t.any { it == "all" || it == "playlist" || it == "queue" || it == "album" || it.startsWith("wszystk") }
        if (one && !all) return RepeatMode.ONE
        if (all && !one) return RepeatMode.ALL
        return when (inferOnOff(a)) {
            // Jak w shuffle: nazwa opisuje polecenie, więc znaczenie jest odwrotne.
            true -> RepeatMode.OFF
            false -> RepeatMode.ALL
            null -> null
        }
    }

    private fun iconFor(pkg: String): Bitmap? {
        if (pkg == cachedIconPackage) return cachedIcon
        val bmp = try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(64, 64)
        } catch (e: Exception) {
            null
        }
        cachedIconPackage = pkg
        cachedIcon = bmp
        return bmp
    }

    private fun firstNotBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private fun loadArt(md: MediaMetadata?): Bitmap? {
        if (md == null) return null
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }

        val uri = firstNotBlank(
            md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_ART_URI),
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
        ) ?: return null

        if (uri == cachedUri) return cachedUriBitmap
        val bmp = decodeLocalUri(uri)
        cachedUri = uri
        cachedUriBitmap = bmp
        return bmp
    }

    /** Tylko lokalne URI (content/file). Nie pobieramy niczego z sieci. */
    private fun decodeLocalUri(raw: String): Bitmap? {
        return try {
            val uri = Uri.parse(raw)
            if (uri.scheme != "content" && uri.scheme != "file") return null
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
}
