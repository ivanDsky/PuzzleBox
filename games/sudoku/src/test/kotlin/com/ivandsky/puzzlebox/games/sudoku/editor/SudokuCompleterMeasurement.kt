package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuRandomFill
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSearchOutcome
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/**
 * Opt-in measurement, not part of the gate: the numbers behind [SudokuCompleter]'s per-size
 * minimum of givens (ticket 66 Unknowns). Run with
 * `SUDOKU_COMPLETER_MEASURE=true ./gradlew :games:sudoku:test --rerun --tests '*SudokuCompleterMeasurement'`
 * (`--rerun` because Gradle does not rerun a test task for a changed environment variable).
 * SUDOKU_COMPLETER_SAMPLES sets the drafts per row (default 200). The tables are in the test's
 * standard output (`build/test-results/test/TEST-*SudokuCompleterMeasurement.xml`); a run takes
 * under a minute.
 *
 * The first table is drafts that have a grid: a random full grid cut down to a number of
 * givens, from none to half the board. Per size and count it shows how often and how fast
 * `complete` finishes them under the Studio's 2 s budget, how many givens the finished board
 * holds, and how many of those each stage added. The second table is drafts placed with no
 * grid in mind ([SudokuTestDrafts.scattered]): whether `complete` finishes exactly those the
 * solver finds a grid for, and how long it takes to give up on the others. Undecided are
 * drafts the solver itself cannot settle in ten seconds, with how many of them `complete` finished.
 */
class SudokuCompleterMeasurement {

    @Test
    fun `measure completions at both sizes`() {
        assumeTrue(System.getenv("SUDOKU_COMPLETER_MEASURE") == "true")
        val samples = System.getenv("SUDOKU_COMPLETER_SAMPLES")?.toInt() ?: 200
        repeat(WARM_UP) { seed -> for (size in SudokuDraft.SIZES) press(cut(size, kept = 1, seed), seed) }

        println("| size | givens | done | p50 ms | p95 ms | max ms | givens after: min / mean / max | added for one solution | added for the ladder | ladder needed none | solver nodes: p50 / p95 / max |")
        println("|---|---|---|---|---|---|---|---|---|---|---|")
        for (size in SudokuDraft.SIZES) for (kept in KEPT.getValue(size)) {
            val drafts = List(samples) { seed -> cut(size, kept, seed) }
            val presses = drafts.mapIndexed { seed, draft -> press(draft, seed) }
            val after = presses.mapNotNull { it.done }.map { done -> done.givens.count { it != 0 } }
            val stages = drafts.mapIndexedNotNull { seed, draft -> presses[seed].done?.let { stages(draft, it, seed) } }
            val nodes = stages.map { it.nodes }.sorted()
            println(
                "| $size | $kept | ${after.size}/$samples | ${timings(presses)} " +
                    "| ${after.minOrNull()} / ${mean(after)} / ${after.maxOrNull()} " +
                    "| ${mean(stages.map { it.forOne })} | ${mean(stages.map { it.forLadder })} " +
                    "| ${stages.count { it.forLadder == 0 }}/${stages.size} " +
                    "| ${nodes[nodes.size / 2]} / ${nodes[(nodes.size * 95 + 99) / 100 - 1]} / ${nodes.last()} |",
            )
        }

        println()
        println("| size | placed | has a grid | of those done | no grid | of those gave up | undecided (done) | max ms done | max ms gave up |")
        println("|---|---|---|---|---|---|---|---|---|")
        for (size in SudokuDraft.SIZES) for (placed in PLACED.getValue(size)) {
            val geometry = geometry(size)
            val drafts = List(samples) { seed -> SudokuTestDrafts.scattered(geometry, placed, Random(size * 1_000 + seed)) }
            val grids = drafts.map { hasGrid(geometry, it) }
            val presses = drafts.mapIndexed { seed, draft -> press(draft, seed) }
            val withGrid = presses.filterIndexed { index, _ -> grids[index] == true }
            val without = presses.filterIndexed { index, _ -> grids[index] == false }
            val undecided = presses.filterIndexed { index, _ -> grids[index] == null }
            println(
                "| $size | $placed | ${withGrid.size}/$samples | ${withGrid.count { it.done != null }}/${withGrid.size} " +
                    "| ${without.size}/$samples | ${without.count { it.done == null }}/${without.size} " +
                    "| ${undecided.size} (${undecided.count { it.done != null }}) | ${maxMillis(withGrid)} | ${maxMillis(without)} |",
            )
        }
    }

