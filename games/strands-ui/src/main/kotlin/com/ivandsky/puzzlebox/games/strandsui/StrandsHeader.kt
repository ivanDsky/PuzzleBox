package com.ivandsky.puzzlebox.games.strandsui

/** The puzzle's header facts: the clue verbatim, "No. n" when numbered, and "by <constructors>". */
data class StrandsHeader(val clue: String, val puzzleNumber: Int?, val credit: String)
