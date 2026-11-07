package com.szczurek9.mediabar.tile

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.szczurek9.mediabar.MainActivity
import com.szczurek9.mediabar.MusicBarApp
import com.szczurek9.mediabar.R

/** Kafelek szybkich ustawień: tymczasowo ukryj / ponownie pokaż pasek. */
class MusicBarTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val controller = (application as MusicBarApp).overlayController
        if (controller == null) {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            controller.toggleUserVisibility()
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val controller = (application as MusicBarApp).overlayController
        when {
            controller == null -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_setup)
            }
            controller.isEnabledAndShownToUser -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = getString(R.string.tile_on)
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_off)
            }
        }
        tile.updateTile()
    }
}
