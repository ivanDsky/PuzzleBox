package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * The level a publish carries (96): the Studio's [proposed] one, from the scored grader's verdict, and the one the
 * author [chosen]. The author's own pick is kept only while it differs from the proposal, so a board change that moves the
 * proposal takes an unchanged choice along and leaves an explicit one where the author put it.
 */
data class LevelChoice(val proposed: Level, val chosen: Level) {

    /** The author's level is not the proposed one. */
    val changed: Boolean get() = chosen != proposed

    /** What to keep on the draft once the author picks [next]: null (follow the proposal) when it is the proposal. */
    fun pickFor(next: Level): Level? = next.takeIf { it != proposed }

    companion object {
        /**
         * The choice for [verdict] and the author's kept [pick]; null when the board has no proposal (not solved, needs
         * guessing, or a game that does not score), so no control is shown and nothing is stored.
         */
        fun of(verdict: Verdict, pick: Level?): LevelChoice? {
            val proposed = (verdict as? Verdict.Unique)?.takeUnless { it.needsGuessing }?.difficulty?.level ?: return null
            return LevelChoice(proposed, pick ?: proposed)
        }
    }
}
