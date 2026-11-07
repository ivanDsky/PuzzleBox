package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.segmentedButtonColors

/** The [controller]'s Crown | Paint toggle, shown in the tool strip's hint slot. */
@Composable
internal fun QueensModeToggle(controller: QueensEditorController, modifier: Modifier = Modifier) {
    val mode by controller.mode.collectAsState()
    QueensModeToggle(mode, controller::setMode, modifier)
}

/**
 * Two segments, **Crown** and **Paint** (tags `queens.editor.mode.crown` / `.paint`), in the
 * Studio's violet selection style; the [mode] segment is selected and a tap on either calls [onSelect].
 */
@Composable
internal fun QueensModeToggle(mode: QueensEditorMode, onSelect: (QueensEditorMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = QueensEditorMode.entries
    SingleChoiceSegmentedButtonRow(modifier.testTag("queens.editor.mode")) {
        modes.forEachIndexed { index, each ->
            SegmentedButton(
                selected = mode == each,
                onClick = { onSelect(each) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                colors = AccentSlot.Violet.segmentedButtonColors(),
                modifier = Modifier.testTag("queens.editor.mode.${each.name.lowercase()}"),
            ) {
                Text(
                    when (each) {
                        QueensEditorMode.Crown -> "Crown"
                        QueensEditorMode.Paint -> "Paint"
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Crown", widthDp = 360, showBackground = true, group = "Queens mode toggle")
@Composable
private fun QueensModeToggleCrownPreview() {
    PuzzleBoxTheme { QueensModeToggle(QueensEditorMode.Crown, onSelect = {}) }
}

@PuzzleBoxPreviews
@Preview(name = "Paint", widthDp = 360, showBackground = true, group = "Queens mode toggle")
@Composable
private fun QueensModeTogglePaintPreview() {
    PuzzleBoxTheme { QueensModeToggle(QueensEditorMode.Paint, onSelect = {}) }
}
