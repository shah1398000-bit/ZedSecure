package com.yunus.speedchip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.ui.format.formatRate

private fun notifOk(c: Context) =
    c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun liveOk(c: Context): Boolean? = runCatching {
    val nm = c.getSystemService(android.app.NotificationManager::class.java)
    nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean
}.getOrNull()

private fun batteryOk(c: Context) =
    c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

private fun openLive(c: Context) {
    val pkg = c.packageName
    val i = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { c.startActivity(i) }.onFailure {
        c.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun askBattery(c: Context) {
    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { c.startActivity(i) }
}

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val running by SpeedState.running.collectAsStateWithLifecycle()
    val down by SpeedState.down.collectAsStateWithLifecycle()
    val up by SpeedState.up.collectAsStateWithLifecycle()
    var refresh by mutableIntStateOf(0).let { remember { it } }.let { st -> androidx.compose.runtime.remember { st } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notif = remember(refresh) { notifOk(ctx) }
    val live = remember(refresh) { liveOk(ctx) }
    val battery = remember(refresh) { batteryOk(ctx) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh++
        if (ok) Controls.start(ctx)
    }
    val toggle: () -> Unit = {
        if (running) Controls.stop(ctx)
        else if (notifOk(ctx)) Controls.start(ctx)
        else perm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val bg = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val (dn, du) = formatRate(down)
    val (un, uu) = formatRate(up)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SpeedChip", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
        }
        Spacer(Modifier.height(48.dp))
        Box(
            Modifier.size(200.dp).clip(CircleShape).background(bg).clickable { toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (running) "STOP" else "START",
                color = fg,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (running) "↓ $dn $du     ↑ $un $uu" else "Stopped",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(36.dp))
        StatusRow("Notifications allowed", notif, "Allow") {
            perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        StatusRow("Live Update (status-bar chip)", live, "Enable") { openLive(ctx) }
        StatusRow("Battery: no restrictions", battery, "Fix") { askBattery(ctx) }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean?, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (ok) { true -> "✓"; false -> "✗"; null -> "?" },
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (ok != true) OutlinedButton(onClick = onAction) { Text(action) }
    }
}
