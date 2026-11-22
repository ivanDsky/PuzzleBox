package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A valid single out of ladder order stands without a word, and a review board ends a few moves after its last trained step. */
class SudokuPracticeSinglesTest {

    @Test
    fun `a valid single the rung does not make stands, with no undo, no band and nothing reported to the count`() {
        val kinds = mutableSetOf<BoardKind>()
        var onSingles = 0
        for (rig in SudokuPracticeRig.all()) {
            val move = rig.find<SudokuPracticeJudgement.Fits, SudokuTrainerMove.Place>() ?: continue
            val before = rig.board
            rig.play(move)

            assertEquals("${rig.label}: the digit stands", move.digit, rig.board.digitAt(move.idx))
            assertEquals("${rig.label}: only that digit went in", 1, before.cells.indices.count { before.digitAt(it) != rig.board.digitAt(it) })
            assertTrue("${rig.label}: ${rig.status}", rig.status is PracticeStatus.FindNextMove || rig.status is PracticeStatus.Done)
            assertTrue("${rig.label}: ${rig.verdicts}", rig.verdicts.isEmpty())
            kinds += rig.kind
            if (rig.technique.value.startsWith("hidden-single")) onSingles++
        }
        assertEquals(BoardKind.entries.toSet(), kinds)
        assertTrue("a hidden single chapter holds a single of another rung", onSingles > 0)
    }

    @Test
    fun `a placement that fits and is no single is still undone as Easier`() {
        var easier = 0
        for (rig in SudokuPracticeRig.all()) {
            val move = rig.find<SudokuPracticeJudgement.Easier, SudokuTrainerMove.Place>() ?: continue
            val before = rig.board
            rig.play(move)
            assertTrue("${rig.label}: ${rig.status}", rig.status is PracticeStatus.Easier)
            assertEquals(rig.label, before, rig.board)
            easier++
        }
        assertTrue(easier > 0)
    }

    @Test
    fun `a review board is done a few moves after its last trained step, with the puzzle unsolved and nothing trained ahead`() {
        var early = 0
        for (rig in SudokuPracticeRig.all(BoardKind.Review)) {
            val trained = SudokuTechniqueIds.all.indexOf(rig.technique)
            var since = 0
            var moves = 0
            while (rig.status !is PracticeStatus.Done) {
                val move = checkNotNull(rig.rightMove()) { "${rig.label}: no Right move after $moves" }
                val countedBefore = rig.verdicts.size
                rig.play(move)
                if (rig.verdicts.size > countedBefore) since = 0 else since++
                check(++moves < 500) { "${rig.label} did not finish" }
            }
            assertTrue("${rig.label}: $since moves since the last trained step", since <= SudokuPractice.REVIEW_GRACE)
            if (rig.state.session.isComplete) continue
            val next = SudokuPractice.showMe(rig.puzzle, rig.board)?.rung?.let { SudokuTechniqueIds.all.indexOf(it) }
            if (next != null && next <= trained) early++
        }
        assertTrue("some review board ends while a learned rung still has moves", early > 0)
    }
}
