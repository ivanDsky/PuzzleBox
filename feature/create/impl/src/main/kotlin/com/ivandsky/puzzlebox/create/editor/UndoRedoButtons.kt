package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * Undo and Redo as two compact icon buttons: the tool strip's own tools at 48dp without their
 * labels, for the publish details' header while the strip is away. Same tags (`create.tool.undo`,
 * `create.tool.redo`), enabled state ([canUndo], [canRedo]) and callbacks as the strip's.
 */
@Composable
fun UndoRedoButtons(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        IconTool("Undo", PuzzleIcons.Undo, canUndo, onUndo, "create.tool.undo", compact = true)
        IconTool("Redo", PuzzleIcons.Redo, canRedo, onRedo, "create.tool.redo", compact = true)
    }
}

/** Mid-edit after an undo: both steps exist. */
@PuzzleBoxPreviews
@Composable
private fun UndoRedoButtonsPreview() {
    PuzzleBoxTheme {
        UndoRedoButtons(canUndo = true, canRedo = true, onUndo = {}, onRedo = {})
    }
}

/** A fresh draft: nothing to undo or redo. */
@PuzzleBoxPreviews
@Composable
private fun UndoRedoButtonsFreshPreview() {
    PuzzleBoxTheme {
        UndoRedoButtons(canUndo = false, canRedo = false, onUndo = {}, onRedo = {})
    }
}
