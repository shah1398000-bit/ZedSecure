package com.yunus.speedchip

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.platform.LocaleManager
import dev.cluvex.zedsecure.domain.model.NotifChip
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.theme.ZedSecureTheme

class MainActivity : ComponentActivity() {
    private val repo get() = (application as SpeedChipApp).settings

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok && Controls.wantsOn(this)) Controls.start(this)
        }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()

        val sp = getSharedPreferences("sc", MODE_PRIVATE)
        if (!sp.getBoolean("init2", false)) {
            repo.update { it.copy(liveNotification = true, speedInNotification = true, notifChip = NotifChip.Speed) }
            sp.edit().putBoolean("init2", true).apply()
        }
        if (!sp.getBoolean("init3", false)) {
            repo.update {
                it.copy(
                    reduceMotion = true,
                    glass = it.glass.copy(reduceTransparency = true),
                    bento = it.bento.copy(wave = false, motifs = false, playfulTouch = false),
                )
            }
            sp.edit().putBoolean("init3", true).apply()
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Controls.wantsOn(this) && !SpeedState.running.value) {
            Controls.start(this)
        }

        val platform = AppPlatform(this)
        setContent {
            val s by repo.settings.collectAsStateWithLifecycle()
            val dark = when (s.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
                else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val langTag = s.language.tag ?: resources.configuration.locales[0].language
            ZedSecureTheme(
                darkTheme = dark,
                dynamicColor = s.dynamicColor,
                accentColor = s.accentColor,
                amoledBlack = s.amoledBlack,
                colorStyle = s.colorStyle,
                colorContrast = s.colorContrast,
                design = s.appDesign,
                bento = s.bento,
                glass = s.glass,
                animateColors = !s.reduceMotion,
                languageTag = langTag,
                fontScale = s.uiFontScale.scale,
                font = s.uiFont,
                uiScale = s.uiScale,
            ) {
                CompositionLocalProvider(LocalPlatform provides platform) {
                    SpeedChipHost(
                        s = s,
                        onUpdate = { repo.update(it) },
                        onLanguage = { lang ->
                            repo.update { it.copy(language = lang) }
                            LocaleManager.reassert(lang.tag)
                            recreate()
                        },
                    )
                }
            }
        }
    }
}
