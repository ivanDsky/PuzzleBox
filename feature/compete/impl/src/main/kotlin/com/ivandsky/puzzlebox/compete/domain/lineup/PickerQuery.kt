package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId

/**
 * The picker page's search [text] and chips. Groups combine with AND, the values of one group with OR; an empty group
 * lets everything through.
 */
data class PickerQuery(
    val text: String,
    val fresh: Set<Freshness>,
    val sources: Set<PuzzleSource>,
    val games: Set<GameId>,
)
