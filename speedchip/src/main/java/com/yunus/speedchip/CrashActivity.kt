package com.yunus.speedchip

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val t = intent.getStringExtra("t") ?: "no trace"
        val tv = TextView(this).apply {
            text = t; textSize = 11f; setTextIsSelectable(true); setPadding(24, 24, 24, 24)
        }
        val copy = Button(this).apply {
            text = "Copy"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("crash", t))
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 140, 0, 0)
            addView(copy)
            addView(ScrollView(this@CrashActivity).apply { addView(tv) })
        }
        setContentView(root)
    }
}
