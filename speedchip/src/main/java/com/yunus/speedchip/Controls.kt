package com.yunus.speedchip

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object Controls {
    private fun sp(c: Context) = c.getSharedPreferences("sc", Context.MODE_PRIVATE)
    fun wantsOn(c: Context) = sp(c).getBoolean("enabled", true)
    fun followVpn(c: Context) = sp(c).getBoolean("follow_vpn", true)
    fun smoothNeedle(c: Context) = sp(c).getBoolean("smooth_needle", true)
    fun setSmoothNeedle(c: Context, v: Boolean) = sp(c).edit().putBoolean("smooth_needle", v).apply()

    fun setFollowVpn(c: Context, v: Boolean) {
        sp(c).edit().putBoolean("follow_vpn", v).apply()
        if (v) start(c)
    }

    fun a11yEnabled(c: Context): Boolean {
        val raw = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val id = ComponentName(c, SpeedA11y::class.java).flattenToString()
        return raw.split(':').any { it.equals(id, ignoreCase = true) }
    }

    fun start(c: Context) {
        sp(c).edit().putBoolean("enabled", true).apply()
        val a = SpeedA11y.instance
        if (a != null) a.engineStart()
        else c.startForegroundService(Intent(c, SpeedService::class.java))
    }

    fun stop(c: Context) {
        sp(c).edit().putBoolean("enabled", false).apply()
        SpeedA11y.instance?.engineStop()
        c.stopService(Intent(c, SpeedService::class.java))
    }
}
