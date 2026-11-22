package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoStepCopy
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

/**
 * The Tango technique list: one row per [TangoStepRung], in the stepper's own order (easiest first; the ids test pins
 * the declaration order to it). The name is the list's own; the line is the rung's technique-level sentence
 * ([TangoStepCopy.technique]), written with no board to point at. Rows are keyed by
 * [TechniqueId], the stored key.
 */
internal object TangoTrainerCatalog {

    private val names: Map<TechniqueId, String> = mapOf(
        TechniqueId("sign") to "Sign",
        TechniqueId("pair") to "Pair",
        TechniqueId("sandwich") to "Sandwich",
        TechniqueId("full-line") to "Full line",
        TechniqueId("equal-pair") to "Equal pair",
        TechniqueId("sign-count") to "Sign count",
        TechniqueId("split-pair") to "Split pair",
        TechniqueId("count-squeeze") to "Count squeeze",
        TechniqueId("line-options") to "Line options",
    )

    /** A rung with no name here fails on first use, so a new technique cannot ship unnamed. */
    val techniques: List<TrainerTechnique> = TangoStepRung.entries.map { rung ->
        TrainerTechnique(rung.trainerId, names.getValue(rung.trainerId), TangoStepCopy.technique(rung))
    }

    /** The list's name for [id], the one a practice board's Easier line names. */
    fun name(id: TechniqueId): String = names.getValue(id)
}
