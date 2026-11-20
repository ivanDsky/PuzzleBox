package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.trainer.TechniqueId

/** The keep rule: when a walked candidate puzzle serves a target technique (spec §2.5). */
object DrillKeep {

    /**
     * The steps [target] takes in a puzzle whose walk counted [counts], or null when the puzzle does not serve it:
     * the walk never used [target], or it used a technique ranked above [target] in [families] (a pair in a drill for
     * pointing). A technique [families] does not rank never rejects a puzzle, and a [target] it does not rank sets no
     * ceiling. Pure: it reads only the counts, so a fake walker can drive it.
     */
    fun targetSteps(counts: Map<TechniqueId, Int>, target: TechniqueId, families: Map<TechniqueId, Int>): Int? {
        val steps = counts[target]?.takeIf { it > 0 } ?: return null
        val ceiling = families[target] ?: return steps
        val beyond = counts.any { (id, count) -> count > 0 && (families[id] ?: Int.MIN_VALUE) > ceiling }
        return if (beyond) null else steps
    }
}
