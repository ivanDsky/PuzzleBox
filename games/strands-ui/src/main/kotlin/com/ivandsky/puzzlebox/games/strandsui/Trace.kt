package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord

/** The live, uncommitted trace: cells in the order they were added. Never persisted (A30). */
data class Trace(val path: List<Coord> = emptyList())
