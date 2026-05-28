package com.example

import kotlinx.coroutines.flow.MutableStateFlow

object TimerState {
    val isRunning = MutableStateFlow(false)
    val remainingMillis = MutableStateFlow(0L)
    val totalMillis = MutableStateFlow(0L)
    val isOverlayActive = MutableStateFlow(false)
}
