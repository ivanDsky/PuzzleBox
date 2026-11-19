package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId

/**
 * One row of a game's technique list: its stable [id], the [name] the list shows, and a plain
 * one-sentence [line] explaining it (spec A1: the existing hint copy as a plain sentence, never a
 * third copy set and never jargon alone).
 */
data class TrainerTechnique(
    val id: TechniqueId,
    val name: String,
    val line: String,
)
