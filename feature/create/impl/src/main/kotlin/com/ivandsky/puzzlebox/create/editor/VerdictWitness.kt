package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * violet while on) has the board draw it. Both report through [onChange]. Beside it the
 * **Auto-show** switch (`create.verdict.autoShow`, announced as a switch with its On/Off state)
 * reads the saved [autoShow] and reports a flip through [onAutoShow]; switching it on also puts
 * this verdict's witness on the board at once. The chip and the switch share a [FlowRow] with a
 * [Spacing.sm] gap, tight enough to keep one line on a 320 dp phone at default font size, so the
 * card still fits the half-open sheet; at a large font scale the switch wraps under the chip
 * instead of spilling past the card, and the sheet drags up. No motion: the selection swaps at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VerdictWitness(
    overlay: EditorOverlay,
    autoShow: Boolean,
    onChange: (EditorOverlay) -> Unit,
    onAutoShow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = overlay.showOnBoard,
                onClick = { onChange(overlay.copy(showOnBoard = !overlay.showOnBoard)) },
                label = { Text("Show on board", maxLines = 1) },
                modifier = Modifier.testTag("create.verdict.showOnBoard"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.container,
                    selectedLabelColor = accent.onContainer,
                ),
            )
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("create.verdict.autoShow")
                    .semantics { stateDescription = if (autoShow) "On" else "Off" }
                    .toggleable(value = autoShow, role = Role.Switch) { on ->
                        onAutoShow(on)
                        if (on && !overlay.showOnBoard) onChange(overlay.copy(showOnBoard = true))
                    },
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Auto-show",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.testTag("create.verdict.autoShow.label"),
                )
                Switch(
                    checked = autoShow,
                    onCheckedChange = null,
                    modifier = Modifier.testTag("create.verdict.autoShow.switch"),
                    colors = SwitchDefaults.colors(checkedTrackColor = accent.accent, checkedThumbColor = accent.onAccent),
                )
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun VerdictWitnessPreview() {
    PuzzleBoxTheme {
        Surface {
            VerdictWitness(
                EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true),
                autoShow = true,
                onChange = {},
                onAutoShow = {},
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}
