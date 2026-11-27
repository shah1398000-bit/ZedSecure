package com.yunus.speedchip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import dev.cluvex.zedsecure.domain.model.NotifChip
import dev.cluvex.zedsecure.ui.format.formatBytes
import dev.cluvex.zedsecure.ui.format.formatRate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

class SpeedEngine(private val ctx: Context, private val withServiceNote: Boolean) {
    companion object {
        const val STEPS = 54
        const val MIN_BPS = 30L * 1024L
        const val TICK_MS = 1000L
        const val FGS_ID = 1
        const val ISLAND_ID = 2
        const val FRAME_MS = 250L
        const val KEEPALIVE_MS = 25_000L
        const val TIMEOUT_MS = 60_000L
        @Volatile private var current: SpeedEngine? = null
    }

    private data class Spec(
        val step: Int,
        val title: String,
        val content: String,
        val chip: String,
        val live: Boolean,
    )

    private val h = Handler(Looper.getMainLooper())
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val iconCache = arrayOfNulls<IconCompat>(STEPS + 1)
    private var vpnCb: ConnectivityManager.NetworkCallback? = null
    private var screenRx: BroadcastReceiver? = null
    private var alive = false

    private var t0 = 0L
    private var rx0 = 0L
    private var tx0 = 0L
    private var sampT = 0L
    private var sampRx = 0L
    private var sampTx = 0L
    private var curD = 0L
    private var curU = 0L
    private var wasActive: Boolean? = null
    private var lastSpec: Spec? = null
    private var goal = 0.0
    private var needle = 0.0
    private var lastPostAt = 0L

    private val tick = Runnable { step() }

    fun ensureChannels() {
        nm.createNotificationChannel(
            NotificationChannel("service", "SpeedChip service", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(
            NotificationChannel("speed", "Speed", NotificationManager.IMPORTANCE_LOW))
    }

    fun start() {
        if (alive) return
        current?.takeIf { it !== this }?.stop()
        current = this
        alive = true
        ensureChannels()
        val now = SystemClock.elapsedRealtime()
        resetBaseline(now)
        t0 = now; rx0 = sampRx; tx0 = sampTx
        wasActive = null
        lastSpec = null
        curD = 0; curU = 0
        goal = 0.0; needle = 0.0; lastPostAt = 0L
        SpeedState.mode.value = if (withServiceNote) "service" else "accessibility"
        SpeedState.running.value = true
        registerVpn()
        registerScreen()
        h.post(tick)
    }

    fun stop() {
        if (!alive) return
        alive = false
        h.removeCallbacksAndMessages(null)
        vpnCb?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        vpnCb = null
        screenRx?.let { runCatching { ctx.unregisterReceiver(it) } }
        screenRx = null
        if (current === this) {
            current = null
            nm.cancel(ISLAND_ID)
            SpeedState.running.value = false
            SpeedState.vpn.value = false
            SpeedState.island.value = false
            SpeedState.down.value = 0
            SpeedState.up.value = 0
        }
    }

    fun wake() {
        h.post { if (alive) step() }
    }

    private fun resetBaseline(now: Long) {
        sampT = now
        sampRx = TrafficStats.getTotalRxBytes()
        sampTx = TrafficStats.getTotalTxBytes()
    }

    private fun step() {
        if (!alive) return
        h.removeCallbacks(tick)
        val now = SystemClock.elapsedRealtime()
        val vpn = scanVpn()
        SpeedState.vpn.value = vpn
        val active = !Controls.followVpn(ctx) || vpn
        val screen = pm.isInteractive

        if (wasActive != active) {
            if (active) {
                resetBaseline(now)
                t0 = now; rx0 = sampRx; tx0 = sampTx
            } else {
                nm.cancel(ISLAND_ID)
                lastSpec = null
                curD = 0; curU = 0
                goal = 0.0; needle = 0.0
                SpeedState.island.value = false
                SpeedState.down.value = 0
                SpeedState.up.value = 0
            }
            if (withServiceNote) nm.notify(FGS_ID, serviceNote(if (active) "Running" else "Waiting for VPN"))
            wasActive = active
        }

        var delay = TICK_MS
        if (active && screen) {
            if (now - sampT >= TICK_MS - 50) {
                sample(now, vpn)
                goal = frac(curD) * STEPS
            }
            val smooth = Controls.smoothNeedle(ctx)
            if (smooth) {
                needle += (goal - needle) * 0.5
                if (abs(goal - needle) < 0.6) needle = goal
            } else {
                needle = goal
            }
            var total = sampRx - rx0 + sampTx - tx0
            if (vpn) total /= 2
            val sp = spec(now - t0, total)
            if (sp != lastSpec || now - lastPostAt >= KEEPALIVE_MS) {
                lastSpec = sp
                lastPostAt = now
                nm.notify(ISLAND_ID, island(sp))
                SpeedState.island.value = true
            }
            delay = if (smooth && needle != goal) FRAME_MS
            else (sampT + TICK_MS - now).coerceIn(50L, TICK_MS)
        }
        if (screen) h.postDelayed(tick, delay)
    }

    private fun sample(now: Long, vpn: Boolean) {
        val dt = (now - sampT).coerceAtLeast(1)
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        var d = (rx - sampRx) * 1000 / dt
        var u = (tx - sampTx) * 1000 / dt
        if (vpn) { d /= 2; u /= 2 }
        curD = if (d >= MIN_BPS) d else 0L
        curU = if (u >= MIN_BPS) u else 0L
        sampT = now; sampRx = rx; sampTx = tx
        SpeedState.down.value = curD
        SpeedState.up.value = curU
    }

    private fun spec(elapsedMs: Long, total: Long): Spec {
        val s = (ctx.applicationContext as SpeedChipApp).settings.settings.value
        val (dn, du) = formatRate(curD)
        val (un, uu) = formatRate(curU)
        val chip = if (!s.speedInNotification) "ON" else when (s.notifChip) {
            NotifChip.Speed -> "↓$dn${du.first()}"
            NotifChip.Duration -> elapsed(elapsedMs)
            NotifChip.Total -> formatBytes(total).replace(" ", "")
            NotifChip.ConfigName -> netLabel()
            else -> "ON"
        }
        val title = if (s.speedInNotification) "↓ $dn $du" else "SpeedChip"
        val content = if (s.speedInNotification) "↓ $dn $du   ↑ $un $uu" else "Running"
        val stepIdx = needle.roundToInt().coerceIn(0, STEPS)
        return Spec(stepIdx, title, content, chip, s.liveNotification)
    }

    private fun openIntent() = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    fun serviceNote(text: String): Notification =
        NotificationCompat.Builder(ctx, "service")
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
        NotificationCompat.Builder(ctx, "speed")
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
            .setTimeoutAfter(TIMEOUT_MS)
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
        val needle = Paint(on).apply { strokeWidth = w * 0.9f }
        cv.drawLine(c, c, c + (r * 0.8f * cos(a)).toFloat(), c + (r * 0.8f * sin(a)).toFloat(), needle)
        cv.drawCircle(c, c, w * 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return IconCompat.createWithBitmap(bmp).also { iconCache[step] = it }
    }

    @Suppress("DEPRECATION")
    private fun scanVpn(): Boolean = cm.allNetworks.any {
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
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

    private fun registerScreen() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                resetBaseline(SystemClock.elapsedRealtime())
                wake()
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching {
            ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onSuccess { screenRx = r }
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
}
