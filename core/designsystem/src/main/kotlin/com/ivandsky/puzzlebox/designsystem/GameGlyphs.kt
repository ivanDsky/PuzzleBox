package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * One glyph per game, each a tiny picture of its board on the 24 grid (identity §2.5), so a
 * game reads apart from the others without its tint. A game's UI module hands its glyph to
 * its brand; Mini and Classic Sudoku share [Sudoku].
 */
object GameGlyphs {
    /** The snake path, a filled start dot and an open end dot. */
    val Zip: ImageVector = gameGlyph(
        name = "Zip",
        stroke = "M5 6H19V12H5V18H17M16.5 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
        fill = "M2.5 6a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
    )

    /** The crown and its base line; also the queen mark on the Queens board. */
    val Queens: ImageVector = gameGlyph(
        name = "Queens",
        stroke = "M4.5 17L4 8.5 8.5 12 12 6 15.5 12 20 8.5 19.5 17ZM5 20H19",
    )

    /** The board's sun: a filled disc with eight short rays. */
    val Tango: ImageVector = gameGlyph(
        name = "Tango",
        stroke = "M12 2.5V4.5M12 19.5V21.5M2.5 12H4.5M19.5 12H21.5" +
            "M5.28 5.28L6.7 6.7M17.3 17.3L18.72 18.72M5.28 18.72L6.7 17.3M17.3 6.7L18.72 5.28",
        fill = "M8.5 12a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0",
    )

    /** A rounded 3×3 grid with a filled centre cell. */
    val Sudoku: ImageVector = gameGlyph(
        name = "Sudoku",
        stroke = "M8 4H16A4 4 0 0 1 20 8V16A4 4 0 0 1 16 20H8A4 4 0 0 1 4 16V8A4 4 0 0 1 8 4Z" +
            "M9.33 4V20M14.67 4V20M4 9.33H20M4 14.67H20",
        fill = "M10.33 10.33H13.67V13.67H10.33Z",
    )

    /** A traced word: six letter beads joined by one winding stroke. */
    val Strands: ImageVector = gameGlyph(
        name = "Strands",
        stroke = "M5.5 18.5L11 15L7 10L12.5 5.5L18.5 8.5L17.5 15",
        fill = "M4.25 18.5a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0 -2.5 0" +
            "M10 15a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
            "M6 10a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
            "M11.5 5.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
            "M17.5 8.5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" +
            "M16.25 15a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0 -2.5 0",
    )
}

private val SheetCellWidth = 76.dp
private val SheetIconSize = 24.dp

// Glyph sheet for the catalog: the five game glyphs at 24dp in inkMuted with their names.
@ShowkaseComposable(name = "GameGlyphs", group = "Icons")
@PuzzleBoxPreviews
@Composable
internal fun GameGlyphsPreview() {
    PuzzleBoxTheme {
        val glyphs = listOf(
            "Zip" to GameGlyphs.Zip,
            "Queens" to GameGlyphs.Queens,
            "Tango" to GameGlyphs.Tango,
            "Sudoku" to GameGlyphs.Sudoku,
            "Strands" to GameGlyphs.Strands,
        )
        Row(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            glyphs.forEach { (name, glyph) ->
                Column(Modifier.width(SheetCellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(glyph, contentDescription = null, modifier = Modifier.size(SheetIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicText(name, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
        }
    }
}
