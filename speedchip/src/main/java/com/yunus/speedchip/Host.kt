package com.yunus.speedchip

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import dev.cluvex.zedsecure.domain.model.AppDesign
import dev.cluvex.zedsecure.domain.model.AppLanguage
import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.NavBarStyle
import dev.cluvex.zedsecure.ui.bento.BentoDock
import dev.cluvex.zedsecure.ui.bento.BentoDockHeight
import dev.cluvex.zedsecure.ui.bento.BentoDockMargin
import dev.cluvex.zedsecure.ui.glass.GlassSearchState
import dev.cluvex.zedsecure.ui.glass.GlassTabBar
import dev.cluvex.zedsecure.ui.glass.GlassTabBarHeight
import dev.cluvex.zedsecure.ui.glass.GlassTabBarMargin
import dev.cluvex.zedsecure.ui.glass.GlassTopEdge
import dev.cluvex.zedsecure.ui.glass.GlassWallpaperLayer
import dev.cluvex.zedsecure.ui.glass.LocalGlassSearch
import dev.cluvex.zedsecure.ui.glass.rememberGlassChrome
import dev.cluvex.zedsecure.ui.glass.rememberGlassTilt
import dev.cluvex.zedsecure.ui.navigation.TopDestination
import dev.cluvex.zedsecure.ui.navigation.ZedNavBar
import dev.cluvex.zedsecure.ui.settings.SettingsPage
import dev.cluvex.zedsecure.ui.settings.SettingsScreen

private val Tabs = listOf(TopDestination.Home, TopDestination.Settings)

@Composable
fun SpeedChipHost(
    s: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf(TopDestination.Home) }
    val glass = s.appDesign == AppDesign.Glass
    val bento = s.appDesign == AppDesign.Bento
    val barStyle = if (bento) s.bento.navStyle else s.navBarStyle
    val dock = bento && barStyle == NavBarStyle.Dock
    val chrome = if (glass) rememberGlassChrome() else null
    val search = remember { GlassSearchState() }

    BackHandler(enabled = current != TopDestination.Home) { current = TopDestination.Home }

    Box(Modifier.fillMaxSize()) {
        if (chrome != null) GlassWallpaperLayer(chrome.wall)
        Scaffold(
            containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                if (!dock && !glass) {
                    ZedNavBar(style = barStyle, tabs = Tabs, current = current, onSelect = { current = it })
                }
            },
        ) { scaffoldPadding ->
            val ld = LocalLayoutDirection.current
            val inner = if (dock || glass) {
                PaddingValues(
                    start = scaffoldPadding.calculateStartPadding(ld),
                    top = scaffoldPadding.calculateTopPadding(),
                    end = scaffoldPadding.calculateEndPadding(ld),
                    bottom = scaffoldPadding.calculateBottomPadding() + if (glass) {
                        GlassTabBarHeight + GlassTabBarMargin + 10.dp
                    } else {
                        BentoDockHeight + BentoDockMargin + 8.dp
                    },
                )
            } else scaffoldPadding

            Box(
                Modifier.fillMaxSize().then(
                    if (chrome != null) Modifier.layerBackdrop(chrome.page) else Modifier
                )
            ) {
                CompositionLocalProvider(LocalGlassSearch provides (if (glass) search else null)) {
                    Crossfade(targetState = current, label = "tab") { dest ->
                        when (dest) {
                            TopDestination.Settings -> SettingsScreen(
                                settings = s,
                                contentPadding = inner,
                                onUpdate = onUpdate,
                                onLanguage = onLanguage,
                                modifier = Modifier,
                                initialPage = SettingsPage.Ui,
                                onDeepLinkBack = { current = TopDestination.Home },
                            )
                            else -> SpeedHome(s, inner)
                        }
                    }
                }
            }
        }

        if (dock) {
            BentoDock(
                tabs = Tabs,
                current = current,
                onSelect = { current = it },
                visible = true,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (chrome != null) {
            GlassTopEdge(chrome.combined, Modifier.align(Alignment.TopCenter))
            GlassTabBar(
                tabs = Tabs,
                current = current,
                onSelect = { search.close(); current = it },
                backdrop = chrome.combined,
                minimized = false,
                onExpand = {},
                search = search,
                onOpenSearch = {},
                modifier = Modifier.align(Alignment.BottomCenter),
                tilt = rememberGlassTilt(s.glass.motionHighlights),
            )
        }
    }
}
