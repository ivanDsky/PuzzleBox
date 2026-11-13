package com.ivandsky.puzzlebox.history.ranks

/**
 * Everything the hub renders, one slice per concern; each slot reads only its own slice. Later
 * slots (top bar, calendar, sheets) add their slices here, each type in its own file.
 */
internal data class RanksUiState(
    val board: RanksBoardSlice,
)
