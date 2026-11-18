package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The rest of this composition's board: on when [enabled], which is [LocalSolveRestOnBoard] unless the caller says
 * otherwise. It confirms a solve either way, unless a host above confirms it ([LocalSolveConfirmHosted]).
 */
@Composable
fun rememberSolveRest(enabled: Boolean = LocalSolveRestOnBoard.current): SolveRestState {
    val haptics = LocalHapticFeedback.current
    val hostConfirms = LocalSolveConfirmHosted.current
    return remember(enabled, haptics, hostConfirms) { SolveRestState(enabled, haptics, hostConfirms) }
}
