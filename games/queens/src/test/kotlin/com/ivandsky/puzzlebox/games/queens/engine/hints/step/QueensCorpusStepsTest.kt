package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.LongLookAheadRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep.Deduction.Result
import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays [QueensStepper] over the 806-board measurement corpus, one press at a time: each press
 * applies one target cell and asks again, as the app does (SC1, SC2; spec A6–A8). A step is a run
 * of consecutive presses with the same rung, evidence and unit, and its cells are the cross set
 * it proves at its first press, as the catalog counts them. Measurement gate: no behaviour ships
 * with it. The corpus is the lab's `boards.json`, copied byte for byte: `{id, src, size, regions,
 * solution}` per board, in lab order.
 */
class QueensCorpusStepsTest {

    @Test
    fun `corpus fixture holds the 806 lab boards with the catalog size split`() {
        val boards = corpus()
        assertEquals(806, boards.size)
        assertEquals(boards.size, boards.map { it.id }.toSet().size)
        assertEquals(mapOf(5 to 4, 7 to 149, 8 to 276, 9 to 281, 10 to 67, 11 to 29), boards.groupingBy { it.puzzle.size }.eachCount())
    }

    @Test
    fun `stepper solves every corpus board by its own steps with auto-cross on`() {
        val tally = Tally("auto-cross on, all 806")
        corpus().forEach { replay(it, autoCross = true, tally) }
        tally.report()
        tally.assertGates()
    }

    @Test
    fun `stepper solves every eighth corpus board by id with auto-cross off`() {
        val tally = Tally("auto-cross off, every 8th by id")
        corpus().sortedBy { it.id }.filterIndexed { i, _ -> i % 8 == 0 }.forEach { replay(it, autoCross = false, tally) }
        tally.report()
        tally.assertGates()
    }

    private data class Board(val id: String, val puzzle: QueensPuzzle, val solution: Set<Cell>)

    /** Identity of a catalog step: consecutive presses with an equal key are one step. */
    private data class StepKey(val kind: String, val rung: String, val evidence: Set<Cell>, val unit: Any?)

    private class Tally(private val label: String) {
        private val startedAt = System.nanoTime()
        var boards = 0
        var solved = 0
        var presses = 0
        var longPresses = 0
        var steps = 0
        var longSteps = 0
        var cells = 0
        var longCells = 0
        val fires = mutableMapOf<String, Int>()
        val problems = mutableListOf<String>()

        fun press(rung: String, long: Boolean, newStep: Boolean, stepCells: Int) {
            presses++
            fires.merge(rung, 1, Int::plus)
            if (long) longPresses++
            if (newStep) {
                steps++
                cells += stepCells
                if (long) {
                    longSteps++
                    longCells += stepCells
                }
            }
        }

        fun reject(problem: String) {
            problems += problem
        }

        fun report() {
            problems.forEach { println("QueensCorpus PROBLEM $it") }
            val ms = (System.nanoTime() - startedAt) / 1_000_000
            println("QueensCorpus [$label]: boards=$boards solved=$solved presses=$presses steps=$steps cells=$cells elapsed=${ms} ms")
            println("  long+reveal: presses $longPresses (${pct(longPresses, presses)}), steps $longSteps/$steps (${pct(longSteps, steps)}), cells $longCells/$cells (${pct(longCells, cells)})")
            println("  fires: " + fires.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value} (${pct(it.value, presses)})" })
        }

        fun assertGates() {
            assertTrue("[$label] ${problems.size} problem(s)", problems.isEmpty())
            assertEquals("[$label] boards not all solved", boards, solved)
            assertTrue("[$label] long+reveal presses ${pct(longPresses, presses)} > 2%", longPresses <= MAX_SHARE * presses)
            assertTrue("[$label] long+reveal steps ${pct(longSteps, steps)} > 2%", longSteps <= MAX_SHARE * steps)
            assertTrue("[$label] long+reveal cells ${pct(longCells, cells)} > 2%", longCells <= MAX_SHARE * cells)
        }

        private fun pct(part: Int, whole: Int): String = if (whole == 0) "0.00%" else "%.2f%%".format(100.0 * part / whole)
    }

    private fun replay(board: Board, autoCross: Boolean, tally: Tally) {
        tally.boards++
        val n = board.puzzle.size
        var state = QueensBoard()
        var last: Cell? = null
        var previous: StepKey? = null
        for (press in 1..MAX_PRESSES) {
            val hint = QueensStepper.next(board.puzzle, state, autoCross, last, board.solution)
            if (hint == QueensStep.Solved) {
                tally.solved++
                return
            }
            val open = openCells(board.puzzle, state, autoCross)
            val key: StepKey
            val rung: String
            val long: Boolean
            val stepCells: Int
            when (hint) {
                is QueensStep.Deduction -> {
                    rung = hint.unit.rung.id
                    long = hint.unit.rung is LongLookAheadRung
                    if (hint.evidence.isEmpty()) {
                        tally.reject("${board.id} press $press: empty evidence: $hint")
                        return
                    }
                    when (val result = hint.result) {
                        is Result.Cross -> {
                            if (result.cells.isEmpty() || hint.evidence.any { it in result.cells }) {
                                tally.reject("${board.id} press $press: cross empty or meets evidence: $hint")
                                return
                            }
                            val bad = result.cells.firstOrNull { it in board.solution || !open.isOpen(it.idx) }
                            if (bad != null) {
                                tally.reject("${board.id} press $press: unsound cross $bad: $hint")
                                return
                            }
                            val target = result.cells.minWith(compareBy<Cell>({ chebyshev(n, it, last) }, { it.idx }))
                            state = state.copy(manual = state.manual + target)
                            last = target
                            key = StepKey("cross", rung, hint.evidence, hint.unit)
                            stepCells = result.cells.size
                        }
                        is Result.LastCell -> {
                            if (result.cell !in board.solution || !open.isOpen(result.cell.idx)) {
                                tally.reject("${board.id} press $press: unsound last cell ${result.cell}: $hint")
                                return
                            }
                            state = state.copy(queens = state.queens + result.cell)
                            last = result.cell
                            key = StepKey("last", rung, hint.evidence, hint.unit)
                            stepCells = 1
                        }
                    }
                }
                is QueensStep.Reveal -> {
                    rung = "reveal"
                    long = true
                    if (hint.cell in board.solution || !open.isOpen(hint.cell.idx)) {
                        tally.reject("${board.id} press $press: unsound reveal $hint")
                        return
                    }
                    state = state.copy(manual = state.manual + hint.cell)
                    last = hint.cell
                    key = StepKey("reveal", rung, setOf(hint.cell), null)
                    stepCells = 1
                }
                is QueensStep.Mistake, QueensStep.Unavailable, QueensStep.Solved -> {
                    tally.reject("${board.id} press $press: $hint on a board fed only with steps")
                    return
                }
            }
            tally.press(rung, long, newStep = key != previous, stepCells = stepCells)
            previous = key
        }
        tally.reject("${board.id}: not solved within $MAX_PRESSES presses")
    }

    private fun openCells(puzzle: QueensPuzzle, board: QueensBoard, autoCross: Boolean): QueensCandidates {
        val queens = board.queens.mapTo(HashSet()) { it.idx }
        val start = if (autoCross) QueensCandidates.start(puzzle, queens) else QueensCandidates(puzzle.size, queens, emptySet())
        return start.exclude(board.manual.mapTo(HashSet()) { it.idx })
    }

    private fun corpus(): List<Board> {
        val text = QueensCorpusStepsTest::class.java.getResourceAsStream("/fixtures/queens-hint-corpus.json")!!.readBytes().decodeToString()
        return Json.parseToJsonElement(text).jsonArray.map { decode(it.jsonObject) }
    }

    private fun decode(record: JsonObject): Board {
        val size = record.getValue("size").jsonPrimitive.content.toInt()
        val regions = record.getValue("regions").jsonArray.map { it.jsonPrimitive.content.toInt() }
        val solution = record.getValue("solution").jsonArray.mapTo(HashSet()) { Cell(it.jsonPrimitive.content.toInt()) }
        return Board(record.getValue("id").jsonPrimitive.content, QueensPuzzle(size = size, regions = regions), solution)
    }

    private companion object {
        /** The app's guard: a board that takes more presses than this fails. */
        const val MAX_PRESSES = 500
        const val MAX_SHARE = 0.02
    }
}

private fun chebyshev(n: Int, a: Cell, b: Cell?): Int =
    if (b == null) 0 else max(abs(a.idx / n - b.idx / n), abs(a.idx % n - b.idx % n))
