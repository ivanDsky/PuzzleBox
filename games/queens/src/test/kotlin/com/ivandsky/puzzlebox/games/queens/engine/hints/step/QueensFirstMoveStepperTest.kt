package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.AttackRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensProof
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.SinglesRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep.Deduction.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A press skips the deductions a player sees at a glance (the ladder's [QueensLadder.obvious] rungs) while
 * another rung proves something those would not give. Mistake first, one deduction per press and the reveal stand.
 */
class QueensFirstMoveStepperTest {

    private class FakeRung(override val id: String, proofs: (QueensRung) -> List<Pair<QueensDeduction, Set<Int>>>) : QueensRung {
        override val level: Difficulty.Level = Difficulty.Level.Easy
        override val captionKey: String = id
        override val trainerId: TechniqueId? = null
        private val proofs = proofs(this).map { (d, evidence) -> QueensProof(d, evidence) }
        override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> = proofs.asSequence()
        override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? = proofs.firstOrNull()?.deduction
    }

    private val n4 = QueensTestSupport.n4Puzzle()

    private fun exclude(rung: QueensRung, vararg cells: Int) = QueensDeduction.Exclude(HintReason(HintReason.Kind.ROW, 0, rung), cells.toSet())

    /** A rung the player sees at a glance, with one small nearby proof: the ladder order would pick it. */
    private val seen = FakeRung("seen") { r -> listOf(exclude(r, 0) to setOf(4)) }
    private val real = FakeRung("real") { r -> listOf(exclude(r, 15) to setOf(9, 10, 11), exclude(r, 14) to setOf(9, 10)) }
    private val harder = FakeRung("harder") { r -> listOf(exclude(r, 3) to setOf(7)) }
    private val silent = FakeRung("silent") { emptyList() }

    private fun fakeStep(ladder: QueensLadder): QueensStep.Deduction =
        QueensStepper.next(n4, QueensBoard(), true, Cell(0), null, ladder) as QueensStep.Deduction

    // ---- fakes (the DoD) ----

    @Test
    fun `skips an obvious rung while another rung has a proof`() {
        val step = fakeStep(QueensLadder(listOf(seen, real), obvious = setOf(seen)))
        assertEquals(real, step.unit.rung)
    }

    @Test
    fun `falls back to the obvious rung when no other rung has a proof`() {
        val step = fakeStep(QueensLadder(listOf(seen, silent), obvious = setOf(seen)))
        assertEquals(seen, step.unit.rung)
        assertEquals(Result.Cross(setOf(Cell(0))), step.result)
    }

    @Test
    fun `picks the lowest other rung and its smallest evidence - the most constrained move`() {
        val step = fakeStep(QueensLadder(listOf(seen, real, harder), obvious = setOf(seen)))
        assertEquals(real, step.unit.rung)
        assertEquals(Result.Cross(setOf(Cell(14))), step.result)
    }

    @Test
    fun `skips an obvious rung wherever it sits on the ladder`() {
        val step = fakeStep(QueensLadder(listOf(real, seen, harder), obvious = setOf(real, seen)))
        assertEquals(harder, step.unit.rung)
    }

    @Test
    fun `a ladder that names no obvious rung answers in ladder order`() {
        assertEquals(seen, fakeStep(QueensLadder(listOf(seen, real))).unit.rung)
    }

    @Test
    fun `passes over a proof that only repeats what the obvious rungs give`() {
        // Crosses cell 0, which the obvious rung crosses anyway: a longer way to say the same thing.
        val echo = FakeRung("echo") { r -> listOf(exclude(r, 0) to setOf(9, 10)) }
        assertEquals(real, fakeStep(QueensLadder(listOf(seen, echo, real), obvious = setOf(seen))).unit.rung)
        assertEquals(seen, fakeStep(QueensLadder(listOf(seen, echo), obvious = setOf(seen))).unit.rung)
    }

    @Test
    fun `keeps a proof that adds a cell to what the obvious rungs give`() {
        val wider = FakeRung("wider") { r -> listOf(exclude(r, 0, 1) to setOf(9, 10)) }
        val step = fakeStep(QueensLadder(listOf(seen, wider), obvious = setOf(seen)))
        assertEquals(wider, step.unit.rung)
        assertEquals(Result.Cross(setOf(Cell(0), Cell(1))), step.result)
    }

    // ---- the real ladder ----

    @Test
    fun `the step ladder calls attacked cells and one-cell units obvious`() {
        assertEquals(setOf<QueensRung>(AttackRung, SinglesRung), QueensLadder.Stepwise.obvious)
    }

    @Test
    fun `a wrong queen still comes before any first move`() {
        val off = Cell((0 until n8.cellCount).first { Cell(it) !in solution })
        val step = QueensStepper.next(n8, QueensBoard(listOf(off)), true, null, solution)
        assertEquals(off, (step as QueensStep.Mistake).cell)
    }

    @Test
    fun `a press reruns reach solved by sound steps, each telling more than a glance or nothing more to tell`() {
        rerun(n8, autoCross = true)
        rerun(n8, autoCross = false)
        rerun(play, autoCross = true)
    }

    @Test
    fun `with auto-cross off a first move never crosses a cell a queen already attacks`() {
        // Queens 5 and 10 are on the solution; auto-cross off leaves every cell they attack open on the board.
        val board = QueensBoard(listOf(Cell(5), Cell(10)))
        val known = QueensCandidates.start(n8, setOf(5, 10))
        val step = QueensStepper.next(n8, board, false, null, solution) as QueensStep.Deduction
        assertTrue("$step", step.unit.rung !in QueensLadder.Stepwise.obvious)
        when (val r = step.result) {
            is Result.Cross -> assertTrue("$step", r.cells.all { known.isOpen(it.idx) })
            is Result.LastCell -> assertTrue("$step", known.isOpen(r.cell.idx))
        }
    }

    private val n8: QueensPuzzle by lazy {
        val text = QueensFirstMoveStepperTest::class.java.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString()
        val o = Json.parseToJsonElement(text).jsonObject.getValue("boards").jsonArray.map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == "gen-n8-q1" }
        QueensPuzzle(
            size = o.getValue("size").jsonPrimitive.content.toInt(),
            regions = o.getValue("regions").jsonArray.map { it.jsonPrimitive.content.toInt() },
            queensPerUnit = o.getValue("queensPerUnit").jsonPrimitive.content.toInt(),
            solution = o.getValue("solve").jsonArray.map { Cell(it.jsonPrimitive.content.toInt()) },
        )
    }

    private val solution: Set<Cell> get() = n8.solution!!.toSet()

    /** The 8×8 play fixture of queens-ui: regions 0 and 3 are one cell each, so the ladder order opens on a single. */
    private val play = QueensPuzzle(
        size = 8,
        regions = listOf(
            0, 4, 1, 1, 1, 1, 2, 2,
            4, 4, 1, 2, 1, 1, 2, 2,
            4, 4, 2, 2, 2, 2, 2, 2,
            4, 4, 4, 4, 4, 3, 4, 4,
            5, 5, 4, 5, 4, 4, 4, 5,
            6, 5, 5, 5, 5, 5, 5, 5,
            6, 6, 6, 6, 6, 6, 6, 6,
            6, 6, 7, 7, 7, 7, 7, 7,
        ),
        queensPerUnit = 1,
        solution = listOf(0, 12, 23, 29, 34, 46, 49, 59).map(::Cell),
    )

    /** What a glance gives from [known]: every one-cell unit filled, and the cells those queens attack ruled out. */
    private fun glance(puzzle: QueensPuzzle, known: QueensCandidates): QueensCandidates =
        generateSequence(known) { s -> (SinglesRung.step(puzzle, s) as? QueensDeduction.Place)?.let { s.place(puzzle, it.cell) } }.last()

    private fun QueensProof.tellsMoreThan(glance: QueensCandidates): Boolean = when (val d = deduction) {
        is QueensDeduction.Place -> d.cell !in glance.queens
        is QueensDeduction.Exclude -> d.cells.any(glance::isOpen)
    }

    /**
     * Presses hints from the empty board, applying each as the player would, until the board is solved.
     */
    private fun rerun(puzzle: QueensPuzzle, autoCross: Boolean) {
        val solution = puzzle.solution!!.toSet()
        val ladder = QueensLadder.Stepwise
        val others = ladder.rungs.filterNot { it in ladder.obvious }
        var board = QueensBoard()
        var last: Cell? = null
        repeat(puzzle.cellCount * 2) {
            val queens = board.queens.map { it.idx }.toSet()
            val crosses = board.manual.map { it.idx }.toSet()
            // What the player knows: every cell a queen attacks is ruled out, crossed or not.
            val known = QueensCandidates.start(puzzle, queens).exclude(crosses)
            val open = if (autoCross) known else QueensCandidates(puzzle.size, queens, emptySet()).exclude(crosses)
            val glance = glance(puzzle, known)
            when (val step = QueensStepper.next(puzzle, board, autoCross, last, solution)) {
                QueensStep.Solved -> return
                is QueensStep.Deduction -> {
                    if (step.unit.rung in ladder.obvious) {
                        val more = others.firstNotNullOfOrNull { rung -> rung.simplestSteps(puzzle, known).firstOrNull { it.tellsMoreThan(glance) } }
                        assertTrue("obvious $step while $more tells more on $board", more == null)
                    } else {
                        val told = when (val r = step.result) {
                            is Result.Cross -> r.cells.any { glance.isOpen(it.idx) }
                            is Result.LastCell -> r.cell.idx !in glance.queens
                        }
                        assertTrue("$step only repeats a glance on $board", told)
                    }
                    when (val r = step.result) {
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
                }
                is QueensStep.Reveal, is QueensStep.Mistake, QueensStep.Unavailable -> fail("rerun produced $step on $board")
            }
        }
        throw AssertionError("rerun did not finish")
    }
}
