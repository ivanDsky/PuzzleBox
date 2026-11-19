package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.game.Puzzle

/**
 * A full puzzle a [DrillSource] offers to drill one technique. [targetSteps] is how many steps the
 * target technique takes in it, the figure a source orders its candidates by.
 */
sealed interface DrillCandidate {
    /** Steps the target technique takes in this puzzle. */
    val targetSteps: Int

    /**
     * An archive daily picked through the technique index. Its content needs a fetch the first time
     * (spec A7: like an archive day), then plays offline from the cache.
     *
     * @property number the archive puzzle number
     */
    data class Archive(val number: Int, override val targetSteps: Int) : DrillCandidate

    /**
     * A generated puzzle that ships with the app, so it is always offline. [id] is immutable: it keys
     * the puzzle for as long as the bundled set carries it.
     *
     * @property id the bundled puzzle's stable id
     * @property puzzle the puzzle itself
     */
    data class Bundled(val id: String, val puzzle: Puzzle, override val targetSteps: Int) : DrillCandidate
}
