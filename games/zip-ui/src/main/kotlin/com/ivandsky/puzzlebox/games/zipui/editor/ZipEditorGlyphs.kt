package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.gameGlyph

/**
 * The icon each editor step shows on its tile. One constant per step, so the user's pick on the
 * device check is a one-line swap (alternatives: `PuzzleIcons.Create` for Path, `PuzzleIcons.Notes`
 * for the numbers steps).
 */
internal object ZipEditorGlyphs {
    private val PathIcon: ImageVector = GameGlyphs.Zip
    private val NumbersIcon: ImageVector = GameGlyphs.Sudoku
    private val NumbersAndWallsIcon: ImageVector = GameGlyphs.Sudoku

    /** A square with one thick edge: the wall the step draws. */
    val WallsIcon: ImageVector = gameGlyph(
        name = "ZipWalls",
        stroke = "M7 5H17A2 2 0 0 1 19 7V17A2 2 0 0 1 17 19H7A2 2 0 0 1 5 17V7A2 2 0 0 1 7 5Z",
        fill = "M16.5 6V18H18.5V6Z",
    )

    fun icon(step: ZipEditorStep): ImageVector = when (step) {
        ZipEditorStep.Path -> PathIcon
        ZipEditorStep.Numbers -> NumbersIcon
        ZipEditorStep.Walls -> WallsIcon
        ZipEditorStep.NumbersAndWalls -> NumbersAndWallsIcon
    }
}
