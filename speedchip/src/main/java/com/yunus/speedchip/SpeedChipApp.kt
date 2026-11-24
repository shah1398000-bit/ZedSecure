package com.yunus.speedchip

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import dev.cluvex.zedsecure.core.platform.LocaleManager
import dev.cluvex.zedsecure.data.settings.SettingsRepository

class SpeedChipApp : Application(), SingletonImageLoader.Factory {
    val settings by lazy { SettingsRepository(this) }

    override fun attachBaseContext(base: Context) {
        CrashReporter.install(base)
        super.attachBaseContext(LocaleManager.wrap(base, SettingsRepository.readLanguageTag(base)))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.reassert(SettingsRepository.readLanguageTag(this))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
}
