package com.ivandsky.puzzlebox.domain.lineup

/** The rows that pass the query, newest first, and whether the community part is only what was cached. */
data class PickerResult(val rows: List<PickablePuzzle>, val offline: Boolean)
