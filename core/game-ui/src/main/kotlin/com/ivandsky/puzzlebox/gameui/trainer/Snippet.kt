package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId

/**
 * One board stopped just before a step of [technique]: the unit of a snippet round. Opaque to
 * callers, who only hand it back to the [TrainerGame] that built it; each game's adapter keeps its
 * own board-carrying implementation internal.
 */
interface Snippet {
    /** The technique the step this snippet stops before belongs to. */
    val technique: TechniqueId

    /** The one line the round shows above the board: what the player is asked to find, naming [technique]. */
    val prompt: String
}
