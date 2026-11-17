package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Community tab's Custom list, handed in by the app shell so this module needs no create edge (ADR-0008).
 * [content] lays out the whole Custom list on the [LazyListState] it is given and places `friends`, the From friends
 * rows, between its own Continue and Published rows. `friendsEmpty` says there is no friend puzzle, so a list with
 * nothing of its own can show one empty state instead. The friends rows pad their own sides and carry their own
 * vertical spacing, as the list's other rows must, and their keys start with `friend:`.
 *
 * On the Community tab's two-pane (the tablet) a row picks its puzzle for the right pane through
 * [LocalCommunityPicker] instead of pushing a page; [content] reads it when it opens a row. One of your own
 * published puzzles is shown there by [published], the published page for a share code that fills the pane given as
 * its modifier (null: the host has none, and such a row opens its page as it does on the phone).
 *
 * [yours] is the same list with only your own puzzles, for Community's Yours tab: New puzzle, Continue and Published
 * by you on the [LazyListState] it is given, or the first-time screen when there are none, with no friends and no
 * community rows (null: the host has no such list). [yoursSummary] is its count and the first published code, read
 * in composition as [DuelsSlot.needYou] is, so the tab can show the count and the tablet can pick a default.
 */
class CustomSlot(
    val published: (@Composable (code: String, modifier: Modifier) -> Unit)? = null,
    val yours: (@Composable (LazyListState) -> Unit)? = null,
    val yoursSummary: @Composable () -> YoursSummary = { YoursSummary.None },
    val content: @Composable (LazyListState, friendsEmpty: Boolean, friends: LazyListScope.() -> Unit) -> Unit,
)
