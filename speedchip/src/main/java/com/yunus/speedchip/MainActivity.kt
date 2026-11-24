package com.yunus.speedchip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.platform.LocaleManager
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.settings.SettingsPage
import dev.cluvex.zedsecure.ui.settings.SettingsScreen
import dev.cluvex.zedsecure.ui.theme.ZedSecureTheme

class MainActivity : ComponentActivity() {
    private val repo get() = (application as SpeedChipApp).settings

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startSvc() }

    private fun startSvc() =
        startForegroundService(Intent(this, SpeedService::class.java))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            startSvc()
        else notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)

        val platform = AppPlatform(this)
        setContent {
            val s by repo.settings.collectAsStateWithLifecycle()
            val dark = when (s.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
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
                    SettingsScreen(
                        settings = s,
                        contentPadding = PaddingValues(),
                        onUpdate = { repo.update(it) },
                        onLanguage = { lang ->
                            repo.update { it.copy(language = lang) }
                            LocaleManager.reassert(lang.tag)
                            recreate()
                        },
                        modifier = Modifier.safeDrawingPadding(),
                        initialPage = SettingsPage.Ui,
                        onDeepLinkBack = { finish() },
                    )
                }
            }
        }
    }
}
