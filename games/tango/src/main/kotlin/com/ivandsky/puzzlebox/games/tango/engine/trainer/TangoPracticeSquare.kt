package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep

/** One square of a [TangoPracticeStep]: [idx] holds [value], and [shown] is the engine step the Spotlight draws for it. */
data class TangoPracticeSquare(val idx: Int, val value: TangoCell, val shown: TangoStep)
