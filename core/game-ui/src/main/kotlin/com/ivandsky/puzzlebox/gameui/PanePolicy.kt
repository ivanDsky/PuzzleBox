package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one two-pane decision for the whole app, from the window size in dp. Home, History, Play,
 * Studio, Compete and the navigation shape all follow it; the replay compare shares the
 * constants but keeps its own width rule.
 */
object PanePolicy {
    /** Material's Expanded width class starts here. */
    val ExpandedMinWidth: Dp = 840.dp

    /** A phone in landscape is ~448 dp tall; two panes need more room than that. */
    val MinHeight: Dp = 480.dp

    /** Expanded width, landscape, and enough height for two panes. Portrait tablets stay single-pane. */
    fun isTwoPane(width: Dp, height: Dp): Boolean =
        width >= ExpandedMinWidth && width > height && height >= MinHeight
}
