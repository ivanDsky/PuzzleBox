package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.skeleton

/**
 * Holds the room of a [rows] by [cols] board while its puzzle loads, so the page does not jump when the board
 * arrives: one block with the same sheen as the skeleton bars around it. Still under reduced motion. It takes a
 * [maxSide] as the [HiddenBoard] it stands for does.
 */
@Composable
fun HiddenBoardLoading(
    modifier: Modifier = Modifier,
    rows: Int = 1,
    cols: Int = 1,
    maxSide: Dp = HiddenBoardTokens.MaxSide,
) {
    Box(modifier.hiddenBoardBounds(rows, cols, maxSide).semantics { contentDescription = "Loading board" }) {
        Box(
            Modifier
                .fillMaxSize()
                .skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium),
        )
    }
}
