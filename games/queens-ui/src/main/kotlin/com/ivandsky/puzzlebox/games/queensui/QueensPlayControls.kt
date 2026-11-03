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
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint

/**
 * The board-adjacent control bar (§6): Undo (A12 provenance-aware), Hint (§5.5 staged
 * ladder), Clear (one full-board undo unit, fix round 6), and the A14 Auto-cross
 * settings toggle (ON auto-fills exclusion marks, OFF places queens bare). Every
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
        if (state.session.isComplete) {
            Button(onClick = { }, enabled = false) { Text("Solved") }
        }
    }
    // A14 revised (fix round 6): conflict highlighting is ALWAYS ON — the Auto-check
    // toggle is gone. While any violation is live, the stripes render on the board and
    // one line names the rule next to the controls (R-2, fix round 4). Same error ink
    // as the §3.5 stripes; reserved line, shown only while live.
    val model = QueensBoardModel.of(state.session, state.hint, state.hintPointed)
    if (model.violations.isNotEmpty()) {
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
    // §5.5 staged hint caption (fix round 6): the ladder's stage-1 DEDUCTION line —
    // why the forced cell is forced — rendered while the region pulses; without a
    // human-checkable reason the neutral fallback never claims logic. Same reserved-
    // line pattern as the violation caption; gone with the next board action.
    hintCaption(state.hint)?.let { caption ->
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("queens/hint_caption")
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
    }
}

/**
 * The §5.5 caption text for the active hint (fix round 6). The region is always named
 * by its palette color (the pulsed fill); the deduction names the unit whose every
 * other cell is blocked. null = nothing to show (no hint, or a no-op hint).
 */
internal fun hintCaption(hint: QueensHint?): String? = when (hint) {
    is QueensHint.Region -> {
        val color = QueensRegionPalette.hueName(hint.region)
        when (hint.reason?.kind) {
            HintReason.Kind.ROW -> "The $color region's queen must be in this row — every other cell there is blocked"
            HintReason.Kind.COLUMN -> "The $color region's queen must be in this column — every other cell there is blocked"
            HintReason.Kind.REGION -> "The $color region's queen must be here — every other cell in the region is blocked"
            // Solver-only forcing: the honest fallback (never a fabricated reason).
            null -> "Hint — the $color region"
        }
    }
    is QueensHint.Incorrect -> "Hint — these crowns can't all stay; remove at least one"
    QueensHint.None, null -> null
}
