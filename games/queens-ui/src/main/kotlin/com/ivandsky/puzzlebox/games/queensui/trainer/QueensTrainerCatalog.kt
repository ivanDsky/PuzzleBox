package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queensui.hint.QueensStepCopy

/**
 * The Queens technique list: one row per [QueensLadder.Stepwise] rung with a Trainer id, in the stepper's own order
 * (easiest first). The name is the list's own; the line is that rung's sentence in [QueensStepCopy.technique]: one copy set, with the hint. Rows are keyed by
 * [TechniqueId], the stored key, never by a rung's class name.
 */
internal object QueensTrainerCatalog {

    private val names: Map<TechniqueId, String> = mapOf(
        TechniqueId("singles") to "Singles",
        TechniqueId("line-lock") to "Line lock",
        TechniqueId("shared-squares") to "Shared squares",
        TechniqueId("pigeonhole-regions") to "Pigeonhole (colors)",
        TechniqueId("pigeonhole-lines") to "Pigeonhole (rows and columns)",
        TechniqueId("leftover-queen") to "Leftover queen",
        TechniqueId("squeeze") to "Squeeze",
        TechniqueId("count-squeeze") to "Count squeeze",
        TechniqueId("short-look-ahead") to "Short look-ahead",
        TechniqueId("long-look-ahead") to "Long look-ahead",
    )

    /** A rung with a Trainer id and no name here fails on first use, so a new technique cannot ship unnamed. */
    val techniques: List<TrainerTechnique> = QueensLadder.Stepwise.rungs.mapNotNull { rung ->
        val id = rung.trainerId ?: return@mapNotNull null
        TrainerTechnique(id, names.getValue(id), QueensStepCopy.technique(rung.captionKey))
    }

    /** The list's name for [id], the one the round's prompt uses. */
    fun name(id: TechniqueId): String = names.getValue(id)
}
