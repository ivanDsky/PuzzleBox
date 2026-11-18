package com.ivandsky.puzzlebox.gameui

/** What only a community puzzle's hero adds: [chips] (its rank on the puzzle, the author's time) that follow the Hints chip. */
data class ResultCommunity(val chips: List<ResultHeroChip>)
