package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback

/** The rest of this composition's board, on or off by [LocalSolveRestOnBoard]; with [LocalPlayFeel] it confirms a solve either way. */
@Composable
fun rememberSolveRest(): SolveRestState {
    val enabled = LocalSolveRestOnBoard.current
    val playFeel = LocalPlayFeel.current
    val haptics = LocalHapticFeedback.current
    return remember(enabled, playFeel, haptics) { SolveRestState(enabled, playFeel, haptics) }
}
