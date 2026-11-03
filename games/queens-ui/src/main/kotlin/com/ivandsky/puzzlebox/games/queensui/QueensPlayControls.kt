package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The board-adjacent control bar (§6): Undo (A12 provenance-aware), Hint (§5.5),
 * Clear, and the A14 Auto-check settings toggle. Every control is a standard M3
 * component — the ≥48dp touch-target floor is enforced by the components themselves
 * (per-cell buttons inside the grid are forbidden, §8).
 */
@Composable
fun QueensPlayControls(
    state: QueensBoardState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { state.undo() }, enabled = !state.session.isComplete && state.session.undo.isNotEmpty()) {
            Text("Undo")
        }
        OutlinedButton(onClick = { state.requestHint() }, enabled = !state.session.isComplete) {
            Text("Hint")
        }
        OutlinedButton(onClick = { state.clearBoard() }, enabled = !state.session.isComplete && state.session.queens.isNotEmpty()) {
            Text("Clear")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = state.autoCheck, onCheckedChange = { state.autoCheck = it })
            Text(
                "Auto-check",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.session.isComplete) {
            Button(onClick = { }, enabled = false) { Text("Solved") }
        }
    }
    // R-2 (fix round 4): the stripe cue alone confused players — while any violation
    // is live under Auto-check, one line names the rule next to the controls. Same
    // error ink as the §3.5 stripes; reserved line, shown only while live.
    if (state.autoCheck && QueensBoardModel.of(state.session, state.hint, true).violations.isNotEmpty()) {
        Text(
            "Rule conflict — queens can't touch or share a row, column, or region",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("queens/violation_caption")
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
    }
}
