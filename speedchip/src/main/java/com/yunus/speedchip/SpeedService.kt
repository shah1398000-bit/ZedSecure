package com.yunus.speedchip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dev.cluvex.zedsecure.domain.model.NotifChip
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatRate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

class SpeedService : Service() {
    companion object {
        const val STEPS = 40
        const val MIN_BPS = 30L * 1024L
        const val TICK_MS = 400L
        const val SAMPLE_MS = 1000L
        const val FGS_ID = 1
        const val ISLAND_ID = 2
    }

    private data class Spec(
        val step: Int,
        val title: String,
        val content: String,
        val chip: String,
        val live: Boolean,
    )

    private val h = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private lateinit var cm: ConnectivityManager
    private lateinit var pm: PowerManager
    private val iconCache = arrayOfNulls<IconCompat>(STEPS + 1)
    private var vpnCb: ConnectivityManager.NetworkCallback? = null
    private var alive = false

    private var t0 = 0L
    private var rx0 = 0L
    private var tx0 = 0L
    private var sampT = 0L
    private var sampRx = 0L
    private var sampTx = 0L
    private var curD = 0L
    private var curU = 0L
    private var needle = 0.0
    private var wasActive: Boolean? = null
    private var lastSpec: Spec? = null

    private val tick = Runnable { step() }

    override fun onCreate() {
        nm = getSystemService(NotificationManager::class.java)
        cm = getSystemService(ConnectivityManager::class.java)
        pm = getSystemService(PowerManager::class.java)
        alive = true
        SpeedState.running.value = true
        nm.createNotificationChannel(
            NotificationChannel("service", "SpeedChip service", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(
            NotificationChannel("speed", "Speed", NotificationManager.IMPORTANCE_LOW))

        val now = SystemClock.elapsedRealtime()
        t0 = now
        sampT = now
        sampRx = TrafficStats.getTotalRxBytes()
        sampTx = TrafficStats.getTotalTxBytes()
        rx0 = sampRx
        tx0 = sampTx
        startForeground(FGS_ID, serviceNote("Starting"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        registerVpn()
        h.post(tick)
    }

    private fun step() {
        if (!alive) return
        val now = SystemClock.elapsedRealtime()
        val vpn = scanVpn()
        SpeedState.vpn.value = vpn
        val active = !Controls.followVpn(this) || vpn

        if (now - sampT >= SAMPLE_MS) {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = (now - sampT).coerceAtLeast(1)
            var d = (rx - sampRx) * 1000 / dt
            var u = (tx - sampTx) * 1000 / dt
            if (vpn) { d /= 2; u /= 2 }
            curD = if (d >= MIN_BPS) d else 0L
            curU = if (u >= MIN_BPS) u else 0L
            sampT = now; sampRx = rx; sampTx = tx
            SpeedState.down.value = curD
            SpeedState.up.value = curU
        }

        if (wasActive != active) {
            if (active) {
                t0 = now; rx0 = sampRx; tx0 = sampTx
            } else {
                nm.cancel(ISLAND_ID)
                lastSpec = null
            }
            nm.notify(FGS_ID, serviceNote(if (active) "Running" else "Waiting for VPN"))
            wasActive = active
        }

        val target = frac(curD)
        needle += (target - needle) * 0.35
        if (abs(target - needle) < 0.01) needle = target

        val interactive = pm.isInteractive
        if (active && interactive) {
            var total = sampRx - rx0 + sampTx - tx0
            if (vpn) total /= 2
            val sp = spec((needle * STEPS).roundToInt(), curD, curU, now - t0, total)
            if (sp != lastSpec) {
                lastSpec = sp
                nm.notify(ISLAND_ID, island(sp))
            }
        }
        h.postDelayed(tick, if (interactive) TICK_MS else 2000L)
    }

    private fun spec(stepIdx: Int, d: Long, u: Long, elapsedMs: Long, total: Long): Spec {
        val s = (application as SpeedChipApp).settings.settings.value
        val (dn, du) = formatRate(d)
        val (un, uu) = formatRate(u)
        val chip = if (!s.speedInNotification) "ON" else when (s.notifChip) {
            NotifChip.Speed -> "↓$dn${du.first()}"
            NotifChip.Duration -> elapsed(elapsedMs)
            NotifChip.Total -> formatBytes(total).replace(" ", "")
            NotifChip.ConfigName -> netLabel()
            else -> "ON"
        }
        val title = if (s.speedInNotification) "↓ $dn $du" else "SpeedChip"
        val content = if (s.speedInNotification) "↓ $dn $du   ↑ $un $uu" else "Running"
        return Spec(stepIdx.coerceIn(0, STEPS), title, content, chip, s.liveNotification)
    }

    private fun openIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun serviceNote(text: String): Notification =
        NotificationCompat.Builder(this, "service")
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle("SpeedChip")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent())
            .build()

    private fun island(sp: Spec): Notification =
        NotificationCompat.Builder(this, "speed")
            .setSmallIcon(gaugeIcon(sp.step))
            .setContentTitle(sp.title)
            .setContentText(sp.content)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent())
            .setShortCriticalText(sp.chip)
            .setRequestPromotedOngoing(sp.live)
            .build()

    private fun frac(bps: Long): Double =
        (ln(1.0 + bps / 1024.0) / ln(1.0 + 30.0 * 1024.0)).coerceIn(0.0, 1.0)

    private fun gaugeIcon(step: Int): IconCompat {
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
        val sweep = 270f * step / STEPS
        if (step > 0) cv.drawArc(box, 135f, sweep, false, on)
        val a = Math.toRadians((135f + sweep).toDouble())
        val needlePaint = Paint(on).apply { strokeWidth = w * 0.9f }
        cv.drawLine(c, c, c + (r * 0.8f * cos(a)).toFloat(), c + (r * 0.8f * sin(a)).toFloat(), needlePaint)
        cv.drawCircle(c, c, w * 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return IconCompat.createWithBitmap(bmp).also { iconCache[step] = it }
    }

    @Suppress("DEPRECATION")
    private fun scanVpn(): Boolean = cm.allNetworks.any {
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun wake() {
        h.post {
            if (!alive) return@post
            h.removeCallbacks(tick)
            step()
        }
    }

    private fun registerVpn() {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { wake() }
            override fun onLost(network: Network) { wake() }
        }
        cm.registerNetworkCallback(req, cb)
        vpnCb = cb
    }

    @Suppress("DEPRECATION")
    private fun netLabel(): String {
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

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        alive = false
        h.removeCallbacksAndMessages(null)
        vpnCb?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        nm.cancel(ISLAND_ID)
        SpeedState.running.value = false
        SpeedState.vpn.value = false
        SpeedState.down.value = 0
        SpeedState.up.value = 0
    }
}
