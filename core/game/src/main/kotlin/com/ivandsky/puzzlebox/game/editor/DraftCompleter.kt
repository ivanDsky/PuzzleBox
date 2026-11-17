package com.ivandsky.puzzlebox.game.editor

import kotlin.random.Random

/**
 * Capability (ADR-0019): a [GameEditor] that can finish a half-built draft (Finish for me). Callers
 * find it with `as?`, so a game without one has no Finish for me and no editor breaks. Pure and
 * blocking; no dispatcher (Konsist P7).
 */
interface DraftCompleter<D : Any> {
    /**
     * Whether the placed pieces can still lead to a solution and the per-game minimum is met.
     * Cheap and pure: it never searches, so a UI may call it on every edit. It is not the verdict.
     */
    fun readiness(draft: D): FinishReadiness

    /**
     * A completion of [draft]: the board is only ever returned when its analysis is Unique (and, on
     * the ladder games, not needs-guessing). Polls [budget] in its loop; [Completion.GaveUp] when it
     * is spent, or the search ended, with no such board found.
     *
     * [target] is the level the author asked for (90); null is "any", the first board found. A
     * completer aims for the target within [budget] (see [FinishBudget]), grading each board it
     * finds on the scale of its [ScoredGrader]. When it cannot hit the target it returns the
     * closest graded board it found as [Completion.Done], never [Completion.GaveUp] just for
     * missing the level; the caller sees the miss in the board's scored verdict.
     * [ClosestCompletion] holds that rule.
     *
     * Every piece the author placed is kept, with one exception (63b, 107): a Queens board painted
     * by hand, full or nearly full, often has no unique completion that keeps every painted cell
     * and every crown. Asked for a level, Queens then moves a crown along its row first, and only
     * when that cannot finish the board repaints cells the author painted ([changedPieces]).
     */
    fun complete(draft: D, random: Random, budget: Budget, target: Difficulty.Level? = null): Completion<D>

    /**
     * How many of the author's pieces the finished [draft] a [complete] returned holds changed,
     * for the Studio to say so; 0 for a completer that keeps them all. Queens counts the painted
     * cells it recoloured, which its draft carries so the editor can highlight them.
     */
    fun changedPieces(draft: D): Int = 0
}
