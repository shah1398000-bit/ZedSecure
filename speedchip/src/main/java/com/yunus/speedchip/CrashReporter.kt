package com.yunus.speedchip

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashReporter {
    fun install(ctx: Context) {
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val trace = sw.toString().take(30000)
            runCatching {
                ctx.startActivity(
                    Intent(ctx, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra("t", trace)
                )
            }
            Thread.sleep(300)
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }
}
