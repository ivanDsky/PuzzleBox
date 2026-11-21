package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell

/** The board a practice drill opens at ([TangoPractice.opening]) and the whole deduction the walk makes there. */
data class TangoPracticeOpening(val board: List<TangoCell?>, val step: TangoPracticeStep)
