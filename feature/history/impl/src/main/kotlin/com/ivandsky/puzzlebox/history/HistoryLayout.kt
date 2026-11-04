package com.ivandsky.puzzlebox.history

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/** History's pane arrangement, derived from window width and orientation. */
enum class HistoryLayout {
    /** Compact and medium widths — and portrait at any width: one pane. */
    SinglePane,

    /** Expanded widths in landscape (tablet): list pane beside a detail pane. */
    TwoPane,
}

/**
 * The pane decision as a pure function (ticket 07 policy pattern, A17: layout logic
 * stays testable without a device). Two-pane list-detail needs Expanded (>= 840dp) AND
 * landscape — a large tablet held in portrait still classifies Expanded by width alone,
 * but a portrait pane has no height to share with a second one.
 */
fun historyLayoutFor(
    widthSizeClass: WindowWidthSizeClass,
    isLandscape: Boolean,
): HistoryLayout =
    if (widthSizeClass == WindowWidthSizeClass.Expanded && isLandscape) {
        HistoryLayout.TwoPane
    } else {
        HistoryLayout.SinglePane
    }
