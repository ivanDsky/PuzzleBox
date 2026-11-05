package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure renderer-model mapping tests: session + hint + stage-2 flag → drawable model.
class QueensBoardModelTest {

    private fun manualX(session: QueensSession, idx: Int): QueensSession =
        session.withCrossToggled(Cell(idx), QueensUiFixtures.T0)

    @Test
    fun `marks flatten by provenance`() {
        val s = manualX(QueensUiFixtures.session(), 5) // MANUAL X
        val placed = s.withQueenPlaced(Cell(1), QueensUiFixtures.T0) // auto-fills row 1
        val model = QueensBoardModel.of(placed)
        assertEquals(QueensCellVisual.X_MANUAL, model.visuals[5])
        assertEquals(QueensCellVisual.QUEEN, model.visuals[1])
        // AUTO exclusion on the same row as the queen, where no manual mark stands.
        assertEquals(QueensCellVisual.X_AUTO, model.visuals[2]) // same ROW as the queen (region = row)
        assertEquals(QueensCellVisual.X_AUTO, model.visuals[4]) // diagonal 3×3 neighbor (round-7 ruling)
        assertEquals(QueensCellVisual.EMPTY, model.visuals[10]) // outside every fill unit
    }

    /** Fix round 6 (A14 revised): conflict highlighting is ALWAYS ON — no toggle. */
    @Test
    fun `violations are computed always - no auto-check gate`() {
        val s = QueensUiFixtures.session()
            .withQueenPlaced(Cell(1), QueensUiFixtures.T0)
            .withQueenPlaced(Cell(2), QueensUiFixtures.T0) // same row → row violation
        assertTrue(QueensBoardModel.of(s).violations.isNotEmpty())
        // and a clean board maps to none
        assertTrue(QueensBoardModel.of(QueensUiFixtures.session()).violations.isEmpty())
    }

    /** Spec A12: a guided lesson shows the answer squares still open and no stripes. */
    @Test
    fun `a guide ghosts the open answer squares and holds back the stripes`() {
        val answer = QueensUiFixtures.solutionCells.map { it.idx }.toSet()
        val clash = QueensUiFixtures.session()
            .withQueenPlaced(Cell(1), QueensUiFixtures.T0)
            .withQueenPlaced(Cell(2), QueensUiFixtures.T0) // same row → row violation
        val guided = QueensBoardModel.of(clash, guide = answer)
        assertTrue(guided.violations.isEmpty())
        assertEquals(answer - 1, guided.ghosts) // the answer queen at 1 has landed
        // Unguided, the same board stripes and ghosts nothing.
        val daily = QueensBoardModel.of(clash)
        assertTrue(daily.violations.isNotEmpty())
        assertTrue(daily.ghosts.isEmpty())
        assertEquals(answer, QueensBoardModel.of(QueensUiFixtures.session(), guide = answer).ghosts)
    }

    /** N1: a queen off the answer keeps its cue once every answer queen has landed, so a
     *  guided lesson that is not won still shows what to take away. */
    @Test
    fun `a guide marks a queen off the answer even when every answer has landed`() {
        val answer = QueensUiFixtures.solutionCells.map { it.idx }.toSet()
        val stray = (0 until QueensUiFixtures.puzzle().cellCount).first { it !in answer }
        // The stray lands first, as on the device (p2-57): the answer alone would win.
        val board = (listOf(stray) + answer).fold(QueensUiFixtures.session()) { s, idx ->
            s.withQueenPlaced(Cell(idx), QueensUiFixtures.T0)
        }
        val guided = QueensBoardModel.of(board, guide = answer)
        assertFalse(guided.win)
        assertTrue(guided.ghosts.isEmpty())
        assertEquals(setOf(stray), guided.strays)
        assertTrue(guided.violations.isEmpty())
        // A daily marks no strays: its rules speak through the stripes instead.
        assertTrue(QueensBoardModel.of(board).strays.isEmpty())
    }

    @Test
    fun `region hint maps to its region cells`() {
        val hints = com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints()
        val hint = hints.hint(QueensUiFixtures.puzzle(), emptyList())
        val model = QueensBoardModel.of(QueensUiFixtures.session(), hint)
        assertTrue(hint is com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region)
        val region = (hint as com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region).region
        assertEquals(region, model.hintRegion)
        assertEquals(4, model.hintCells.size) // region = row → 4 cells
    }

    /** Fix round 6 (§5.5 stage 2): the repeat tap POINTS at the forced cell. */
    @Test
    fun `hintPointed surfaces the forced cell as the ghost target`() {
        val hints = com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints()
        val hint = hints.hint(QueensUiFixtures.puzzle(), emptyList()) as
            com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Region
        assertNull(QueensBoardModel.of(QueensUiFixtures.session(), hint, hintPointed = false).hintCell)
        assertEquals(hint.cell.idx, QueensBoardModel.of(QueensUiFixtures.session(), hint, hintPointed = true).hintCell)
        // a non-region hint never points
        val incorrect = com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint.Incorrect(listOf(Cell(0)))
        assertNull(QueensBoardModel.of(QueensUiFixtures.session(), incorrect, hintPointed = true).hintCell)
    }

    @Test
    fun `win reflects the session completion stamp`() {
        val solved = QueensUiFixtures.solutionCells.fold(QueensUiFixtures.session()) { acc, c ->
            acc.withQueenPlaced(c, QueensUiFixtures.T0)
        }
        assertTrue(solved.isComplete)
        val model = QueensBoardModel.of(solved)
        assertTrue(model.win)
        assertEquals(QueensUiFixtures.solutionCells.map { it.idx }, model.queens)
    }

    @Test
    fun `auto marks vanish when their source queen is removed`() {
        val placed = QueensUiFixtures.session().withQueenPlaced(Cell(1), QueensUiFixtures.T0)
        assertEquals(QueensCellVisual.X_AUTO, QueensBoardModel.of(placed).visuals[2])
        val removed = placed.withQueenRemoved(Cell(1), QueensUiFixtures.T0)
        val model = QueensBoardModel.of(removed)
        assertNull(removed.markAt(Cell(2)))
        assertFalse(model.visuals[2] == QueensCellVisual.X_AUTO)
        assertEquals(QueensCellVisual.EMPTY, model.visuals[1])
    }
}
