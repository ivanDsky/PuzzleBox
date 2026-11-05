package com.ivandsky.puzzlebox.games.strands

/** A theme word or the spangram: its uppercase [text] and its intended [cells] in spelling order (A4). */
data class StrandsWord(val text: String, val cells: List<Coord>)
