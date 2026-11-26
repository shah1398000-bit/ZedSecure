package com.yunus.speedchip

import kotlinx.coroutines.flow.MutableStateFlow

object SpeedState {
    val running = MutableStateFlow(false)
    val vpn = MutableStateFlow(false)
    val down = MutableStateFlow(0L)
    val up = MutableStateFlow(0L)
}
