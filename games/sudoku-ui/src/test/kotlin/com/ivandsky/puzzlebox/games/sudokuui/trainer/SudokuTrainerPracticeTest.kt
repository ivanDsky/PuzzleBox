package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTrainerPracticeTest {

    private val trainer = SudokuTrainerFixtures.trainer
    private val mini = SudokuTrainerFixtures.canonical(SudokuTrainerFixtures.dailies.getValue("li:1"))

    private fun practice(puzzle: Puzzle, technique: TechniqueId, kind: BoardKind = BoardKind.Focus) =
        trainer.practice(puzzle, technique, kind, CoroutineScope(Job())) {}

    @Test
    fun `the trainer is Mini Sudoku's and offers the index and the generated drill sources`() {
        assertEquals(SudokuGames.MINI.id, trainer.gameId)
        assertEquals(2, trainer.drillSources.size)
        assertTrue(trainer.drillSources[0] is SudokuIndexDrillSource)
        assertTrue(trainer.drillSources[1] is SudokuGeneratedDrillSource)
    }

    @Test
    fun `every listed technique has a practice on some fixture`() {
        val reached = SudokuTrainerFixtures.all.values.flatMap { puzzle ->
            val canonical = SudokuTrainerFixtures.canonical(puzzle)
            trainer.catalog().map { it.id }.filter { practice(canonical, it) != null }
        }.toSet()

        assertEquals(trainer.catalog().map { it.id }.toSet(), reached)
    }

    @Test
    fun `a technique has a board of each kind exactly where it has one of the other, and each opens waiting for a move`() {
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            val canonical = SudokuTrainerFixtures.canonical(puzzle)
            for (id in SudokuTechniqueIds.all) {
                val boards = BoardKind.entries.map { practice(canonical, id, it) }
                assertEquals("$name ${id.value}", boards[0] == null, boards[1] == null)
                boards.filterNotNull().forEach { assertEquals("$name ${id.value}", PracticeStatus.FindNextMove, it.status.value) }
            }
        }
    }

    @Test
    fun `a puzzle of another game, a classic one, an undecodable one, an unsolvable one and an unknown technique give no board`() {
        val id = SudokuTechniqueIds.all.first { practice(mini, it) != null }
        val queens = Puzzle(GameId("queens"), mini.variant, mini.formatVersion, mini.payload)
        val classicId = Puzzle(SudokuGames.CLASSIC.id, mini.variant, mini.formatVersion, mini.payload)
        val classic = Puzzle(SudokuGames.MINI.id, mini.variant, mini.formatVersion, SudokuCodec.encode(SudokuSamples.classic))
        val broken = Puzzle(mini.gameId, mini.variant, mini.formatVersion, byteArrayOf(1, 2, 3))
        val first = SudokuTrainerFixtures.all.values.first()
        val open = SudokuPuzzle(first.variant, List(36) { 0 }, first.solution)
        val ambiguous = Puzzle(SudokuGames.MINI.id, mini.variant, mini.formatVersion, SudokuCodec.encode(open))

        assertNotNull(practice(mini, id))
        assertNull(practice(queens, id))
        assertNull("classic Sudoku never trains", practice(classicId, id))
        assertNull("a 9x9 board is no Mini Sudoku", practice(classic, id))
        assertNull(practice(broken, id))
        assertNull("no unique solution to judge by", practice(ambiguous, id))
        assertNull(practice(mini, TechniqueId("no-such-technique")))
    }

    @Test
    fun `a technique the puzzle never makes the easiest rung gives no board`() {
        val hiddenPair = SudokuTechniqueIds.all.single { it.value == "hidden-pair" }

        assertNull("li:1 is solved by singles alone", practice(mini, hiddenPair))
    }
}
