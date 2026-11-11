package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable

/**
 * The duels summary row's two inputs, offered together: [onOpen] opens the duels list and
 * [needYou] is the number of duels waiting on the player, read where the row composes.
 */
class HomeDuelsSummary(val onOpen: () -> Unit, val needYou: @Composable () -> Int)
