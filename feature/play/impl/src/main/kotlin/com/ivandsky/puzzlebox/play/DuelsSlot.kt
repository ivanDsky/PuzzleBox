package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable

/**
 * The Games screen's Duels tab, handed in by the app shell so this module needs no compete
 * edge (ADR-0008): [content] is the tab's body, which lays its list out on the [LazyListState]
 * it is given (Games scrolls that list to its top on a section request and a reselect), and
 * [needYou] is the count the tab's badge shows (0 shows nothing).
 */
class DuelsSlot(val content: @Composable (LazyListState) -> Unit, val needYou: @Composable () -> Int)
