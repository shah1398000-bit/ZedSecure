package com.yunus.speedchip

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cluvex.zedsecure.core.platform.LocaleManager
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.NotifChip
import dev.cluvex.zedsecure.domain.model.ThemeMode
import dev.cluvex.zedsecure.ui.glass.GlassWallpaperLayer
import dev.cluvex.zedsecure.ui.glass.rememberGlassChrome
import dev.cluvex.zedsecure.ui.platform.LocalPlatform
import dev.cluvex.zedsecure.ui.settings.SettingsPage
import dev.cluvex.zedsecure.ui.settings.SettingsScreen
import dev.cluvex.zedsecure.ui.theme.ZedSecureTheme

class MainActivity : ComponentActivity() {
    private val repo get() = (application as SpeedChipApp).settings

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()

        val sp = getSharedPreferences("sc", MODE_PRIVATE)
        if (!sp.getBoolean("init2", false)) {
            repo.update { it.copy(liveNotification = true, speedInNotification = true, notifChip = NotifChip.Speed) }
            sp.edit().putBoolean("init2", true).apply()
        }
        if (Controls.wantsOn(this) && !SpeedState.running.value &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            Controls.start(this)

        val platform = AppPlatform(this)
        setContent {
            val s by repo.settings.collectAsStateWithLifecycle()
            var screen by rememberSaveable { mutableStateOf("home") }
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
                    val glassDesign = s.appDesign == AppDesign.Glass
                    val chrome = if (glassDesign) rememberGlassChrome() else null
                    Box(
                        Modifier.fillMaxSize().then(
                            if (glassDesign) Modifier
                            else Modifier.background(MaterialTheme.colorScheme.background)
                        )
                    ) {
                        if (chrome != null) GlassWallpaperLayer(chrome.wall)
                        if (screen == "home") {
                            HomeScreen(
                                onOpenSettings = { screen = "settings" },
                                modifier = Modifier.safeDrawingPadding(),
                            )
                        } else {
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
                                onDeepLinkBack = { screen = "home" },
                            )
                        }
                    }
                }
            }
        }
    }
}
