package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.segmentedButtonColors
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * The 2+ solutions card's witness controls (spec A7, 02 handoff card layout): the A/B
 * segments (`create.verdict.witness.a` / `.b`, the chosen one tinted and checked in the
 * Studio's violet, like the board's Crown | Paint toggle) pick which solution the card
 * describes, and **Show on board** (`create.verdict.showOnBoard`, a selected chip in the same
 * violet while on) has the board draw it. Both report through [onChange]. No motion: the
 * selection swaps at once.
 */
@Composable
internal fun VerdictWitness(overlay: EditorOverlay, onChange: (EditorOverlay) -> Unit, modifier: Modifier = Modifier) {
    val accent = AccentSlot.Violet.colors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val witnesses = EditorOverlay.Witness.entries
        SingleChoiceSegmentedButtonRow {
            witnesses.forEachIndexed { index, witness ->
                SegmentedButton(
                    selected = overlay.witness == witness,
                    onClick = { onChange(overlay.copy(witness = witness)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = witnesses.size),
                    colors = AccentSlot.Violet.segmentedButtonColors(),
                    modifier = Modifier.testTag("create.verdict.witness.${witness.name.lowercase()}"),
                ) { Text("Solution ${witness.name}", maxLines = 1) }
            }
        }
        FilterChip(
            selected = overlay.showOnBoard,
            onClick = { onChange(overlay.copy(showOnBoard = !overlay.showOnBoard)) },
            label = { Text("Show on board") },
            modifier = Modifier.testTag("create.verdict.showOnBoard"),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.container,
                selectedLabelColor = accent.onContainer,
            ),
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun VerdictWitnessPreview() {
    PuzzleBoxTheme {
        Surface { VerdictWitness(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), onChange = {}, Modifier.padding(Spacing.lg)) }
    }
}
