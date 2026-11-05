package com.ivandsky.puzzlebox.games.strands

/** The hint on the board: the target [word] (a theme word or the spangram) and its [stage]. */
data class ActiveHint(val word: String, val stage: HintStage)
