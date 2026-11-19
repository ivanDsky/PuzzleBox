package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable

/**
 * The Games screen's Train tab, handed in by the app shell so this module needs no train edge (ADR-0008): [content] is
 * the tab's whole body, filling the page it is given. On the tablet that page is the 400dp list column and the right
 * pane stays empty for this tab until the slot gains a pane of its own. The tab is shown only while the shell hands a
 * slot in: the app builds none while no game has a trainer, so Games never lists an empty Train tab.
 */
class TrainSlot(
    val content: @Composable () -> Unit,
)
