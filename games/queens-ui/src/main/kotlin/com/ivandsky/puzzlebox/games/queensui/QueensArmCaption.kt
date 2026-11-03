package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign

/**
 * §5.3 armed-move helper caption (F-4, fix round 3): while a queen is armed for the
 * A11 move, one line names both continuations of the two-tap language. The slot is
 * ALWAYS reserved at [QueensBoardDefaults.armCaptionHeight] and empty when disarmed,
 * so arming never shifts the board.
 */
@Composable
fun QueensArmCaption(state: QueensBoardState, modifier: Modifier = Modifier) {
    val armed = state.pendingMoveCell != null
    Box(
        modifier
            .height(QueensBoardDefaults.armCaptionHeight)
            .testTag("queens/arm_caption"),
        contentAlignment = Alignment.Center,
    ) {
        if (armed) {
            Text(
                "Tap a cell to move · tap again to remove",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
