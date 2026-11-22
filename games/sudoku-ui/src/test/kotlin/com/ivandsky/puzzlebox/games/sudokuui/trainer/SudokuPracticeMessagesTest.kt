package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The line a practice board gives for a move it undoes as Wrong: plain words, no answer given away. */
class SudokuPracticeMessagesTest {

    private fun why(rig: SudokuPracticeRig): String = (rig.status as PracticeStatus.Wrong).why

    @Test
    fun `the solution's note taken off says nothing rules it out, and names neither the square nor the answer`() {
        for (rig in SudokuPracticeRig.all()) {
            val move = rig.solutionNote()
            rig.play(move)
            assertEquals(
                rig.label,
                "Nothing rules ${move.digit} out of this square yet, so its note stays.",
                why(rig),
            )
        }
    }

    @Test
    fun `a wrong number never names a numbered box, and a clash in a box says this box`() {
        val numbered = Regex("""\bbox \d""", RegexOption.IGNORE_CASE)
        var clashes = 0
        for (rig in SudokuPracticeRig.all().distinctBy { it.name }) {
            for (move in rig.moves().filterIsInstance<SudokuTrainerMove.Place>()) {
                if (rig.judge(move) !is SudokuPracticeJudgement.Wrong) continue
                rig.play(move)
                val line = why(rig)
                assertFalse("${rig.label}: $line", numbered.containsMatchIn(line))
                assertFalse("${rig.label}: $line", line.contains("digit"))
                if (line.endsWith("in this box")) clashes++
            }
        }
        assertTrue("a clash in a box", clashes > 0)
    }

    @Test
    fun `a note put back is told notes only come off, and an erase is told it clears every note`() {
        var added = 0
        var erased = 0
        for (rig in SudokuPracticeRig.all()) {
            val removal = rig.find<SudokuPracticeJudgement.Right, SudokuTrainerMove.RemoveNote>() ?: continue
            rig.play(removal)
            if (rig.status is PracticeStatus.Done || rig.status is PracticeStatus.Shown) continue
            rig.state.select(removal.idx)
            rig.state.input(removal.digit)
            if (rig.status !is PracticeStatus.Wrong) continue
            assertEquals(
                rig.label,
                "Notes can only come off here. Turn Notes on and tap a number to take that one note off.",
                why(rig),
            )
            added++
        }
        for (rig in SudokuPracticeRig.all()) {
            val idx = rig.board.cells.indices.firstOrNull { ((rig.board.cells[it] as? SudokuCell.Notes)?.mask ?: 0).countOneBits() > 1 } ?: continue
            rig.state.select(idx)
            rig.state.erase()
            val status = rig.status as? PracticeStatus.Wrong ?: continue
            assertEquals(rig.label, "Erase clears every note. Take off one note at a time.", status.why)
            erased++
        }
        assertTrue("a note put back", added > 0)
        assertTrue("an erase", erased > 0)
    }

    @Test
    fun `a number put over a number is told squares take a number only when empty, never that erase clears notes`() {
        var over = 0
        for (rig in SudokuPracticeRig.all()) {
            val placed = rig.find<SudokuPracticeJudgement.Right, SudokuTrainerMove.Place>() ?: continue
            rig.play(placed)
            if (rig.status is PracticeStatus.Done || rig.status is PracticeStatus.Shown) continue
            if (rig.board.digitAt(placed.idx) != placed.digit) continue
            if (rig.state.session.notesMode) rig.state.toggleNotesMode()
            rig.state.select(placed.idx)
            rig.state.input(placed.digit % rig.puzzle.geometry.size + 1)
            val status = rig.status as? PracticeStatus.Wrong ?: continue
            assertEquals(rig.label, "That square already has a number. Put numbers on empty squares only.", status.why)
            over++
        }
        assertTrue("a number over a number", over > 0)
    }

    @Test
    fun `an undo is told it is not a step, and never that erase clears notes`() {
        var undone = 0
        for (rig in SudokuPracticeRig.all()) {
            val placed = rig.find<SudokuPracticeJudgement.Right, SudokuTrainerMove.Place>() ?: continue
            rig.play(placed)
            if (rig.status is PracticeStatus.Done || rig.status is PracticeStatus.Shown) continue
            if (rig.board.digitAt(placed.idx) != placed.digit) continue
            rig.state.undo()
            val status = rig.status as? PracticeStatus.Wrong ?: continue
            assertFalse(rig.label, status.why.startsWith("Erase"))
            assertEquals(rig.label, "That is not a step here. Take notes off one at a time, or put a number on an empty square.", status.why)
            undone++
        }
        assertTrue("an undo", undone > 0)
    }
}
