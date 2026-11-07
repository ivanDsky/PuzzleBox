package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Test

/** Each game has its own glyph on the 24 grid, all stroked in the one icon style. */
class GameGlyphsTest {

    private val all = listOf(GameGlyphs.Zip, GameGlyphs.Queens, GameGlyphs.Tango, GameGlyphs.Sudoku, GameGlyphs.Strands)

    @Test
    fun `the five games draw five different glyphs`() {
        assertEquals(all.size, all.map { glyph -> glyph.root.filterIsInstance<VectorPath>().map { it.pathData } }.toSet().size)
    }

    @Test
    fun `every glyph sits on the 24 grid with a 2dp round stroke`() {
        all.forEach { glyph ->
            assertEquals(24f, glyph.viewportWidth)
            assertEquals(24f, glyph.viewportHeight)
            glyph.root.filterIsInstance<VectorPath>().forEach {
                assertEquals(glyph.name, 2f, it.strokeLineWidth)
                assertEquals(StrokeCap.Round, it.strokeLineCap)
                assertEquals(StrokeJoin.Round, it.strokeLineJoin)
            }
        }
    }
}
