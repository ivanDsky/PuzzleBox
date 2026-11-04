package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The Queens-specific controls (§6), hosted in the shared play chrome's `gameControls`
 * slot: Clear (one full-board command, fix round 6), the A14 Auto-cross settings toggle
 * (ON auto-fills exclusion marks, OFF places queens bare). The rule-conflict line
 * is the controller's PlayFeedback.violation, shown in the shared caption slot. Undo/redo, Hint (with its caption) and the timer are CORE chrome now. Every
 * control is a standard M3 component — the ≥48dp touch-target floor is enforced by the
 * components themselves (per-cell buttons inside the grid are forbidden, §8).
 */
@Composable
fun QueensPlayControls(
    state: QueensBoardState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Round-7 ruling: Clear is live when ANYTHING can be wiped — any queen OR any
        // mark (a crosses-only board must be clearable).
        OutlinedButton(
            onClick = { state.clearBoard() },
            enabled = !state.session.isComplete &&
                (state.session.queens.isNotEmpty() || state.session.marks.isNotEmpty()),
        ) {
            Text("Clear")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = state.autoCross, onCheckedChange = { state.setAutoCross(it) })
            Text(
                "Auto-cross",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
