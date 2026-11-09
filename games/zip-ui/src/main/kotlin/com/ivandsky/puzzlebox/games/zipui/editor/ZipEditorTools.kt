package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.segmentedButtonColors
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft

/**
 * Under the grid: the step tabs (tags `zip.editor.step.<path|numbers|walls>`) and the current
 * step's actions. Path offers Reverse and Clear path (tags `zip.editor.reverse`,
 * `zip.editor.clearPath`); Walls offers Clear walls (tag `zip.editor.clearWalls`).
 */
@Composable
internal fun ZipEditorTools(controller: ZipEditorController, modifier: Modifier = Modifier) {
    val step by controller.step.collectAsState()
    ZipEditorTools(step, controller::select, controller::reverse, controller::clearPath, controller::clearWalls, modifier)
}

@Composable
internal fun ZipEditorTools(
    step: ZipEditorStep,
    onSelect: (ZipEditorStep) -> Unit,
    onReverse: () -> Unit,
    onClearPath: () -> Unit,
    onClearWalls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val steps = ZipEditorStep.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("zip.editor.step")) {
            steps.forEachIndexed { index, each ->
                SegmentedButton(
                    selected = step == each,
                    onClick = { onSelect(each) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = steps.size),
                    colors = AccentSlot.Violet.segmentedButtonColors(),
                    modifier = Modifier.testTag("zip.editor.step.${each.name.lowercase()}"),
                ) {
                    Text(each.name, maxLines = 1)
                }
            }
        }
        if (step == ZipEditorStep.Path) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onReverse, Modifier.testTag("zip.editor.reverse")) { Text("Reverse") }
                TextButton(onClearPath, Modifier.testTag("zip.editor.clearPath")) { Text("Clear path") }
            }
        }
        if (step == ZipEditorStep.Walls) {
            TextButton(onClearWalls, Modifier.testTag("zip.editor.clearWalls")) { Text("Clear walls") }
        }
    }
}

/**
 * How much of the board the path covers, [covered] of [total] cells, and the step the stepper
 * (04 handoff: 1 Path → 2 Numbers → 3 Walls) asks for next, or null while the path is short
 * or once every step has work on it.
 */
internal data class ZipEditorHintState(val covered: Int, val total: Int, val next: ZipEditorStep? = null) {
    companion object {
        fun of(board: ZipDraft): ZipEditorHintState {
            val total = board.n * board.n
            val next = when {
                board.path.size < total -> null
                board.waypoints.size <= ENDS -> ZipEditorStep.Numbers
                board.walls.isEmpty() -> ZipEditorStep.Walls
                else -> null
            }
            return ZipEditorHintState(board.path.size, total, next)
        }

        /** The two path ends, always numbered; more numbers means the author marked a middle cell. */
        private const val ENDS = 2
    }
}

/**
 * The context line beside the tool strip: how many cells the path covers (tag
 * `zip.editor.hint`) and, once it covers the board, the step to take next (tag
 * `zip.editor.hint.next`).
 */
@Composable
internal fun ZipEditorHint(controller: ZipEditorController, modifier: Modifier = Modifier) {
    val board by controller.shown.collectAsState()
    ZipEditorHint(ZipEditorHintState.of(board), modifier)
}

@Composable
internal fun ZipEditorHint(state: ZipEditorHintState, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "${state.covered} of ${state.total} cells",
            Modifier.testTag("zip.editor.hint"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.next?.let { next ->
            Text(
                "Next: ${next.name}",
                Modifier.testTag("zip.editor.hint.next"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Zip tools, Path", widthDp = 360, showBackground = true, group = "Zip editor tools")
@Composable
private fun ZipEditorToolsPathPreview() {
    PuzzleBoxTheme {
        Column {
            ZipEditorTools(ZipEditorStep.Path, {}, {}, {}, {})
            ZipEditorHint(ZipEditorHintState(covered = 18, total = 25))
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Zip tools, Numbers", widthDp = 360, showBackground = true, group = "Zip editor tools")
@Composable
private fun ZipEditorToolsNumbersPreview() {
    PuzzleBoxTheme {
        Column {
            ZipEditorTools(ZipEditorStep.Numbers, {}, {}, {}, {})
            ZipEditorHint(ZipEditorHintState(covered = 25, total = 25, next = ZipEditorStep.Numbers))
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Zip tools, Walls", widthDp = 360, showBackground = true, group = "Zip editor tools")
@Composable
private fun ZipEditorToolsWallsPreview() {
    PuzzleBoxTheme { ZipEditorTools(ZipEditorStep.Walls, {}, {}, {}, {}) }
}
