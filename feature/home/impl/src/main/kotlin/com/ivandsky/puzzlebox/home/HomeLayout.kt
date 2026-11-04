package com.ivandsky.puzzlebox.home

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/** Home's pane arrangement, derived from window width and orientation. */
enum class HomeLayout {
    /** Compact and medium widths — and portrait at any width: one pane. */
    SinglePane,

    /** Expanded widths in landscape (tablet): list pane beside a detail pane. */
    TwoPane,
}

/**
 * The pane decision as a pure function (A16/A17: layout logic stays testable without
 * a device). Two-pane needs Expanded (>= 840dp) AND landscape: a large tablet held in
 * portrait still classifies Expanded by width alone, but a portrait pane has no height
 * to share with a second one. Medium stays single-pane per the Material 3 window-size
 * guidance.
 */
fun homeLayoutFor(
    widthSizeClass: WindowWidthSizeClass,
    isLandscape: Boolean,
): HomeLayout =
    if (widthSizeClass == WindowWidthSizeClass.Expanded && isLandscape) {
        HomeLayout.TwoPane
    } else {
        HomeLayout.SinglePane
    }
