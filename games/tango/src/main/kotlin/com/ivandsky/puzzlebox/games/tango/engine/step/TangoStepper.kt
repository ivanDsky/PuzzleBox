package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import kotlin.math.abs
import kotlin.math.max

/**
 * The one next stepwise hint on a Tango board: a wrong entry first, then the cheapest rung, then a
 * reveal, or [TangoStep.Solved]. Pure and bounded: it never calls the solver. The
 * solution comes from the caller (null when the solver ran out of budget or the board is not
 * unique): without it only rule breaks are mistakes and nothing is revealed.
 *
 * The cheapest rung is not always first: a step the player sees at a glance waits while a later
 * rung forces a cell they cannot see that way.
 */
object TangoStepper {

    /**
     * D0..D2, the rungs seen at a glance: one sign with a filled side, or two equal cells one cell
     * from three in a row. Their proof is a single marker or pair, with nothing to count.
     */
    private val glance: List<TangoStepRule> = listOf(
        TangoLegacyStepRules.sign,
        TangoLegacyStepRules.pair,
        TangoLegacyStepRules.sandwich,
    )

    /** D3..D8, the rungs that take counting or reading a whole line. */
    private val worked: List<TangoStepRule> = listOf(
        TangoLegacyStepRules.fullLine,
        TangoEqualPairRule,
        TangoSignCountRule,
        TangoSplitPairRule,
        TangoCountSqueezeRule,
        TangoLegacyStepRules.lineOptions,
    )

    /**
     * The first [worked] rung that forces a cell no [glance] rung forces wins, and a glance step is
     * returned only when no such cell exists. Then a mistake, the solved check and the reveal.
     */
    fun next(puzzle: TangoPuzzle, grid: List<TangoCell?>, lastMove: Int?, solution: List<TangoCell>?): TangoStep {
        require(grid.size == puzzle.cellCount) { "grid holds ${grid.size} cells, puzzle ${puzzle.cellCount}" }
        require(solution == null || solution.size == puzzle.cellCount) { "solution size ${solution?.size}" }
        mistake(puzzle, grid, lastMove, solution)?.let { return it }
        if (grid.none { it == null } && TangoRules.isComplete(TangoState(puzzle, grid))) return TangoStep.Solved
        // With a solution every wrong entry was returned above, so the rungs read only givens and
        // correct entries (A5); without one, entries off the solution cannot be told apart.
        deduction(puzzle, grid, lastMove)?.let { return it }
        if (solution == null) return TangoStep.Unavailable
        return TangoReveal.pick(puzzle, grid, lastMove, solution) ?: TangoStep.Unavailable
    }

    /** The deduction to show: within a rung the one nearest [lastMove], then fewest evidence cells, then lowest index. */
    private fun deduction(puzzle: TangoPuzzle, grid: List<TangoCell?>, lastMove: Int?): TangoStep.Deduction? {
        val nearest = compareBy<TangoStep.Deduction>({ chebyshev(puzzle.n, it.idx, lastMove) }, { it.evidence.size }, { it.idx })
        val glanced = glance.map { it.forced(puzzle, grid) }
        val seen = glanced.flatMapTo(HashSet()) { steps -> steps.map { it.idx } }
        // With nothing seen at a glance this is the catalog order over the worked rungs.
        return worked.firstNotNullOfOrNull { rung -> rung.forced(puzzle, grid).filter { it.idx !in seen }.minWithOrNull(nearest) }
            ?: glanced.firstNotNullOfOrNull { it.minWithOrNull(nearest) }
    }

    /** The player entry to accuse, nearest [lastMove] then lowest index; givens are never accused. */
    private fun mistake(puzzle: TangoPuzzle, grid: List<TangoCell?>, lastMove: Int?, solution: List<TangoCell>?): TangoStep.Mistake? {
        val accused = (0 until puzzle.cellCount).mapNotNull { idx ->
            val value = grid[idx]
            if (value == null || puzzle.isGiven(idx)) return@mapNotNull null
            val reason = brokenRule(puzzle, grid, idx, value)
            when {
                solution != null -> if (value != solution[idx]) TangoStep.Mistake(idx, reason) else null
                reason != null -> TangoStep.Mistake(idx, reason)
                else -> null
            }
        }
        return accused.minWithOrNull(compareBy({ chebyshev(puzzle.n, it.idx, lastMove) }, { it.idx }))
    }

    /** The rule [idx] breaks on [grid], checked in the validation order (marker, triple, count). */
    private fun brokenRule(puzzle: TangoPuzzle, grid: List<TangoCell?>, idx: Int, value: TangoCell): TangoVerdict.Reason? {
        val n = puzzle.n
        val row = List(n) { idx / n * n + it }
        val col = List(n) { it * n + idx % n }
        return when {
            TangoMarkerRule.violationAt(puzzle, grid, idx) -> TangoVerdict.Reason.MarkerConflict
            inRunOfThree(grid, row, idx % n, value) || inRunOfThree(grid, col, idx / n, value) -> TangoVerdict.Reason.ThreeInARow
            row.count { grid[it] == value } > n / 2 || col.count { grid[it] == value } > n / 2 -> TangoVerdict.Reason.CountExceeded
            else -> null
        }
    }

    /** Whether position [pos] of [line] lies in three equal cells in a row. */
    private fun inRunOfThree(grid: List<TangoCell?>, line: List<Int>, pos: Int, value: TangoCell): Boolean =
        (pos - 2..pos).any { start -> start >= 0 && start + 2 < line.size && (start..start + 2).all { grid[line[it]] == value } }
}

/** Chebyshev distance between cells [a] and [b] of an [n]×[n] board; 0 when [b] is null, so every cell ties. */
internal fun chebyshev(n: Int, a: Int, b: Int?): Int =
    if (b == null) 0 else max(abs(a / n - b / n), abs(a % n - b % n))
