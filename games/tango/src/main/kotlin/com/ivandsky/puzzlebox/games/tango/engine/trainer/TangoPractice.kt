package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoReveal
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tango.engine.step.chebyshev

/**
 * The practice board's engine (spec §2.2): where a drill starts, which deduction a move belongs to, and what Show me
 * plays. Pure; the caller carries the puzzle's unique solution.
 *
 * Everything reads the board's easiest rung: the lowest rung of [TangoLadder] with at least one deduction on it. That is
 * not the stepper's `next()`, which defers a step the player sees at a glance. Every instance of that rung counts, not
 * only the one a hint would show.
 */
object TangoPractice {

    /**
     * The board a drill of [kind] for [technique] opens on, or null when there is none (spec §2.2, [BoardKind]). The
     * grid holds the givens and solution values only. [walk]'s points whose easiest rung is no rung (a reveal) break
     * runs, as they end a board ([isDone]).
     * - [BoardKind.Focus]: the first point of the longest run of consecutive walk points where [technique] is the
     *   easiest rung (ties: the earliest run).
     * - [BoardKind.Review]: the run of consecutive walk points whose easiest rung is learned ([technique] and every rung
     *   before it on [TangoLadder]) with the most [technique] points (ties: the earliest), at its first [technique]
     *   point.
     *
     * Null when [technique] is no Tango technique, when [solution] is no solution of [puzzle], and when the walk fills
     * the board without [technique] ever being the easiest rung, so both kinds exist on a puzzle exactly when the
     * walker counts [technique] there.
     */
    fun start(puzzle: TangoPuzzle, solution: List<TangoCell>, technique: TechniqueId, kind: BoardKind): List<TangoCell?>? =
        opening(puzzle, solution, technique, kind)?.board

    /**
     * The point [start] opens a board of [kind] at, with the step the walk makes there: what the technique intro
     * demonstrates (frame 0 is exactly the board practice opens at). Null where [start] is null; the step is of
     * [technique]'s rung, never a reveal.
     */
    fun opening(puzzle: TangoPuzzle, solution: List<TangoCell>, technique: TechniqueId, kind: BoardKind): TangoPracticeOpening? {
        val order = TangoLadder.rungs.map { it.first.trainerId }
        val trainedAt = order.indexOf(technique)
        if (trainedAt < 0) return null
        if (!isSolution(puzzle, solution)) return null
        val inRun: (TechniqueId?) -> Boolean = when (kind) {
            BoardKind.Focus -> { id -> id == technique }
            BoardKind.Review -> { id -> id != null && order.indexOf(id) <= trainedAt }
        }
        var best: TangoWalkPoint? = null
        var bestTrained = 0
        var open: TangoWalkPoint? = null
        var trained = 0
        for (point in walk(puzzle, solution)) {
            if (!inRun(point.technique)) {
                trained = 0
                open = null
                continue
            }
            if (point.technique != technique) continue
            if (open == null) open = point
            trained++
            if (trained > bestTrained) {
                best = open
                bestTrained = trained
            }
        }
        return best?.let { TangoPracticeOpening(it.board, checkNotNull(it.step)) }
    }

    /**
     * Whether a board of [kind] for [technique] is done on [grid] (spec §2.2): the puzzle is solved (no square is
     * left), or the easiest rung on [grid] is another technique ([BoardKind.Focus]) or a technique later than
     * [technique] on [TangoLadder] ([BoardKind.Review]), or no rung fires on [grid]: the next move would be a reveal,
     * which no learned technique finds, so nothing more is asked.
     *
     * @throws IllegalArgumentException when [technique] is no Tango technique.
     */
    fun isDone(puzzle: TangoPuzzle, grid: List<TangoCell?>, technique: TechniqueId, kind: BoardKind): Boolean {
        val rungs = TangoLadder.rungs
        val trainedAt = rungs.indexOfFirst { it.first.trainerId == technique }
        require(trainedAt >= 0) { "${technique.value} is no Tango technique" }
        if (grid.none { it == null }) return true
        val easiest = rungs.indexOfFirst { (_, rule) -> rule.forced(puzzle, grid).isNotEmpty() }
        if (easiest < 0) return true
        return when (kind) {
            BoardKind.Focus -> easiest != trainedAt
            BoardKind.Review -> easiest > trainedAt
        }
    }

    /**
     * The practice walk of [puzzle], the one [start] and the technique index share: from the givens, a point for every
     * board the walk stands on, each applying one whole deduction of the easiest rung (the one nearest the last square
     * it filled, then the fewest evidence cells, then the lowest cell), or a reveal when no rung fires. A point's
     * technique is the id of the easiest rung on its board, null when no rung fires. The walk ends when the board is
     * full. [solution] must be a solution of [puzzle].
     */
    internal fun walk(puzzle: TangoPuzzle, solution: List<TangoCell>): Sequence<TangoWalkPoint> = sequence {
        val grid = MutableList<TangoCell?>(puzzle.cellCount) { puzzle.givens[it] }
        var last: Int? = null
        while (grid.any { it == null }) {
            val steps = easiestSteps(puzzle, grid)
            val step = nearestOrReveal(puzzle, solution, grid, last, steps)
            yield(TangoWalkPoint(grid.toList(), steps.firstOrNull()?.rung?.trainerId, step))
            step ?: return@sequence
            step.squares.forEach { grid[it.idx] = it.value }
            last = step.squares.last().idx
        }
    }

