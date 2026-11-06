package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * The 2+ solutions card's witness controls (spec A7, 02 handoff card layout): the A/B toggle
 * (`create.verdict.witness.a` / `.b`, the selected one in `inverseSurface`) picks which
 * solution the card describes, and **Show on board** (`create.verdict.showOnBoard`, a
 * selected chip while on) has the board draw it. Both report through [onChange]. No motion:
 * the selection swaps at once.
 */
@Composable
internal fun VerdictWitness(overlay: EditorOverlay, onChange: (EditorOverlay) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            EditorOverlay.Witness.entries.forEach { witness ->
                WitnessOption(witness, selected = overlay.witness == witness) { onChange(overlay.copy(witness = witness)) }
            }
        }
        FilterChip(
            selected = overlay.showOnBoard,
            onClick = { onChange(overlay.copy(showOnBoard = !overlay.showOnBoard)) },
            label = { Text("Show on board") },
            modifier = Modifier.testTag("create.verdict.showOnBoard"),
        )
    }
}

@Composable
private fun WitnessOption(witness: EditorOverlay.Witness, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = LocalMinimumInteractiveComponentSize.current)
            .testTag("create.verdict.witness.${witness.name.lowercase()}")
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
        shape = PillShape,
        color = if (selected) scheme.inverseSurface else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.inverseOnSurface else scheme.onSurface,
    ) {
        Row(Modifier.padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text("Solution ${witness.name}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun VerdictWitnessPreview() {
    PuzzleBoxTheme {
        Surface { VerdictWitness(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), onChange = {}, Modifier.padding(Spacing.lg)) }
    }
}
