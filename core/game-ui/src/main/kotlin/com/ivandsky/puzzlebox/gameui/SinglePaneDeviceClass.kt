package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The single pane's device class, from the window the app picks its panes from. A single
 * pane the two-pane rule would have split still takes the single-pane tablet cap. Public for
 * a game whose single pane is its own scrolling page (Strands) and must cap its width alike.
 */
@Composable
fun singlePaneDeviceClass(): DeviceClass {
    val window = LocalWindowInfo.current.containerSize
    val deviceClass = with(LocalDensity.current) { deviceClassFor(window.width.toDp(), window.height.toDp()) }
    return if (deviceClass == DeviceClass.TabletLandscape) DeviceClass.TabletPortrait else deviceClass
}
