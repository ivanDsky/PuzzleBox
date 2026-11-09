package com.ivandsky.puzzlebox.games.sudoku.engine.hints.step

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.EliminationStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.IntersectionSteps
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SubsetSteps
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuStepperEliminationTest {

    /** A board state: [givens] are every digit known at that point, [known] the strikes already shown. */
    private class Fixture(givens: String, solution: String, val known: Map<Int, Int> = emptyMap()) {
        val puzzle = SudokuPuzzle(SudokuVariants.MINI, KnownSolutions.grid(givens), KnownSolutions.grid(solution))
    }

    private val shifted = "612345345612456123123456561234234561"
    private val six = 1 shl 5

    // Empty board; 6 struck from box 0's cols 1 and 2, so box 0 puts its 6 in col 0. Col 0 pointing is the
    // one intersection here: a row pointing on a two-row box always comes with a claiming.
    private val pointing = Fixture(".".repeat(36), shifted, mapOf(1 to six, 2 to six, 7 to six, 8 to six))

    // The rest are states from the lab replay of lab/sudoku/random12k.json (board id in the name).
    private val claiming = Fixture("315.6.6..153.5.31..3..45.6352.5...3.", "315462642153254316136245463521521634") // rnd:1182
    private val nakedPair = Fixture("432516.1.423.4..3......41...4..24...", "432516516423641235253164165342324651") // rnd:3150
    private val hiddenPair = Fixture(".56...21.6...61..3..2...6.51..12....", "456231213654561423342516635142124365") // rnd:10787

    // rnd:4176: a pointing and two claimings unlock nothing; the one hidden pair leaves a single.
    private val unlockLater = Fixture("....4...2.13.2...65.....2.3...1..3..", "631245452613324156516432243561165324")

    // rnd:154: a pointing and a claiming, neither unlocks a single.
    private val noneUnlock = Fixture("..62.442.6..6.142.24...6.6254...4.62", "516234423651631425245316162543354162")

    // rnd:107: four pointings in different boxes, none unlocking.
    private val fourPointings = Fixture("1.5..63.6.5..53.4......5.325..5....3", "125436346152253641461325632514514263")

    // rnd:2173 after two strikes of 6: only a naked and a hidden triple are left.
    private val triplesOnly = Fixture(
        "32...1..123....512512364........4..3",
        "325641641235436512512364153426264153",
        mapOf(24 to six, 30 to six),
    )

    private val rungs = listOf(IntersectionSteps.POINTING, IntersectionSteps.CLAIMING, SubsetSteps.NAKED_PAIR, SubsetSteps.HIDDEN_PAIR)

    private fun next(fixture: Fixture, lastMove: Int? = null, known: Map<Int, Int> = fixture.known): SudokuStep =
        SudokuStepper.next(fixture.puzzle, SudokuBoard.of(fixture.puzzle), lastMove, known)

    private fun candidates(fixture: Fixture, known: Map<Int, Int> = fixture.known, board: SudokuBoard = SudokuBoard.of(fixture.puzzle)): IntArray =
        SudokuCandidates.of(fixture.puzzle, board).also { for ((cell, mask) in known) it[cell] = it[cell] and mask.inv() }

    /** The legacy steps, run on a copy, as an oracle for which techniques the board offers. */
    private fun fires(step: EliminationStep, fixture: Fixture, known: Map<Int, Int> = fixture.known, board: SudokuBoard = SudokuBoard.of(fixture.puzzle)) =
        step.eliminate(fixture.puzzle.geometry, candidates(fixture, known, board))

    private fun merge(known: Map<Int, Int>, struck: Map<Int, Int>): Map<Int, Int> =
        (known.keys + struck.keys).associateWith { (known[it] ?: 0) or (struck[it] ?: 0) }

    /** Every bit of [struck] is a live candidate, and none is the cell's solution digit. */
    private fun assertStrikesLiveAndSound(
        fixture: Fixture,
        known: Map<Int, Int>,
        struck: Map<Int, Int>,
        board: SudokuBoard = SudokuBoard.of(fixture.puzzle),
    ) {
        val before = candidates(fixture, known, board)
        assertTrue("nothing struck", struck.isNotEmpty())
        for ((cell, mask) in struck) {
            assertTrue("cell $cell strikes ${mask.toString(2)} beyond ${before[cell].toString(2)}", mask != 0 && mask and before[cell].inv() == 0)
            assertEquals("cell $cell loses its solution digit", 0, mask and (1 shl (fixture.puzzle.solution[cell] - 1)))
        }
    }

    private fun elimination(fixture: Fixture, lastMove: Int? = null): SudokuStep.Elimination {
        val step = next(fixture, lastMove)
        assertTrue("expected an elimination, got $step", step is SudokuStep.Elimination)
        return (step as SudokuStep.Elimination).also { assertStrikesLiveAndSound(fixture, fixture.known, it.struck) }
    }

    private fun assertOnlyRung(fixture: Fixture, rung: EliminationStep) =
        assertEquals(rungs.map { it == rung }, rungs.map { fires(it, fixture) })

    private fun cells(fixture: Fixture, unit: SudokuUnit?): Set<Int> = fixture.puzzle.geometry.cellsOf(unit!!).toSet()

    @Test
    fun `pointing strikes the digit from the rest of the line the box confines it to`() {
        assertOnlyRung(pointing, IntersectionSteps.POINTING)
        assertEquals(
            SudokuStep.Elimination(SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 0), mapOf(12 to six, 18 to six, 24 to six, 30 to six), setOf(0, 6)),
            elimination(pointing),
        )
    }

    @Test
    fun `claiming strikes the digit from the rest of the box the line confines it to`() {
        assertOnlyRung(claiming, IntersectionSteps.CLAIMING)
        val step = elimination(claiming)
        assertEquals(SudokuStep.Elimination(SudokuTechnique.CLAIMING, SudokuUnit(UnitKind.COLUMN, 0), mapOf(14 to 2, 20 to 2), setOf(12, 18)), step)
        // The struck cells share the evidence's box and lie outside the claiming column.
        val box = claiming.puzzle.geometry.boxOf(step.evidence.first())
        assertTrue(step.struck.keys.all { claiming.puzzle.geometry.boxOf(it) == box && it !in cells(claiming, step.unit) })
    }

    @Test
    fun `a naked pair strikes its two digits from the rest of the unit`() {
        assertOnlyRung(nakedPair, SubsetSteps.NAKED_PAIR)
        val step = elimination(nakedPair)
        assertEquals(SudokuStep.Elimination(SudokuTechnique.NAKED_PAIR, SudokuUnit(UnitKind.ROW, 3), mapOf(18 to 48, 20 to 48, 21 to 32), setOf(19, 22)), step)
        val pairDigits = step.evidence.fold(0) { acc, cell -> acc or candidates(nakedPair)[cell] }
        assertEquals(2, Integer.bitCount(pairDigits))
        assertTrue(step.struck.all { (cell, mask) -> cell !in step.evidence && mask and pairDigits.inv() == 0 })
    }

    @Test
    fun `a hidden pair strikes every other digit from its two cells`() {
        assertOnlyRung(hiddenPair, SubsetSteps.HIDDEN_PAIR)
        val step = elimination(hiddenPair)
        assertEquals(SudokuStep.Elimination(SudokuTechnique.HIDDEN_PAIR, SudokuUnit(UnitKind.ROW, 3), mapOf(22 to 24, 23 to 24), setOf(22, 23)), step)
        assertEquals(step.evidence, step.struck.keys)
    }

    @Test
    fun `evidence lies inside the unit on every fixture`() {
        for (fixture in listOf(pointing, claiming, nakedPair, hiddenPair, unlockLater, noneUnlock, fourPointings)) {
            val step = elimination(fixture)
            assertTrue("$step", cells(fixture, step.unit).containsAll(step.evidence))
        }
    }

    @Test
    fun `an elimination that unlocks a single beats a cheaper rung that unlocks nothing`() {
        assertTrue(rungs.all { fires(it, unlockLater) == (it != SubsetSteps.NAKED_PAIR) })
        val step = elimination(unlockLater)
        assertEquals(SudokuStep.Elimination(SudokuTechnique.HIDDEN_PAIR, SudokuUnit(UnitKind.COLUMN, 1), mapOf(1 to 48, 19 to 40), setOf(1, 19)), step)
        assertTrue(next(unlockLater, known = merge(unlockLater.known, step.struck)) is SudokuStep.Single)
    }

    @Test
    fun `with nothing unlocking the cheapest rung wins`() {
        assertTrue(fires(IntersectionSteps.POINTING, noneUnlock) && fires(IntersectionSteps.CLAIMING, noneUnlock))
        val step = elimination(noneUnlock)
        assertEquals(SudokuStep.Elimination(SudokuTechnique.POINTING, SudokuUnit(UnitKind.BOX, 0), mapOf(4 to 1), setOf(0, 1)), step)
        assertFalse(next(noneUnlock, known = merge(noneUnlock.known, step.struck)) is SudokuStep.Single)
    }

    @Test
    fun `inside a rung the elimination striking nearest the last move wins, scan order on a tie`() {
        assertEquals(setOf(19), elimination(fourPointings, lastMove = null).struck.keys)
        assertEquals(setOf(33, 34), elimination(fourPointings, lastMove = 35).struck.keys)
        assertEquals(setOf(21, 22), elimination(fourPointings, lastMove = 5).struck.keys)
        assertEquals(setOf(19, 31), elimination(fourPointings, lastMove = 30).struck.keys)
    }

    @Test
    fun `a board whose only elimination is a triple falls through to the reveal`() {
        assertTrue(rungs.none { fires(it, triplesOnly) })
        assertTrue(fires(SubsetSteps.NAKED_TRIPLE, triplesOnly) && fires(SubsetSteps.HIDDEN_TRIPLE, triplesOnly))
        val step = next(triplesOnly)
        assertTrue("$step", step is SudokuStep.Reveal)
        assertEquals(triplesOnly.puzzle.solution[(step as SudokuStep.Reveal).cell], step.digit)
    }

    @Test
    fun `a single goes first even while eliminations remain`() {
        val known = merge(unlockLater.known, elimination(unlockLater).struck)
        assertTrue(fires(IntersectionSteps.POINTING, unlockLater, known))
        assertTrue(next(unlockLater, known = known) is SudokuStep.Single)
    }

    @Test
    fun `once its strikes are known an elimination is never offered again`() {
        for (fixture in listOf(pointing, claiming, nakedPair, hiddenPair, noneUnlock, fourPointings)) {
            val first = elimination(fixture)
            val known = merge(fixture.known, first.struck)
            val after = next(fixture, known = known)
            assertNotEquals(first, after)
            if (after is SudokuStep.Elimination) assertTrue(after.struck.all { (cell, mask) -> mask and (known[cell] ?: 0) == 0 })
        }
    }

    @Test
    fun `replaying strikes through known reaches a single, reveals only when no rung applies, and solves every fixture`() {
        for (fixture in listOf(pointing, claiming, nakedPair, hiddenPair, unlockLater, noneUnlock, fourPointings, triplesOnly)) {
            var board = SudokuBoard.of(fixture.puzzle)
            var known = fixture.known
            var lastMove: Int? = null
            var singleAfterElimination = false
            var eliminated = false
            while (true) {
                val step = SudokuStepper.next(fixture.puzzle, board, lastMove, known)
                val (cell, digit) = when (step) {
                    is SudokuStep.Elimination -> {
                        assertStrikesLiveAndSound(fixture, known, step.struck, board)
                        known = merge(known, step.struck)
                        eliminated = true
                        continue
                    }
                    is SudokuStep.Single -> {
                        singleAfterElimination = singleAfterElimination || eliminated
                        step.place.cell to step.place.digit
                    }
                    is SudokuStep.Reveal -> {
                        assertTrue("reveal while a rung applies", rungs.none { fires(it, fixture, known, board) })
                        step.cell to step.digit
                    }
                    SudokuStep.Solved -> break
                    is SudokuStep.Mistake -> throw AssertionError("unexpected $step on a clean board")
                }
                assertEquals("digit at $cell", fixture.puzzle.solution[cell], digit)
                board = board.with(cell, SudokuCell.Entry(digit))
                lastMove = cell
            }
            assertTrue(fixture == triplesOnly || singleAfterElimination)
            assertTrue(board.isFull)
        }
    }
}
