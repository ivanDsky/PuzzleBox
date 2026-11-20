package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell

/**
 * One point of [TangoPractice.walk]: the [board] the walk stands on (givens and solution values, a snapshot) and the
 * [technique] it is the easiest rung there, the trainer id of the lowest ladder rung with a deduction on [board].
 * Null when no rung fires and the walk reveals a square.
 */
internal data class TangoWalkPoint(val board: List<TangoCell?>, val technique: TechniqueId?)
