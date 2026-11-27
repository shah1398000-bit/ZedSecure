package com.yunus.speedchip

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class SpeedA11y : AccessibilityService() {
    companion object {
        @Volatile var instance: SpeedA11y? = null
    }

    private var engine: SpeedEngine? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        stopService(Intent(this, SpeedService::class.java))
        val e = SpeedEngine(this, false)
        engine = e
        if (Controls.wantsOn(this)) e.start()
    }

    fun engineStart() { engine?.start() }
    fun engineStop() { engine?.stop() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        engine?.stop()
        engine = null
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        instance = null
        super.onDestroy()
    }
}
