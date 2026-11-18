package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The window's size class for the play screen: which board cap applies (nav-rethink B5, B6). */
enum class DeviceClass {
    /** Anything below the tablet width, a phone in landscape included. */
    Phone,

    /** A tablet in a single pane: shortest side of at least 600 dp, not two-pane. */
    TabletPortrait,

    /** The two-pane window, as [PanePolicy] decides it. */
    TabletLandscape,
}

private val TabletMinShortestSide: Dp = 600.dp

/** The [DeviceClass] of a window [width] by [height]; the two-pane rule is [PanePolicy]'s, not repeated here. */
fun deviceClassFor(width: Dp, height: Dp): DeviceClass = when {
    PanePolicy.isTwoPane(width, height) -> DeviceClass.TabletLandscape
    minOf(width, height) >= TabletMinShortestSide -> DeviceClass.TabletPortrait
    else -> DeviceClass.Phone
}
