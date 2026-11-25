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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
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
            SpeedState.down.value = d; SpeedState.up.value = u
            nm.notify(1, build(d, u, t - t0, total))
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        nm = getSystemService(NotificationManager::class.java)
        SpeedState.running.value = true
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

    private val iconCache = arrayOfNulls<IconCompat>(41)

    private fun gaugeIcon(bps: Long): IconCompat {
        val f = (ln(1.0 + bps / 1024.0) / ln(1.0 + 30.0 * 1024.0)).coerceIn(0.0, 1.0)
        val step = (f * 40).roundToInt()
        iconCache[step]?.let { return it }
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val c = size / 2f
        val r = size * 0.38f
        val w = size * 0.10f
        val box = RectF(c - r, c - r, c + r, c + r)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(105, 255, 255, 255)
        }
        val on = Paint(track).apply { color = Color.WHITE }
        cv.drawArc(box, 135f, 270f, false, track)
        val sweep = 270f * step / 40f
        if (step > 0) cv.drawArc(box, 135f, sweep, false, on)
        val a = Math.toRadians((135f + sweep).toDouble())
        val needle = Paint(on).apply { strokeWidth = w * 0.9f }
        cv.drawLine(c, c, c + (r * 0.8f * cos(a)).toFloat(), c + (r * 0.8f * sin(a)).toFloat(), needle)
        cv.drawCircle(c, c, w * 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return IconCompat.createWithBitmap(bmp).also { iconCache[step] = it }
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
            .setSmallIcon(gaugeIcon(d))
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
    override fun onDestroy() {
        h.removeCallbacks(tick)
        SpeedState.running.value = false
        SpeedState.down.value = 0; SpeedState.up.value = 0
    }
}
