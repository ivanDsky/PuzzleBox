package com.ivandsky.puzzlebox.games.strands

/**
 * A 2 × 3 board small enough to reason about by hand:
 * ```
 * C A T      theme CAT = (0,0)(0,1)(0,2); also traceable as (0,0)(1,0)(1,1)
 * A T S      spangram ATS = (1,0)(1,1)(1,2)
 * ```
 * TAT is a valid non-theme word.
 */
val tinyPuzzle = StrandsPuzzle(
    rows = 2,
    cols = 3,
    letters = "CATATS",
    clue = "Feline 🐈",
    spangram = StrandsWord("ATS", listOf(Coord(1, 0), Coord(1, 1), Coord(1, 2))),
    themeWords = listOf(StrandsWord("CAT", listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2)))),
    validWords = setOf("CAT", "ATS", "TAT"),
    constructors = "Test Setter",
)
