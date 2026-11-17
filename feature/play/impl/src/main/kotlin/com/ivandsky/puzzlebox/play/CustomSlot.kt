package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Games screen's Custom tab, handed in by the app shell so this module needs no create
 * edge (ADR-0008); without one the pills stay Dailies, Friends, Duels. It acts only beside a
 * [DuelsSlot], which is what makes Games tabbed at all. [content] lays out the whole Custom
 * list on the [LazyListState] it is given (Games scrolls that list to its top on a section
 * request, a reselect and a return to the tab) and places `friends`, the From friends rows,
 * between its own Continue and Published rows. `friendsEmpty` says there is no friend puzzle,
 * so a list with nothing of its own can show one empty state instead. The friends rows pad
 * their own sides and carry their own vertical spacing, as the list's other rows must, and
 * their keys start with `friend:`; Games adds no side inset on the phone and keeps such rows
 * flush with the list pane on the tablet.
 *
 * On the Community tab's two-pane (the tablet) a row picks its puzzle for the right pane through
 * [LocalCommunityPicker] instead of pushing a page; [content] reads it when it opens a row. One of your own
 * published puzzles is shown there by [published], the published page for a share code that fills the pane given as
 * its modifier (null: the host has none, and such a row opens its page as it does on the phone).
 */
class CustomSlot(
    val published: (@Composable (code: String, modifier: Modifier) -> Unit)? = null,
    val content: @Composable (LazyListState, friendsEmpty: Boolean, friends: LazyListScope.() -> Unit) -> Unit,
)
