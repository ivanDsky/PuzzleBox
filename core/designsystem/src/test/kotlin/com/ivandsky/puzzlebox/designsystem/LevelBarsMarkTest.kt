package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Test

/** Four ascending bars on the 24 grid; a level fills as many as its rank and the rest stay faint. */
class LevelBarsMarkTest {

    private val marks = listOf(PuzzleIcons.LevelEasy, PuzzleIcons.LevelMedium, PuzzleIcons.LevelHard, PuzzleIcons.LevelExpert)

    private fun bars(mark: ImageVector) = mark.root.filterIsInstance<VectorPath>()

    @Test
    fun `every mark sits on the 24 grid with four bars`() {
        marks.forEach { mark ->
            assertEquals(24f, mark.viewportWidth)
            assertEquals(24f, mark.viewportHeight)
            assertEquals(4, bars(mark).size)
        }
    }

    @Test
    fun `Easy to Expert fill one to four bars`() {
        val filled = marks.map { mark -> bars(mark).count { it.fillAlpha == 1f } }
        assertEquals(listOf(1, 2, 3, 4), filled)
    }

    @Test
    fun `unfilled bars are drawn faint, left to right the filled ones come first`() {
        marks.forEachIndexed { rank, mark ->
            assertEquals(List(4) { if (it <= rank) 1f else 0.3f }, bars(mark).map { it.fillAlpha })
        }
    }
}
