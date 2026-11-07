package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hand-drawn outline marks share one stroke style, so they sit together in a row. */
class StrokedMarkTest {

    @Test
    fun `the copy mark is two outlined sheets stroked like the saved-on-device mark`() {
        val reference = paths(PuzzleIcons.SavedOnDevice).single()
        val copy = paths(PuzzleIcons.Copy)

        assertEquals(24f, PuzzleIcons.Copy.viewportWidth)
        assertEquals(24f, PuzzleIcons.Copy.viewportHeight)
        assertEquals(2, copy.size)
        copy.forEach {
            assertTrue("copy path is filled", it.fill == null)
            assertEquals(reference.strokeLineWidth, it.strokeLineWidth)
            assertEquals(reference.strokeLineCap, it.strokeLineCap)
            assertEquals(reference.strokeLineJoin, it.strokeLineJoin)
        }
    }

    private fun paths(icon: ImageVector): List<VectorPath> = icon.root.flatPaths()

    private fun VectorGroup.flatPaths(): List<VectorPath> = flatMap {
        when (it) {
            is VectorPath -> listOf(it)
            is VectorGroup -> it.flatPaths()
        }
    }
}