    /** A random full grid of [size] cut down to [kept] givens; each seed its own grid and cut. */
    private fun cut(size: Int, kept: Int, seed: Int): SudokuDraft {
        val geometry = geometry(size)
        val blank = List(geometry.cellCount) { 0 }
        val grid = checkNotNull(SudokuRandomFill.fill(geometry, blank, Random(size * 100_000 + seed), Budget.Unlimited))
        return SudokuTestDrafts.cut(geometry, grid, kept, Random(seed))
    }

    /** One press under the Studio's 2 s budget: the finished board or null, and the wall time. */
    private fun press(draft: SudokuDraft, seed: Int): Press {
        val start = System.nanoTime()
        val completion = SudokuCompleter.complete(draft, Random(seed), Budget.within(BUDGET_MS, System::nanoTime))
        return Press((completion as? Completion.Done)?.draft, (System.nanoTime() - start) / 1_000)
    }

    /**
     * The givens a press added to [draft] to reach [done], by stage: first until one solution
     * is left, then until the hint ladder finishes the board; and the search nodes the solver
     * visited in the first stage. That stage is replayed with the press's seed and budget;
     * null when the replay runs out of time.
     */
    private fun stages(draft: SudokuDraft, done: SudokuDraft, seed: Int): Stages? {
        val geometry = geometry(draft.size)
        val random = Random(seed)
        val budget = Budget.within(BUDGET_MS, System::nanoTime)
        val grid = SudokuRandomFill.fill(geometry, draft.givens, random, budget) ?: return null
        val givens = draft.givens.toIntArray()
        var nodes = 0
        val counted = Budget {
            nodes++
            budget.exhausted()
        }
        if (!SudokuCompleter.narrowToOne(geometry, givens, grid, random, counted)) return null
        val unique = givens.count { it != 0 }
        return Stages(unique - draft.givens.count { it != 0 }, done.givens.count { it != 0 } - unique, nodes)
    }

    /** Whether the solver finds a grid for [draft]; null when it cannot tell within [DECIDE_MS]. */
    private fun hasGrid(geometry: SudokuGeometry, draft: SudokuDraft): Boolean? {
        val outcome = SudokuSolver.bounded(geometry, draft.givens, limit = 1, budget = Budget.within(DECIDE_MS, System::nanoTime))
        return (outcome as? SudokuSearchOutcome.Done)?.solutions?.isNotEmpty()
    }

    private fun timings(presses: List<Press>): String {
        val micros = presses.map { it.micros }.sorted()
        return "${millis(micros[micros.size / 2])} | ${millis(micros[(micros.size * 95 + 99) / 100 - 1])} | ${millis(micros.last())}"
    }

    private fun maxMillis(presses: List<Press>): String = presses.maxOfOrNull { it.micros }?.let(::millis) ?: "-"

    private fun millis(micros: Long): String = String.format(Locale.ROOT, "%.2f", micros / 1_000.0)

    private fun mean(counts: List<Int>): String = String.format(Locale.ROOT, "%.1f", counts.average())

    private fun geometry(size: Int): SudokuGeometry = requireNotNull(SudokuVariants.bySize(size)).geometry

    private class Press(val done: SudokuDraft?, val micros: Long)

    private class Stages(val forOne: Int, val forLadder: Int, val nodes: Int)

    private companion object {
        const val BUDGET_MS = 2_000L

        /** How long the solver may look for a grid before a scattered draft counts as undecided. */
        const val DECIDE_MS = 10_000L

        /** Presses per size that warm the JIT before the first measured row. */
        const val WARM_UP = 200

        /** Givens kept per row of the first table: none, a few, a quarter of the board, half of it. */
        val KEPT = mapOf(6 to listOf(0, 1, 2, 4, 9, 18), 9 to listOf(0, 1, 2, 4, 8, 20, 40))

        /** Digits placed per row of the second table. */
        val PLACED = mapOf(6 to listOf(4, 9, 12, 18), 9 to listOf(10, 14, 17, 20, 27, 40))
    }
}
