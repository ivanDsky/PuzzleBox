package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId

/**
 * One strategy for finding a full drill puzzle (the technique index, the generated set, ...). A
 * game lists its strategies in [TrainerGame.drillSources], and an implementer's name ends in
 * `DrillSource`.
 */
fun interface DrillSource {
    /**
     * Puzzles that need [technique], most target steps first; empty when this source has none.
     */
    fun candidates(technique: TechniqueId): List<DrillCandidate>
}
