package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How many moves a focus board trains, as the Train feature asks it ([SudokuTrainer.trainedMoves]). */
class SudokuTrainerMovesTest {

    private val trainer = SudokuTrainerFixtures.trainer

    /** The trained turns a focus board takes when Show me plays it to its end: one per deduction the board trains. */
    private fun shownTurns(puzzle: Puzzle, technique: TechniqueId): Int? {
        val verdicts = mutableListOf<TrainerVerdict>()
        val board = trainer.practice(puzzle, technique, BoardKind.Focus, CoroutineScope(Job())) { verdicts += it } ?: return null
        var guard = 0
        while (board.status.value !is PracticeStatus.Done) {
            check(guard++ < 200) { "the board never ended" }
            board.showMe()
            board.gotIt()
        }
        return verdicts.size
    }

    /** Bug caught: a count that disagrees with what the board then asks, so a board preferred for its length ends after one move. */
    @Test
    fun `the count is the number of trained moves a focus board takes`() {
        var compared = 0
        for ((name, fixture) in SudokuTrainerFixtures.all) {
            val puzzle = SudokuTrainerFixtures.canonical(fixture)
            for (id in SudokuTechniqueIds.all) {
                val played = shownTurns(puzzle, id)
                assertEquals("$name ${id.value}", played, trainer.trainedMoves(puzzle, id))
                if (played != null) compared++
            }
        }
        assertTrue(compared > 0)
    }

    /** Bug caught: a count that is always one (or always many), which would make the preference a no-op or a lie. */
    @Test
    fun `the fixtures hold boards of one move and boards of several`() {
        val counts = SudokuTrainerFixtures.all.values.flatMap { fixture ->
            SudokuTechniqueIds.all.mapNotNull { trainer.trainedMoves(SudokuTrainerFixtures.canonical(fixture), it) }
        }

        assertTrue("some board trains a single move: $counts", counts.any { it == 1 })
        assertTrue("some board trains two or more: $counts", counts.any { it >= 2 })
    }

    /** Bug caught: a count for a puzzle that opens no board, which the view model would then try and fail to open. */
    @Test
    fun `no count where the puzzle gives no board`() {
        val mini = SudokuTrainerFixtures.canonical(SudokuTrainerFixtures.dailies.getValue("li:1"))
        val queens = Puzzle(GameId("queens"), mini.variant, mini.formatVersion, mini.payload)
        val broken = Puzzle(mini.gameId, mini.variant, mini.formatVersion, byteArrayOf(1, 2, 3))

        assertNull(trainer.trainedMoves(queens, SudokuTechniqueIds.all.first()))
        assertNull(trainer.trainedMoves(broken, SudokuTechniqueIds.all.first()))
        assertNull(trainer.trainedMoves(mini, TechniqueId("no-such-technique")))
    }
}
