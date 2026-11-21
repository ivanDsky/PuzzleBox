package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuIndexDrillSourceTest {

    private val naked = SudokuTechniqueIds.byTechnique.getValue(SudokuTechnique.NAKED_SINGLE)
    private val pointing = SudokuTechniqueIds.byTechnique.getValue(SudokuTechnique.POINTING)

    private fun indexText(game: GameId = SudokuGames.MINI.id): String = TechniqueIndex.write(
        TechniqueIndex(
            gameId = game,
            techniqueSetVersion = 1,
            cutoff = LocalDate.of(2026, 11, 5),
            entries = mapOf(
                120 to mapOf(naked to 3),
                95 to mapOf(naked to 7, pointing to 1),
                300 to mapOf(naked to 7),
                41 to mapOf(pointing to 2),
            ),
        ),
    )

    @Test
    fun `candidates come most target steps first, ties by puzzle number`() {
        val source = SudokuIndexDrillSource { indexText() }

        assertEquals(
            listOf(DrillCandidate.Archive(95, 7), DrillCandidate.Archive(300, 7), DrillCandidate.Archive(120, 3)),
            source.candidates(naked),
        )
        assertEquals(listOf(DrillCandidate.Archive(41, 2), DrillCandidate.Archive(95, 1)), source.candidates(pointing))
    }

    @Test
    fun `a technique no puzzle in the index needs has no candidates`() {
        assertEquals(emptyList<DrillCandidate>(), SudokuIndexDrillSource { indexText() }.candidates(TechniqueId("hidden-pair")))
    }

    @Test
    fun `no index, a broken index and another game's index offer nothing`() {
        assertEquals(emptyList<DrillCandidate>(), SudokuIndexDrillSource { null }.candidates(naked))
        assertEquals(emptyList<DrillCandidate>(), SudokuIndexDrillSource { "not an index" }.candidates(naked))
        assertEquals(emptyList<DrillCandidate>(), SudokuIndexDrillSource { indexText(game = GameId("tango")) }.candidates(naked))
        assertEquals("classic Sudoku's index is no Mini's", emptyList<DrillCandidate>(), SudokuIndexDrillSource { indexText(game = SudokuGames.CLASSIC.id) }.candidates(naked))
    }

    @Test
    fun `the shipped index is read once, on the first ask`() {
        var reads = 0
        val source = SudokuIndexDrillSource { reads++; indexText() }

        assertEquals(0, reads)
        source.candidates(naked)
        source.candidates(pointing)
        assertEquals(1, reads)
    }
}
