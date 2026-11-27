package com.yunus.speedchip

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class SpeedService : Service() {
    private var engine: SpeedEngine? = null

    override fun onCreate() {
        val e = SpeedEngine(this, true)
        engine = e
        e.ensureChannels()
        startForeground(
            SpeedEngine.FGS_ID, e.serviceNote("Starting"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        e.start()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        engine?.stop()
        engine = null
    }
}
