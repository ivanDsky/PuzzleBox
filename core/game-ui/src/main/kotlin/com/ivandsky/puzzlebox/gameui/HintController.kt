package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Hint bookkeeping (A3: hints are counted by the caller, score = time + hints). Plain
 * snapshot-state holder; the game owns WHEN a hint fires and what it shows — this only
 * keeps the count and exposes it to the button.
 */
class HintController(initialUsed: Int = 0) {

    var usedCount: Int by mutableIntStateOf(initialUsed)
        private set

    fun recordHint() {
        usedCount++
    }
}

/**
 * The hint affordance (F1 §3). [onHint] fires once per press; the screen applies the
 * game's hint via its rules and calls [HintController.recordHint] — one user-visible
 * hint action counts exactly once, including the Zip rescue flow where a free trim
 * precedes the step (F2 §10 ruling).
 */
@Composable
fun HintButton(
    hints: HintController,
    onHint: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onHint,
        modifier = modifier,
        enabled = enabled,
    ) {
        val count = hints.usedCount
        Text(if (count == 0) "Hint" else "Hint · $count")
    }
}
