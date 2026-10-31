package com.ivandsky.puzzlebox.home

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/** Home's pane arrangement, derived from the window width. */
enum class HomeLayout {
    /** Compact and medium widths: one pane, content stacked vertically. */
    SinglePane,

    /** Expanded widths (tablet): list pane beside a detail pane. */
    TwoPane,
}

/**
 * The pane decision as a pure function (A16/A17: layout logic stays testable without
 * a device). Expanded (>= 840dp) earns list-detail; medium stays single-pane per the
 * Material 3 window-size guidance.
 */
fun homeLayoutFor(widthSizeClass: WindowWidthSizeClass): HomeLayout =
    if (widthSizeClass == WindowWidthSizeClass.Expanded) HomeLayout.TwoPane else HomeLayout.SinglePane