    /**
     * [value] at [idx] on [grid], judged for [technique]: the move is [TangoPracticeVerdict.Right] when it is part of a
     * deduction of the easiest rung, [TangoPracticeVerdict.Easier] when it fits [solution] but no easiest-rung
     * deduction makes it, and [TangoPracticeVerdict.Wrong] when it differs from [solution]. A move on a board where no
     * rung fires is Right when it fits: the square has no proof to ask for, so it counts for no technique.
     *
     * Several deductions may make the move; the one reading the fewest cells (then the lowest cell) is its own.
     *
     * @throws IllegalArgumentException when [idx] is off the board, a given or already filled.
     */
    fun judge(
        puzzle: TangoPuzzle,
        solution: List<TangoCell>,
        grid: List<TangoCell?>,
        technique: TechniqueId,
        idx: Int,
        value: TangoCell,
    ): TangoPracticeVerdict {
        require(grid.size == puzzle.cellCount && solution.size == puzzle.cellCount) {
            "grid ${grid.size} and solution ${solution.size} do not match ${puzzle.cellCount} cells"
        }
        require(idx in 0 until puzzle.cellCount && !puzzle.isGiven(idx) && grid[idx] == null) {
            "cell $idx is off the board or a given or already filled"
        }
        val steps = easiestSteps(puzzle, grid)
        val rung = steps.firstOrNull()?.rung
        if (value != solution[idx]) return TangoPracticeVerdict.Wrong(rung)
        if (rung == null) {
            val reveal = TangoPracticeStep(null, listOf(TangoPracticeSquare(idx, value, TangoStep.Reveal(idx, value))))
            return TangoPracticeVerdict.Right(reveal, trained = false)
        }
        val owning = steps.filter { it.contains(idx, value) }.minWithOrNull(compareBy({ it.evidenceSize }, { it.firstIdx }))
        if (owning != null) return TangoPracticeVerdict.Right(owning, trained = rung.trainerId == technique)
        val nearest = steps.minWith(compareBy({ it.distance(puzzle.n, idx) }, { it.evidenceSize }, { it.firstIdx }))
        return TangoPracticeVerdict.Easier(rung, nearest)
    }

    /**
     * What Show me plays on [grid]: one whole deduction of the easiest rung (the one nearest [lastMove], then the
     * fewest evidence cells, then the lowest cell), or a reveal when no rung fires. Null on a full board.
     */
    fun show(puzzle: TangoPuzzle, solution: List<TangoCell>, grid: List<TangoCell?>, lastMove: Int?): TangoPracticeStep? =
        nearestOrReveal(puzzle, solution, grid, lastMove, easiestSteps(puzzle, grid))

    /** Every deduction of the lowest rung that has one on [grid], grouped whole; empty when no rung fires. */
    private fun easiestSteps(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoPracticeStep> {
        for ((rung, rule) in TangoLadder.rungs) {
            val deductions = rule.forced(puzzle, grid)
            if (deductions.isEmpty()) continue
            return deductions
                .groupBy { Instance(rung, it.evidence, it.row, it.col) }
                .values
                .map { group -> TangoPracticeStep(rung, group.map { TangoPracticeSquare(it.idx, it.value, it) }) }
        }
        return emptyList()
    }

    private fun nearestOrReveal(
        puzzle: TangoPuzzle,
        solution: List<TangoCell>,
        grid: List<TangoCell?>,
        lastMove: Int?,
        steps: List<TangoPracticeStep>,
    ): TangoPracticeStep? {
        if (steps.isNotEmpty()) {
            return steps.minWith(compareBy({ it.distance(puzzle.n, lastMove) }, { it.evidenceSize }, { it.firstIdx }))
        }
        val reveal = TangoReveal.pick(puzzle, grid, lastMove, solution) ?: return null
        return TangoPracticeStep(null, listOf(TangoPracticeSquare(reveal.idx, reveal.value, reveal)))
    }

    /** What the deductions of one step share: the same proof of the same line. */
    private data class Instance(val rung: TangoStepRung, val evidence: Set<Int>, val row: Int?, val col: Int?)

    private val TangoPracticeStep.firstIdx: Int get() = squares.minOf { it.idx }

    private fun TangoPracticeStep.distance(n: Int, from: Int?): Int = squares.minOf { chebyshev(n, it.idx, from) }

    /** [solution] fills every cell, keeps every given and breaks no rule. */
    private fun isSolution(puzzle: TangoPuzzle, solution: List<TangoCell>): Boolean =
        solution.size == puzzle.cellCount &&
            puzzle.givens.all { (idx, value) -> solution[idx] == value } &&
            TangoRules.isComplete(TangoState(puzzle, solution))
}
