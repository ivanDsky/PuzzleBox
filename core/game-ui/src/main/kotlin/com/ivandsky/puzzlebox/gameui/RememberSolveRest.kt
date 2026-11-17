package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** The rest of this composition's board, on or off by [LocalSolveRestOnBoard]. */
@Composable
fun rememberSolveRest(): SolveRestState {
    val enabled = LocalSolveRestOnBoard.current
    val view = LocalView.current
    return remember(enabled, view) { SolveRestState(enabled, view) }
}
