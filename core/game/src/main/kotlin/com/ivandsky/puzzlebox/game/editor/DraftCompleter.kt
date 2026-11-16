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
     * A completion of [draft]: every piece the author placed is kept, and the board is only ever
     * returned when its analysis is Unique (and, on the ladder games, not needs-guessing). Polls
     * [budget] in its loop; [Completion.GaveUp] when it is spent or no completion was found.
     */
    fun complete(draft: D, random: Random, budget: Budget): Completion<D>
}
