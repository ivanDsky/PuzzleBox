package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.gameGlyph

/**
 * The icon each editor mode shows on its tile. One constant per mode, so the user's pick on the
 * device check is a one-line swap (alternative for Paint: `PuzzleIcons.Create`, the pencil).
 */
internal object QueensModeGlyphs {
    private val CrownIcon: ImageVector = GameGlyphs.Queens

    /** A rounded square with a filled patch: a region being painted. */
    val PaintIcon: ImageVector = gameGlyph(
        name = "QueensPaint",
        stroke = "M7 5H17A2 2 0 0 1 19 7V17A2 2 0 0 1 17 19H7A2 2 0 0 1 5 17V7A2 2 0 0 1 7 5Z",
        fill = "M9 9H15V15H9Z",
    )

    fun icon(mode: QueensEditorMode): ImageVector = when (mode) {
        QueensEditorMode.Crown -> CrownIcon
        QueensEditorMode.Paint -> PaintIcon
    }
}
