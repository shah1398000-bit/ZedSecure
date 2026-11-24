package com.yunus.speedchip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.cluvex.zedsecure.ui.platform.Platform

class AppPlatform(private val ctx: Context) : Platform {
    private val cb get() = ctx.getSystemService(ClipboardManager::class.java)
    override fun copyToClipboard(text: String) { cb.setPrimaryClip(ClipData.newPlainText("t", text)) }
    override fun readClipboard(): String? = cb.primaryClip?.getItemAt(0)?.text?.toString()
    override fun shareText(text: String) {
        ctx.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun openUri(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun toast(message: String) { Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show() }
    override val supportsDynamicColor: Boolean get() = true
    override val hasStatusNotification: Boolean get() = true
    override val minimalInterface: Boolean get() = true
}
