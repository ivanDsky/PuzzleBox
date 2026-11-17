package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

/** Over the board while it [SolveRestState.resting]: a tap anywhere on it ends the rest. Place it last in a Box. */
@Composable
fun SolveRestTapLayer(state: SolveRestState, modifier: Modifier = Modifier) {
    if (state.resting) {
        Box(modifier.testTag("play.solve_rest").clickable(interactionSource = null, indication = null) { state.skip() })
    }
}

// Preview: no rest is running, so the layer draws nothing and the board under it stays as is (A8).
@PuzzleBoxPreviews
@Composable
internal fun SolveRestTapLayerPreview() {
    PuzzleBoxTheme {
        Box { SolveRestTapLayer(rememberSolveRest()) }
    }
}
