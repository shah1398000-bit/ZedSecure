package com.yunus.speedchip

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SpeedTile : TileService() {
    override fun onStartListening() = paint(SpeedState.running.value)

    override fun onClick() {
        val turnOn = !SpeedState.running.value
        if (turnOn) Controls.start(this) else Controls.stop(this)
        paint(turnOn)
    }

    private fun paint(on: Boolean) {
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "SpeedChip"
            updateTile()
        }
    }
}
