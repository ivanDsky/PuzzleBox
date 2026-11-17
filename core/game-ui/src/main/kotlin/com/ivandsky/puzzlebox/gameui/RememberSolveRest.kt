package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The rest of this composition's board, on or off by [LocalSolveRestOnBoard]; with [LocalPlayFeel] it confirms a solve
 * either way, unless a host above confirms it ([LocalSolveConfirmHosted]).
 */
@Composable
fun rememberSolveRest(): SolveRestState {
    val enabled = LocalSolveRestOnBoard.current
    val playFeel = LocalPlayFeel.current
    val haptics = LocalHapticFeedback.current
    val hostConfirms = LocalSolveConfirmHosted.current
    return remember(enabled, playFeel, haptics, hostConfirms) { SolveRestState(enabled, playFeel, haptics, hostConfirms) }
}
