package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.GameGlyphs

/**
 * The icon each editor step shows on its tile. One constant per step, so the user's pick on the
 * device check is a one-line swap (alternatives: `PuzzleIcons.Create` for Path, `PuzzleIcons.Notes`
 * for Numbers & walls).
 */
internal object ZipEditorGlyphs {
    private val PathIcon: ImageVector = GameGlyphs.Zip
    private val NumbersAndWallsIcon: ImageVector = GameGlyphs.Sudoku

    fun icon(step: ZipEditorStep): ImageVector = when (step) {
        ZipEditorStep.Path -> PathIcon
        ZipEditorStep.NumbersAndWalls -> NumbersAndWallsIcon
    }
}
