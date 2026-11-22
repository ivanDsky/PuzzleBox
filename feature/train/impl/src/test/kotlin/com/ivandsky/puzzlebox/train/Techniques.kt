package com.ivandsky.puzzlebox.train

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

/** Techniques named after their ids, for a catalog of [ids]. */
internal fun techniques(vararg ids: String): List<TrainerTechnique> =
    ids.map { TrainerTechnique(TechniqueId(it), it.replaceFirstChar(Char::uppercase), "The line of $it.") }
