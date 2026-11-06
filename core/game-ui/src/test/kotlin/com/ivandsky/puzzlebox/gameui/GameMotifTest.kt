package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Test

class GameMotifTest {

    private val glyph = gameGlyph(name = "Probe", stroke = "M4 4H20", fill = "M12 12H13V13H12Z")

    private fun paths(vector: ImageVector) = vector.root.filterIsInstance<VectorPath>()

    @Test
    fun `every stroked path takes the motif stroke`() {
        val motif = glyph.withStroke(1.4f)
        assertEquals(listOf(1.4f, 1.4f), paths(motif).map { it.strokeLineWidth })
    }

    @Test
    fun `caps, joins, fills and path data survive the restroke`() {
        val before = paths(glyph)
        val after = paths(glyph.withStroke(1.6f))
        assertEquals(before.map { it.pathData }, after.map { it.pathData })
        assertEquals(before.map { it.fill }, after.map { it.fill })
        assertEquals(List(2) { StrokeCap.Round }, after.map { it.strokeLineCap })
        assertEquals(List(2) { StrokeJoin.Round }, after.map { it.strokeLineJoin })
    }

    @Test
    fun `viewport is unchanged so the 24 grid scales with size`() {
        val motif = glyph.withStroke(1.4f)
        assertEquals(glyph.viewportWidth, motif.viewportWidth)
        assertEquals(glyph.viewportHeight, motif.viewportHeight)
    }
}
