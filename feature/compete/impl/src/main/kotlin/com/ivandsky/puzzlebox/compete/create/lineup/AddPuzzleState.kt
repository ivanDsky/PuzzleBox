package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery

/** One result of the add sheet and whether that puzzle is already in the lineup. */
internal data class PickerEntry(val puzzle: PickablePuzzle, val added: Boolean)

/** What the exact-puzzle search shows: the [query] it was asked, its results, whether they are only what was cached, and whether the daily archive has a page left, and the line of a code lookup under way or ended without a puzzle. [loaded] is false until the first list arrives. */
internal data class AddPuzzleState(
    val query: PickerQuery = NO_QUERY,
    val rows: List<PickerEntry> = emptyList(),
    val offline: Boolean = false,
    val moreDays: Boolean = false,
    val lookup: String? = null,
    val canRetry: Boolean = false,
    val loaded: Boolean = false,
)

internal val NO_QUERY = PickerQuery(text = "", fresh = emptySet(), sources = emptySet(), games = emptySet())
