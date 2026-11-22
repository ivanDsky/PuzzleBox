package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.model.GameId

/**
 * The Community tab's Custom seams, handed in by the app shell so this module needs no create edge (ADR-0008).
 *
 * On the Community tab's two-pane (the tablet) a row picks its puzzle for the right pane through
 * [LocalCommunityPicker] instead of pushing a page. One of your own published puzzles is shown there by [published],
 * the published page for a share code that fills the pane given as its modifier (null: the host has none, and such
 * a row opens its page as it does on the phone).
 *
 * [yours] is the list of only your own puzzles, for Community's Yours tab: New puzzle, Continue and Published
 * by you on the [LazyListState] it is given, or the first-time screen when there are none (null: the host has no
 * such list). [yoursSummary] is its count and the first published code, read in composition as [DuelsSlot.needYou] is, so the tab can show the count and the tablet can pick a default.
 *
 * [newPuzzle] is the New puzzle sheet's host, behind the create FAB flag (null: the host has no such action, so no FAB).
 * It wraps the Community screen and gives its content the call that opens the sheet, and it is told the [Dp] the FAB
 * covers at a list's end, which its own Yours list leaves free and lifts its snackbar by. With the host in place
 * the Yours list has no New puzzle row of its own. The call takes a nullable [GameId]: null opens the New puzzle sheet,
 * a game starts a new draft of it at once, with no sheet, and opens its Studio (Community's "Make a puzzle" cards).
 */
class CustomSlot(
    val published: (@Composable (code: String, modifier: Modifier) -> Unit)? = null,
    val yours: (@Composable (LazyListState) -> Unit)? = null,
    val yoursSummary: @Composable () -> YoursSummary = { YoursSummary.None },
    val newPuzzle: (@Composable (clearance: Dp, content: @Composable (onNewPuzzle: (GameId?) -> Unit) -> Unit) -> Unit)? = null,
)
