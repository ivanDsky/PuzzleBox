package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId

/** Merges two devices' progress for one game. Pure; commutative, idempotent and associative. */
object TrainerProgressMerge {

    /**
     * The union of both maps, unknown ids included. Per technique the higher mark wins, so a mark
     * never regresses; the in-a-row count and its timestamp come from the later answer, and on an
     * equal timestamp the higher count wins, so the result does not depend on argument order.
     */
    fun merge(
        a: Map<TechniqueId, TechniqueProgress>,
        b: Map<TechniqueId, TechniqueProgress>,
    ): Map<TechniqueId, TechniqueProgress> {
        val merged = a.toMutableMap()
        for ((id, theirs) in b) {
            val mine = merged[id]
            merged[id] = if (mine == null) theirs else mergeOne(mine, theirs)
        }
        return merged
    }

    private fun mergeOne(x: TechniqueProgress, y: TechniqueProgress): TechniqueProgress {
        val later = when {
            x.answeredAtMs != y.answeredAtMs -> if (x.answeredAtMs > y.answeredAtMs) x else y
            else -> if (x.inRow >= y.inRow) x else y
        }
        return TechniqueProgress(maxOf(x.mark, y.mark), later.inRow, later.answeredAtMs)
    }
}
