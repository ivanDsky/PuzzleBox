package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.AttackRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensProof
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep.Deduction.Result
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep.Mistake.Kind
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QueensStepperTest {

    /** One stepper replay from the empty board, applying each step as the player would. */
    private class Replay(val steps: List<QueensStep>)

    private companion object {
        fun text(name: String): String = QueensStepperTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()

        fun JsonObject.ints(key: String): List<Int>? = get(key)?.jsonArray?.map { it.jsonPrimitive.content.toInt() }

        fun golden(name: String): QueensPuzzle {
            val boards = Json.parseToJsonElement(text("queens-solver-golden.json")).jsonObject.getValue("boards").jsonArray
            val o = boards.map { it.jsonObject }.single { it.getValue("name").jsonPrimitive.content == name }
            return QueensPuzzle(
                size = o.getValue("size").jsonPrimitive.content.toInt(),
                regions = o.ints("regions")!!,
                queensPerUnit = o.getValue("queensPerUnit").jsonPrimitive.content.toInt(),
                solution = o.ints("solve")!!.map(::Cell),
            )
        }

        /** 6×6 with two solutions: the ladder stalls on it, so its replay reaches a reveal. */
        val twoSolutions: QueensPuzzle by lazy {
            val o = Json.parseToJsonElement(text("queens-editor-two-solutions.json")).jsonObject
            QueensPuzzle(size = 6, regions = o.ints("regions")!!, solution = o.ints("solutionA")!!.map(::Cell))
        }

        /** gen-n8-q1, N=1. Solution: 5, 10, 16, 28, 39, 43, 49, 62. */
        val n8: QueensPuzzle by lazy { golden("gen-n8-q1") }

        /** The candidates the stepper seeds from [board]; tests use it only to judge soundness. */
        fun seeded(puzzle: QueensPuzzle, board: QueensBoard, autoCross: Boolean): QueensCandidates {
            val queens = board.queens.map { it.idx }.toSet()
            val start = if (autoCross) QueensCandidates.start(puzzle, queens) else QueensCandidates(puzzle.size, queens, emptySet())
            return start.exclude(board.manual.map { it.idx }.toSet())
        }

        fun replay(puzzle: QueensPuzzle, autoCross: Boolean): Replay {
            val solution = puzzle.solution!!.toSet()
            var board = QueensBoard()
            var last: Cell? = null
            val steps = mutableListOf<QueensStep>()
            repeat(puzzle.cellCount * 2) {
                val step = QueensStepper.next(puzzle, board, autoCross, last, solution)
                steps += step
                val open = seeded(puzzle, board, autoCross)
                when (step) {
                    QueensStep.Solved -> return Replay(steps)
                    is QueensStep.Deduction -> when (val r = step.result) {
                        is Result.Cross -> {
                            assertTrue("$step", r.cells.isNotEmpty() && r.cells.all { open.isOpen(it.idx) && it !in solution })
                            board = board.copy(manual = board.manual + r.cells)
                            last = r.cells.maxBy { it.idx }
                        }
                        is Result.LastCell -> {
                            assertTrue("$step", open.isOpen(r.cell.idx) && r.cell in solution)
                            board = board.copy(queens = board.queens + r.cell)
                            last = r.cell
                        }
                    }
                    is QueensStep.Reveal -> {
                        assertTrue("$step", open.isOpen(step.cell.idx) && step.cell !in solution && !step.holdsQueen)
                        board = board.copy(manual = board.manual + step.cell)
                        last = step.cell
                    }
                    is QueensStep.Mistake, QueensStep.Unavailable -> fail("replay produced $step on $board")
                }
            }
            throw AssertionError("replay did not finish")
        }

        /** Each soundness board replayed once for every test that reads it. */
        val replays: Map<String, Replay> by lazy {
            mapOf(
                "gen-n8-q1" to replay(n8, autoCross = true),
                "gen-n8-q2" to replay(golden("gen-n8-q2"), autoCross = true),
                "n8N2 auto-cross off" to replay(QueensTestSupport.n8N2Puzzle(), autoCross = false),
                "two-solutions" to replay(twoSolutions, autoCross = true),
            )
        }

        fun cells(vararg idx: Int): Set<Cell> = idx.map(::Cell).toSet()

        /** The cells a lone queen at [queen] rules out on an N=1 board: touching, or sharing a unit. */
        fun attackedBy(puzzle: QueensPuzzle, queen: Int): Set<Int> {
            val n = puzzle.size
            return (0 until puzzle.cellCount).filter { c ->
                c != queen && (
                    (abs(c / n - queen / n) <= 1 && abs(c % n - queen % n) <= 1) ||
                        c / n == queen / n || c % n == queen % n || puzzle.regions[c] == puzzle.regions[queen]
                    )
            }.toSet()
        }
    }

    private val solution: Set<Cell> get() = n8.solution!!.toSet()

    private fun next(board: QueensBoard, lastMove: Cell? = null, autoCross: Boolean = true, solution: Set<Cell>? = this.solution) =
        QueensStepper.next(n8, board, autoCross, lastMove, solution)

    private fun board(queens: List<Int> = emptyList(), manual: Set<Int> = emptySet()) =
        QueensBoard(queens.map(::Cell), manual.map(::Cell).toSet())

    // ---- order, solved ----

    @Test
    fun `empty board starts with a deduction and the full solution is solved`() {
        assertTrue(next(board()) is QueensStep.Deduction)
        assertTrue(next(board(), solution = null) is QueensStep.Deduction)
        val full = QueensBoard(n8.solution!!)
        assertEquals(QueensStep.Solved, next(full))
        assertEquals(QueensStep.Solved, next(full, autoCross = false, solution = null))
    }

    // ---- mistakes ----

    @Test
    fun `a wrong queen comes before an available deduction`() {
        assertEquals(QueensStep.Mistake(Cell(0), Kind.OFF_SOLUTION, null), next(board(queens = listOf(0))))
    }

    @Test
    fun `the wrong entry nearer the last move wins`() {
        val b = board(queens = listOf(0, 63))
        assertEquals(Cell(63), (next(b, lastMove = Cell(62)) as QueensStep.Mistake).cell)
        assertEquals(Cell(0), (next(b, lastMove = Cell(1)) as QueensStep.Mistake).cell)
    }

    @Test
    fun `at equal distance a queen goes before a cross, then the lower cell`() {
        // Queen 0 and the cross on solution cell 16 are both one step from cell 8.
        assertEquals(QueensStep.Mistake(Cell(0), Kind.OFF_SOLUTION, null), next(board(queens = listOf(0), manual = setOf(16)), lastMove = Cell(8)))
        // Quiet wrong queens 0 and 18 are both one step from cell 9.
        assertEquals(Cell(0), (next(board(queens = listOf(18, 0)), lastMove = Cell(9)) as QueensStep.Mistake).cell)
        assertEquals(Cell(0), (next(board(queens = listOf(18, 0))) as QueensStep.Mistake).cell)
    }

    @Test
    fun `a wrong queen attacking queens is a rule break naming the nearest one`() {
        // 13 is off the solution, touches 5 and shares row 1 with 10; the correct 5 and 10 are not accused.
        assertEquals(QueensStep.Mistake(Cell(13), Kind.RULE_BREAK, Cell(5)), next(board(queens = listOf(5, 10, 13)), lastMove = Cell(5)))
    }

    @Test
    fun `a manual cross on a solution cell is a wrong cross`() {
        assertEquals(QueensStep.Mistake(Cell(16), Kind.WRONG_CROSS, null), next(board(manual = setOf(16))))
    }

    @Test
    fun `a manual mark hidden under a correct queen is not a cross`() {
        assertTrue(next(board(queens = listOf(16), manual = setOf(16))) is QueensStep.Deduction)
    }

    @Test
    fun `an auto mark on a solution cell is never a mistake`() {
        // N=2 with Auto-cross on: queen 1 auto-crosses its whole row on screen, solution cell 3 included.
        val q2 = golden("gen-n8-q2")
        val step = QueensStepper.next(q2, board(queens = listOf(1)), autoCross = true, lastMove = Cell(3), solution = q2.solution!!.toSet())
        assertTrue("$step", step is QueensStep.Deduction)
    }

    // ---- no solution ----

    @Test
    fun `without a solution a quiet off-solution queen and a cross on a solution cell are not accused`() {
        assertTrue(next(board(queens = listOf(0)), solution = null) is QueensStep.Deduction)
        assertTrue(next(board(manual = setOf(16)), solution = null) is QueensStep.Deduction)
    }

    @Test
    fun `without a solution the attacking queen nearer the last move is the rule break`() {
        val b = board(queens = listOf(5, 12))
        assertEquals(QueensStep.Mistake(Cell(12), Kind.RULE_BREAK, Cell(5)), next(b, lastMove = Cell(12), solution = null))
        assertEquals(QueensStep.Mistake(Cell(5), Kind.RULE_BREAK, Cell(12)), next(b, lastMove = Cell(5), solution = null))
        assertEquals(QueensStep.Mistake(Cell(5), Kind.RULE_BREAK, Cell(12)), next(b, solution = null))
    }

    // ---- seeding ----

    @Test
    fun `auto-cross off lists the crosses a queen still owes as D0`() {
        // 18 touches queen 10 and is outside everything queen 5 attacks, so queen 10's list is the nearer one.
        val step = next(board(queens = listOf(5, 10)), lastMove = Cell(18), autoCross = false) as QueensStep.Deduction
        assertEquals(AttackRung, step.rung)
        assertEquals(cells(10), step.evidence)
        assertEquals(Result.Cross((attackedBy(n8, 10) - 5).map(::Cell).toSet()), step.result)
    }

    @Test
    fun `with every attacked cell crossed by hand, or auto-cross on, D0 does not fire`() {
        val crossed = attackedBy(n8, 5) + attackedBy(n8, 10) - setOf(5, 10)
        val byHand = next(board(queens = listOf(5, 10), manual = crossed), autoCross = false) as QueensStep.Deduction
        val auto = next(board(queens = listOf(5, 10))) as QueensStep.Deduction
        assertNotEquals(AttackRung, byHand.rung)
        assertEquals(byHand, auto)
    }

    // ---- tie-break, conversion ----

    private class FakeRung(override val id: String, proofs: (QueensRung) -> List<Pair<QueensDeduction, Set<Int>>>) : QueensRung {
        override val level: Difficulty.Level = Difficulty.Level.Easy
        override val captionKey: String = id
        private val proofs = proofs(this).map { (d, evidence) -> QueensProof(d, evidence) }
        override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> = proofs.asSequence()
        override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? = proofs.firstOrNull()?.deduction
    }

    private val n4 = QueensTestSupport.n4Puzzle()

    private fun exclude(rung: QueensRung, vararg cells: Int) = QueensDeduction.Exclude(rung, HintReason(HintReason.Kind.ROW, 0, rung), cells.toSet())

    /** Runs the n4 empty board through one fake rung listing [proofs] (crossed cells to evidence), and returns the crossed cells. */
    private fun pick(lastMove: Cell?, vararg proofs: Pair<Set<Int>, Set<Int>>): Set<Cell> {
        val rung = FakeRung("fake") { r -> proofs.map { (crossed, evidence) -> exclude(r, *crossed.toIntArray()) to evidence } }
        val step = QueensStepper.next(n4, QueensBoard(), true, lastMove, n4.solution!!.toSet(), QueensLadder(listOf(rung)))
        return ((step as QueensStep.Deduction).result as Result.Cross).cells
    }

    @Test
    fun `within a rung the smaller evidence wins, then the nearer cell, then the lower cell, then rung order`() {
        assertEquals(cells(15), pick(Cell(0), setOf(0) to setOf(1, 2), setOf(15) to setOf(3)))
        assertEquals(cells(15), pick(Cell(14), setOf(0) to setOf(5), setOf(15) to setOf(6)))
        assertEquals(cells(0), pick(Cell(5), setOf(10) to setOf(6), setOf(0) to setOf(6)))
        assertEquals(cells(3), pick(null, setOf(12) to setOf(6), setOf(3) to setOf(6)))
        // An exclusion is as near as its nearest crossed cell.
        assertEquals(cells(0, 15), pick(Cell(15), setOf(10) to setOf(6), setOf(0, 15) to setOf(6)))
        assertEquals(cells(0, 4), pick(null, setOf(0, 4) to setOf(6), setOf(0) to setOf(6)))
    }

    @Test
    fun `a lower rung beats a nearer higher rung`() {
        val low = FakeRung("low") { r -> listOf(exclude(r, 0) to setOf(4, 5, 6)) }
        val high = FakeRung("high") { r -> listOf(exclude(r, 15) to setOf(9)) }
        val step = QueensStepper.next(n4, QueensBoard(), true, Cell(15), null, QueensLadder(listOf(low, high))) as QueensStep.Deduction
        assertEquals(low, step.rung)
    }

    @Test
    fun `a place becomes the last cell and an exclusion the whole cross set, with the proof's evidence`() {
        val reason = HintReason(HintReason.Kind.REGION, 3, AttackRung)
        val place = FakeRung("place") { r -> listOf(QueensDeduction.Place(r, reason, 14) to setOf(12, 13, 15)) }
        assertEquals(
            QueensStep.Deduction(place, reason, cells(12, 13, 15), Result.LastCell(Cell(14))),
            QueensStepper.next(n4, QueensBoard(), true, null, null, QueensLadder(listOf(place))),
        )
        val cross = FakeRung("cross") { r -> listOf(QueensDeduction.Exclude(r, reason, setOf(2, 3, 9)) to setOf(0, 4)) }
        assertEquals(
            QueensStep.Deduction(cross, reason, cells(0, 4), Result.Cross(cells(2, 3, 9))),
            QueensStepper.next(n4, QueensBoard(), true, null, null, QueensLadder(listOf(cross))),
        )
    }

    // ---- reveal, unavailable ----

    @Test
    fun `with no proof the reveal is an off-solution open cell in the smallest unsolved unit`() {
        val none = QueensLadder(emptyList())
        // Row 3 has open 14 (solution) and 15 after crosses on 12 and 13: two open cells, the fewest of any unsolved unit.
        val crossed = QueensBoard(manual = cells(12, 13))
        val step = QueensStepper.next(n4, crossed, true, null, n4.solution!!.toSet(), none)
        assertEquals(QueensStep.Reveal(Cell(15), holdsQueen = false), step)
        assertEquals(step, QueensStepper.next(n4, crossed, true, null, n4.solution!!.toSet(), none))
        // Region 3 is the lone solution cell 14, so it offers nothing; every row then ties at four and the lowest cell goes.
        assertEquals(QueensStep.Reveal(Cell(0), holdsQueen = false), QueensStepper.next(n4, QueensBoard(), true, null, n4.solution!!.toSet(), none))
        assertEquals(QueensStep.Reveal(Cell(15), holdsQueen = false), QueensStepper.next(n4, QueensBoard(), true, Cell(15), n4.solution!!.toSet(), none))
    }

    @Test
    fun `where the ladder stalls, no solution is unavailable and a solution gives a sound reveal`() {
        val stall = generateSequence(QueensCandidates.start(twoSolutions, emptySet())) { state ->
            when (val d = QueensLadder.Stepwise.next(twoSolutions, state)) {
                null -> null
                is QueensDeduction.Place -> state.place(twoSolutions, d.cell)
                is QueensDeduction.Exclude -> state.exclude(d.cells)
            }
        }.last()
        val board = QueensBoard(stall.queens.sorted().map(::Cell), stall.excluded.map(::Cell).toSet())
        assertEquals(QueensStep.Unavailable, QueensStepper.next(twoSolutions, board, true, null, null))
        val solution = twoSolutions.solution!!.toSet()
        val reveal = QueensStepper.next(twoSolutions, board, true, null, solution) as QueensStep.Reveal
        assertTrue("$reveal", stall.isOpen(reveal.cell.idx) && reveal.cell !in solution && !reveal.holdsQueen)
    }

    // ---- soundness replays ----

    @Test
    fun `replays from empty reach solved with only sound crosses, last cells and reveals`() {
        for ((name, r) in replays) {
            assertEquals(name, QueensStep.Solved, r.steps.last())
            assertTrue(name, r.steps.dropLast(1).none { it == QueensStep.Solved })
        }
        assertTrue(replays.getValue("two-solutions").steps.any { it is QueensStep.Reveal })
        assertFalse(replays.getValue("gen-n8-q1").steps.any { it is QueensStep.Reveal })
    }

    @Test
    fun `auto-cross off replays step through D0`() {
        assertTrue(replays.getValue("n8N2 auto-cross off").steps.any { (it as? QueensStep.Deduction)?.rung == AttackRung })
        assertTrue(replays.getValue("gen-n8-q2").steps.none { (it as? QueensStep.Deduction)?.rung == AttackRung })
    }

    @Test
    fun `equal arguments give equal steps`() {
        val b = board(queens = listOf(5, 10), manual = setOf(0, 1))
        assertEquals(next(b, lastMove = Cell(10)), next(b, lastMove = Cell(10)))
    }
}
