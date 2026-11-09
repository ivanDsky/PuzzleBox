package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays [TangoStepper] over every real 6×6 Tango board in the corpus and checks the catalog numbers
 * (spec A6, SC1, SC2; `tango-deductions.md`). Measurement gate only: no behaviour ships with it.
 *
 * Corpus: `fixtures/tango-boards.json`, one record per board: `{id, src, n, giv, sol, sg}` with `giv`/`sol`
 * row-major (`S` sun, `M` moon, `.` empty) and `sg` the `=`/`x` markers between adjacent cells.
 * Source: awa.games LinkedIn Tango puzzles #1..#762 (755 boards) plus one repo fixture (`fx:byid-1`).
 * Puzzle data by AWA GAMES, https://awa.games/linkedin-games/tango/ (terms https://awa.games/policies/#terms).
 * Taken 2026-11-08. Filter: `n == 6`, so the 8×8 board `li:321` is dropped: 756 records.
 * Built by `.scratch/hint-parity/corpus/tango/build.js` (`.scratch/` is git-ignored, so the test carries its own copy).
 */
class TangoCorpusTest {

    @Test
    fun `corpus file holds 756 unique 6x6 records`() {
        val boards = corpus()
        assertEquals(756, boards.size)
        assertEquals(756, boards.map { it.id }.toSet().size)
        assertTrue(boards.all { it.puzzle.n == 6 })
    }

    @Test
    fun `every corpus board has exactly one solution and it is the published one`() {
        val wrong = corpus().filterNot { board ->
            TangoUniqueness.isUniquelySolvable(board.puzzle) &&
                TangoRules.isComplete(TangoState(board.puzzle, board.solution)) &&
                board.puzzle.givens.all { (idx, value) -> board.solution[idx] == value }
        }.map { it.id }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `stepper replays every corpus board to Solved soundly within the catalog shares`() {
        val replays = corpus().map(::replay)
        assertEquals(emptyList<String>(), replays.mapNotNull { it.problem }.take(5))
        assertEquals(756, replays.count { it.solved })

        val steps = replays.flatMap { it.steps }
        val reveals = replays.sumOf { it.reveals }
        val cells = steps.sumOf { it.cells }
        val d8Steps = steps.count { it.rung == TangoStepRung.LINE_OPTIONS }
        val d8Cells = steps.filter { it.rung == TangoStepRung.LINE_OPTIONS }.sumOf { it.cells }
        println(
            "tango corpus: boards=${replays.size} steps=${steps.size} cells=$cells reveals=$reveals " +
                "d8Steps=$d8Steps (${"%.2f".format(100.0 * d8Steps / steps.size)}% of steps) " +
                "d8Cells=$d8Cells (${"%.2f".format(100.0 * d8Cells / cells)}% of cells)",
        )
        assertEquals(0, reveals)
        assertTrue("LINE_OPTIONS $d8Steps of ${steps.size} steps is over 1%", d8Steps <= 0.01 * steps.size)
    }

    private data class Board(val id: String, val puzzle: TangoPuzzle, val solution: List<TangoCell>)

    /** One catalog step: consecutive deductions with the same rung, evidence and line; a reveal is its own step. */
    private data class Step(val rung: TangoStepRung?, val evidence: Set<Int>, val row: Int?, val col: Int?, val cells: Int)

    private class Replay(val steps: List<Step>, val reveals: Int, val solved: Boolean, val problem: String?)

    private fun replay(board: Board): Replay {
        val puzzle = board.puzzle
        val solution = board.solution
        val grid = MutableList<TangoCell?>(puzzle.cellCount) { puzzle.givens[it] }
        val empties = grid.count { it == null }
        val steps = ArrayList<Step>()
        var reveals = 0
        var last: Int? = null
        var placed = 0
        while (true) {
            val (idx, value) = when (val step = TangoStepper.next(puzzle, grid, last, solution)) {
                TangoStep.Solved -> return Replay(steps, reveals, solved = true, problem = null)
                is TangoStep.Deduction -> {
                    record(steps, Step(step.rung, step.evidence, step.row, step.col, 1))
                    step.idx to step.value
                }
                is TangoStep.Reveal -> {
                    reveals++
                    record(steps, Step(null, emptySet(), null, null, 1))
                    step.idx to step.value
                }
                is TangoStep.Mistake, TangoStep.Unavailable ->
                    return Replay(steps, reveals, solved = false, problem = "${board.id}: $step")
            }
            if (grid[idx] != null) return Replay(steps, reveals, solved = false, problem = "${board.id}: names filled cell $idx")
            if (value != solution[idx]) return Replay(steps, reveals, solved = false, problem = "${board.id}: unsound value at $idx")
            if (++placed > empties) return Replay(steps, reveals, solved = false, problem = "${board.id}: replay not finite")
            grid[idx] = value
            last = idx
        }
    }

    /** Merges [next] into the last step when both are deductions of the same rung, evidence and line. */
    private fun record(steps: MutableList<Step>, next: Step) {
        val last = steps.lastOrNull()
        if (next.rung != null && last != null && last.rung == next.rung && last.evidence == next.evidence &&
            last.row == next.row && last.col == next.col
        ) {
            steps[steps.lastIndex] = last.copy(cells = last.cells + 1)
        } else {
            steps += next
        }
    }

    private fun corpus(): List<Board> {
        val text = requireNotNull(javaClass.getResource("/fixtures/tango-boards.json")) { "tango-boards.json" }.readText()
        return Json.parseToJsonElement(text).jsonArray.map { decode(it.jsonObject) }
    }

    private fun decode(record: JsonObject): Board {
        val id = record.getValue("id").jsonPrimitive.content
        val n = record.getValue("n").jsonPrimitive.int
        val givens = record.getValue("giv").jsonPrimitive.content.withIndex()
            .filter { it.value != '.' }
            .associate { it.index to cellOf(it.value) }
        val markers = record.getValue("sg").jsonArray.map { edge ->
            val (a, b, kind) = edge.jsonArray
            val from = a.jsonPrimitive.int
            TangoMarker(
                from,
                if (b.jsonPrimitive.int == from + 1) TangoEdgeSide.RIGHT else TangoEdgeSide.DOWN,
                if (kind.jsonPrimitive.content == "=") TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE,
            )
        }
        val solution = record.getValue("sol").jsonPrimitive.content.map(::cellOf)
        return Board(id, TangoPuzzle(VariantId("tango/n$n"), n, givens, markers), solution)
    }

    private fun cellOf(c: Char): TangoCell = when (c) {
        'S' -> TangoCell.SUN
        'M' -> TangoCell.MOON
        else -> error("bad cell '$c'")
    }
}
