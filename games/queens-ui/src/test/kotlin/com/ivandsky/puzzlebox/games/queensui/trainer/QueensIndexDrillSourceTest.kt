package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class QueensIndexDrillSourceTest {

    private val singles = TechniqueId("singles")
    private val squeeze = TechniqueId("squeeze")

    private fun indexText(game: String = "queens"): String = TechniqueIndex.write(
        TechniqueIndex(
            gameId = GameId(game),
            techniqueSetVersion = 1,
            cutoff = LocalDate.of(2026, 11, 2),
            entries = mapOf(
                120 to mapOf(singles to 3),
                95 to mapOf(singles to 7, squeeze to 1),
                300 to mapOf(singles to 7),
                41 to mapOf(squeeze to 2),
            ),
        ),
    )

    @Test
    fun `candidates come most target steps first, ties by puzzle number`() {
        val source = QueensIndexDrillSource { indexText() }

        assertEquals(
            listOf(DrillCandidate.Archive(95, 7), DrillCandidate.Archive(300, 7), DrillCandidate.Archive(120, 3)),
            source.candidates(singles),
        )
        assertEquals(listOf(DrillCandidate.Archive(41, 2), DrillCandidate.Archive(95, 1)), source.candidates(squeeze))
    }

    @Test
    fun `a technique no puzzle in the index needs has no candidates`() {
        assertEquals(emptyList<DrillCandidate>(), QueensIndexDrillSource { indexText() }.candidates(TechniqueId("long-look-ahead")))
    }

    @Test
    fun `no index, a broken index and another game's index offer nothing`() {
        assertEquals(emptyList<DrillCandidate>(), QueensIndexDrillSource { null }.candidates(singles))
        assertEquals(emptyList<DrillCandidate>(), QueensIndexDrillSource { "not an index" }.candidates(singles))
        assertEquals(emptyList<DrillCandidate>(), QueensIndexDrillSource { indexText(game = "tango") }.candidates(singles))
    }

    @Test
    fun `the shipped index is read once, on the first ask`() {
        var reads = 0
        val source = QueensIndexDrillSource { reads++; indexText() }

        assertEquals(0, reads)
        source.candidates(singles)
        source.candidates(squeeze)
        assertEquals(1, reads)
    }

    @Test
    fun `this build ships no index yet, so the default source offers nothing`() {
        assertEquals(emptyList<DrillCandidate>(), QueensIndexDrillSource().candidates(singles))
    }
}
