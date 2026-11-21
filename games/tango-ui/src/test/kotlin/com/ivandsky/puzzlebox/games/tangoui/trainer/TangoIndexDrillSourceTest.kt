package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TangoIndexDrillSourceTest {

    private val sign = TechniqueId("sign")
    private val squeeze = TechniqueId("count-squeeze")

    private fun indexText(game: String = "tango"): String = TechniqueIndex.write(
        TechniqueIndex(
            gameId = GameId(game),
            techniqueSetVersion = 1,
            cutoff = LocalDate.of(2026, 11, 5),
            entries = mapOf(
                120 to mapOf(sign to 3),
                95 to mapOf(sign to 7, squeeze to 1),
                300 to mapOf(sign to 7),
                41 to mapOf(squeeze to 2),
            ),
        ),
    )

    @Test
    fun `candidates come most target steps first, ties by puzzle number`() {
        val source = TangoIndexDrillSource { indexText() }

        assertEquals(
            listOf(DrillCandidate.Archive(95, 7), DrillCandidate.Archive(300, 7), DrillCandidate.Archive(120, 3)),
            source.candidates(sign),
        )
        assertEquals(listOf(DrillCandidate.Archive(41, 2), DrillCandidate.Archive(95, 1)), source.candidates(squeeze))
    }

    @Test
    fun `a technique no puzzle in the index needs has no candidates`() {
        assertEquals(emptyList<DrillCandidate>(), TangoIndexDrillSource { indexText() }.candidates(TechniqueId("split-pair")))
    }

    @Test
    fun `no index, a broken index and another game's index offer nothing`() {
        assertEquals(emptyList<DrillCandidate>(), TangoIndexDrillSource { null }.candidates(sign))
        assertEquals(emptyList<DrillCandidate>(), TangoIndexDrillSource { "not an index" }.candidates(sign))
        assertEquals(emptyList<DrillCandidate>(), TangoIndexDrillSource { indexText(game = "queens") }.candidates(sign))
    }

    @Test
    fun `the shipped index is read once, on the first ask`() {
        var reads = 0
        val source = TangoIndexDrillSource { reads++; indexText() }

        assertEquals(0, reads)
        source.candidates(sign)
        source.candidates(squeeze)
        assertEquals(1, reads)
    }
}
