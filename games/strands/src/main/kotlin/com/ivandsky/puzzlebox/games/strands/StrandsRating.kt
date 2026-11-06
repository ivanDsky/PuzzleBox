package com.ivandsky.puzzlebox.games.strands

/** The solved rating word, from hints used (A36). Its copy lives in the UI. */
enum class StrandsRating {
    Perfect,
    Great,
    WellDone,
    ;

    companion object {
        fun of(hintsUsed: Int): StrandsRating = when {
            hintsUsed <= 0 -> Perfect
            hintsUsed == 1 -> Great
            else -> WellDone
        }
    }
}
