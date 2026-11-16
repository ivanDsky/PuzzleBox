package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.compete.api.DuelKey

/**
 * The Games screen's Duels tab, handed in by the app shell so this module needs no compete
 * edge (ADR-0008): [content] is the tab's body, which lays its list out on the [LazyListState]
 * it is given (Games scrolls that list to its top on a section request and a reselect).
 * It is also given the pending [DuelPrefill] (a challenge to a friend in a game; null for none), and
 * calls the third argument once it has taken it, so the prefill acts once. [needYou] is the count the tab's badge
 * shows (0 shows nothing), and [onOpenDuel] is what the Enter-code sheet calls when an entered code names a duel.
 */
class DuelsSlot(
    val content: @Composable (LazyListState, DuelPrefill?, () -> Unit) -> Unit,
    val needYou: @Composable () -> Int,
    val onOpenDuel: (DuelKey) -> Unit,
)
