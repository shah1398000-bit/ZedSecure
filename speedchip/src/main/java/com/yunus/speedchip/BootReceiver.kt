package com.yunus.speedchip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ok = i.action == Intent.ACTION_BOOT_COMPLETED ||
            i.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (ok && Controls.wantsOn(c)) Controls.start(c)
    }
}
