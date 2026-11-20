package com.ivandsky.puzzlebox.create.editor

import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.gameGlyph
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * The icon each solution tile shows: the letters A and B, stroked on the 24 grid. One constant per
 * witness, so the user's pick on the device check is a one-line swap (alternative:
 * `PuzzleIcons.StatusRing` for A and `PuzzleIcons.StatusHalf` for B, which borrow the hub status).
 */
internal object WitnessGlyphs {
    val A: ImageVector = gameGlyph(name = "WitnessA", stroke = "M6 19L12 5L18 19M8.5 14H15.5")

    val B: ImageVector = gameGlyph(
        name = "WitnessB",
        stroke = "M8 5V19M8 5H12.5A3.25 3.25 0 0 1 12.5 11.5H8M8 11.5H13.5A3.75 3.75 0 0 1 13.5 19H8",
    )

    fun icon(witness: EditorOverlay.Witness): ImageVector = when (witness) {
        EditorOverlay.Witness.A -> A
        EditorOverlay.Witness.B -> B
    }
}
