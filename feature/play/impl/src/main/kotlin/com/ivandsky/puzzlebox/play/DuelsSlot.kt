package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable

/**
 * The Games screen's Duels tab, handed in by the app shell so this module needs no compete
 * edge (ADR-0008): [content] is the tab's body, which owns its own list and scrolling, and
 * [needYou] is the count the tab's badge shows (0 shows nothing).
 */
class DuelsSlot(val content: @Composable () -> Unit, val needYou: @Composable () -> Int)
