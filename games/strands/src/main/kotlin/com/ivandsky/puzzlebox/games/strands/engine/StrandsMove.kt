package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.games.strands.Coord

/** One submitted trace, cells in the order the finger passed them. */
data class StrandsMove(val path: List<Coord>) : Move
