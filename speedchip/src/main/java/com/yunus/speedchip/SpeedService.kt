package com.yunus.speedchip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dev.cluvex.zedsecure.domain.model.NotifChip
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatRate

class SpeedService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private var t0 = 0L
    private var rx0 = 0L
    private var tx0 = 0L
    private var lastT = 0L
    private var lastRx = 0L
    private var lastTx = 0L

    private val tick = object : Runnable {
        override fun run() {
            val t = SystemClock.elapsedRealtime()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = (t - lastT).coerceAtLeast(1)
            var d = (rx - lastRx) * 1000 / dt
            var u = (tx - lastTx) * 1000 / dt
            var total = rx - rx0 + tx - tx0
            if (vpnActive()) { d /= 2; u /= 2; total /= 2 }
            lastT = t; lastRx = rx; lastTx = tx
            nm.notify(1, build(d, u, t - t0, total))
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("speed", "Speed", NotificationManager.IMPORTANCE_LOW))
        t0 = SystemClock.elapsedRealtime(); lastT = t0
        rx0 = TrafficStats.getTotalRxBytes(); lastRx = rx0
        tx0 = TrafficStats.getTotalTxBytes(); lastTx = tx0
        startForeground(1, build(0, 0, 0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        h.postDelayed(tick, 1000)
    }

    private fun vpnActive(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun netLabel(): String {
        val cm = getSystemService(ConnectivityManager::class.java)
        for (n in cm.allNetworks) {
            val c = cm.getNetworkCapabilities(n) ?: continue
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            return if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "Wi-Fi" else "Mobile"
        }
        return "Offline"
    }

    private fun elapsed(ms: Long): String {
        val s = ms / 1000
        return when { s < 60 -> "${s}s"; s < 3600 -> "${s / 60}m"; else -> "${s / 3600}h" }
    }

    private fun build(d: Long, u: Long, elapsedMs: Long, total: Long): Notification {
        val s = (application as SpeedChipApp).settings.settings.value
        val (dn, du) = formatRate(d)
        val (un, uu) = formatRate(u)
        val rate = "$dn${du.first()}"
        val chip = if (!s.speedInNotification) "ON" else when (s.notifChip) {
            NotifChip.Speed -> "↓$rate"
            NotifChip.Duration -> elapsed(elapsedMs)
            NotifChip.Total -> formatBytes(total).replace(" ", "")
            NotifChip.ConfigName -> netLabel()
            else -> "ON"
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "speed")
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(if (s.speedInNotification) "↓ $dn $du" else "SpeedChip")
            .setContentText(if (s.speedInNotification) "↓ $dn $du   ↑ $un $uu" else "Running")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setShortCriticalText(chip)
            .setRequestPromotedOngoing(s.liveNotification)
            .build()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { h.removeCallbacks(tick) }
}
