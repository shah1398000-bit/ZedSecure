package com.yunus.speedchip

import android.content.Context
import android.content.Intent

object Controls {
    private fun sp(c: Context) = c.getSharedPreferences("sc", Context.MODE_PRIVATE)
    fun wantsOn(c: Context) = sp(c).getBoolean("enabled", false)
    fun followVpn(c: Context) = sp(c).getBoolean("follow_vpn", false)
    fun setFollowVpn(c: Context, v: Boolean) = sp(c).edit().putBoolean("follow_vpn", v).apply()

    fun start(c: Context) {
        sp(c).edit().putBoolean("enabled", true).apply()
        c.startForegroundService(Intent(c, SpeedService::class.java))
    }

    fun stop(c: Context) {
        sp(c).edit().putBoolean("enabled", false).apply()
        c.stopService(Intent(c, SpeedService::class.java))
    }
}
