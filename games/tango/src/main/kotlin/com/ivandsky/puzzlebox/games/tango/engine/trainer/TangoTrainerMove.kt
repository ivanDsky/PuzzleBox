package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.TangoCell

/** The one move a Tango snippet round takes (spec §2.2): a sun or a moon on the empty cell [idx]. There is no cross and no clear. */
data class TangoTrainerMove(val idx: Int, val value: TangoCell)
