package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Games screen's Train tab, handed in by the app shell so this module needs no train edge (ADR-0008): [content] is
 * the tab's whole body, filling the page it is given. On the tablet that page is the 400dp list column and the right
 * pane beside it is [pane] while the Train tab is shown, in place of a game's page: the open technique's round, filling
 * the modifier it is given. The body and the pane are one pair, so a shell that builds the body to leave the round to
 * the pane builds it only for a two-pane layout. The tab is shown only while the shell hands a slot in: the app builds
 * none while no game has a trainer, so Games never lists an empty Train tab.
 */
class TrainSlot(
    val content: @Composable () -> Unit,
    val pane: @Composable (Modifier) -> Unit,
)
