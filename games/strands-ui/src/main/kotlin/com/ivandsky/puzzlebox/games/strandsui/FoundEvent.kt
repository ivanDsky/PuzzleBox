package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord

/** A word just found: its [cells] in spelling order. [seq] retriggers the settle or sweep. */
data class FoundEvent(val cells: List<Coord>, val spangram: Boolean, val seq: Int)
