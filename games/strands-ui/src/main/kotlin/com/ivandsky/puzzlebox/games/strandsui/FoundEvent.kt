package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord

/**
 * A word just found: its [cells] in spelling order. [seq] retriggers the settle or sweep. [inSolveMoment] marks the
 * word that solved the board while the solve moment B plays: its letters start closer together, to rest inside it.
 */
data class FoundEvent(val cells: List<Coord>, val spangram: Boolean, val seq: Int, val inSolveMoment: Boolean = false)
