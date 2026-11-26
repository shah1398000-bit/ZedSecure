package com.yunus.speedchip

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.ConnectButtonStyle
import dev.cluvex.zedsecure.domain.model.ConnectionState
import dev.cluvex.zedsecure.ui.bento.BentoConnectControl
import dev.cluvex.zedsecure.ui.format.formatRate
import dev.cluvex.zedsecure.ui.glass.GlassZControl
import dev.cluvex.zedsecure.ui.glass.GlassZPhase
import dev.cluvex.zedsecure.ui.home.AltConnectControl
import dev.cluvex.zedsecure.ui.settings.SettingsGroup
import dev.cluvex.zedsecure.ui.settings.SettingsSwitchRow

private fun notifOk(c: Context) =
    c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun liveOk(c: Context): Boolean? = runCatching {
    val nm = c.getSystemService(android.app.NotificationManager::class.java)
    nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean
}.getOrNull()

private fun batteryOk(c: Context) =
    c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

private fun isXiaomi() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

private fun autostartOk(c: Context): Boolean? {
    if (!isXiaomi()) return null
    return runCatching {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val m = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
        (m.invoke(ops, 10008, android.os.Process.myUid(), c.packageName) as Int) == AppOpsManager.MODE_ALLOWED
    }.getOrNull()
}

private fun openLive(c: Context) {
    val pkg = c.packageName
    val i = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { c.startActivity(i) }.onFailure { openNotifSettings(c) }
}

private fun openNotifSettings(c: Context) {
    runCatching {
        c.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun askBattery(c: Context) {
    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { c.startActivity(i) }
}

private fun openAutostart(c: Context) {
    val i = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { c.startActivity(i) }.onFailure {
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun altStyle(st: ConnectButtonStyle) = when (st) {
    ConnectButtonStyle.Ring, ConnectButtonStyle.Bar,
    ConnectButtonStyle.Icon, ConnectButtonStyle.Switch -> st
    else -> ConnectButtonStyle.Bar
}

@Composable
fun SpeedHome(
    s: AppSettings,
    padding: PaddingValues,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val ctx = LocalContext.current
    val running by SpeedState.running.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notif = remember(refresh) { notifOk(ctx) }
    val live = remember(refresh) { liveOk(ctx) }
    val battery = remember(refresh) { batteryOk(ctx) }
    val auto = remember(refresh) { autostartOk(ctx) }
    var follow by remember { mutableStateOf(Controls.followVpn(ctx)) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh++
        if (ok) Controls.start(ctx) else openNotifSettings(ctx)
    }
    val toggle: () -> Unit = remember(running, perm) {
        {
            if (running) Controls.stop(ctx)
            else if (notifOk(ctx)) Controls.start(ctx)
            else perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val state = if (running) ConnectionState.Connected else ConnectionState.Idle
    val animate = !s.reduceMotion
    val onBg = MaterialTheme.colorScheme.onBackground
    val sub = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("SpeedChip", color = onBg, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(36.dp))

        when (s.appDesign) {
            AppDesign.Glass -> GlassZControl(
                phase = if (running) GlassZPhase.On else GlassZPhase.Off,
                onClick = toggle,
                label = if (running) "Stop" else "Start",
                animate = animate,
            )
            AppDesign.Bento -> BentoConnectControl(state, toggle, animate)
            AppDesign.Pixel -> AltConnectControl(ConnectButtonStyle.Ring, state, toggle)
            AppDesign.Old -> AltConnectControl(altStyle(s.connectButtonStyle), state, toggle)
        }

        Spacer(Modifier.height(28.dp))
        Readout(running, follow, onBg, sub)
        Spacer(Modifier.height(32.dp))

        val allGood = notif && battery && live != false && auto != false
        AnimatedVisibility(
            visible = !allGood,
            exit = if (s.reduceMotion) ExitTransition.None
            else fadeOut() + slideOutHorizontally(targetOffsetX = { it }) + shrinkVertically(),
        ) {
            SettingsGroup {
                StatusRow("Notifications allowed", notif, "Allow", onBg) {
                    perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                StatusRow("Live Update (status-bar chip)", live, "Enable", onBg) { openLive(ctx) }
                StatusRow("Battery: no restrictions", battery, "Fix", onBg) { askBattery(ctx) }
                if (isXiaomi()) {
                    StatusRow("Background autostart", auto, "Open", onBg) { openAutostart(ctx) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsGroup {
            SettingsSwitchRow(
                title = "Follow VPN",
                summary = "Show the island only while a VPN is connected",
                checked = follow,
                onCheckedChange = { follow = it; Controls.setFollowVpn(ctx, it) },
            )
            SettingsSwitchRow(
                title = "Smooth mode",
                summary = "Fewer animations and effects for better performance",
                checked = s.reduceMotion && s.glass.reduceTransparency,
                onCheckedChange = { v ->
                    onUpdate {
                        it.copy(
                            reduceMotion = v,
                            glass = it.glass.copy(reduceTransparency = v),
                            bento = it.bento.copy(wave = !v, motifs = !v, playfulTouch = !v),
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Readout(running: Boolean, follow: Boolean, color: Color, sub: Color) {
    val vpn by SpeedState.vpn.collectAsStateWithLifecycle()
    val down by SpeedState.down.collectAsStateWithLifecycle()
    val up by SpeedState.up.collectAsStateWithLifecycle()
    val waiting = running && follow && !vpn
    val (dn, du) = formatRate(down)
    val (un, uu) = formatRate(up)
    Text(
        if (waiting) "Waiting for VPN" else if (running) "↓ $dn $du" else "Stopped",
        color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
    )
    if (running && !waiting) Text("↑ $un $uu", color = sub, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun StatusRow(label: String, ok: Boolean?, action: String, color: Color, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (ok) { true -> "✓"; false -> "✗"; null -> "?" },
            color = color,
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(label, color = color, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (ok != true) OutlinedButton(onClick = onAction) { Text(action) }
    }
}
