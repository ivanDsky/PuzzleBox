package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.gameGlyph

/** The transport glyphs PuzzleIcons lacks: solid shapes on the 24 grid, drawn with the icon set's round 2dp stroke. */
internal object ReplayIcons {
    val Pause: ImageVector = gameGlyph("ReplayPause", stroke = "M8 7.5h1v9H8zM15 7.5h1v9h-1z", fill = "M8 7.5h1v9H8zM15 7.5h1v9h-1z")
    val Previous: ImageVector = gameGlyph("ReplayPrevious", stroke = "M6.5 6.5v11", fill = "M17 8v8L10 12z")
    val Next: ImageVector = gameGlyph("ReplayNext", stroke = "M17.5 6.5v11", fill = "M7 8v8l7-4z")
}
