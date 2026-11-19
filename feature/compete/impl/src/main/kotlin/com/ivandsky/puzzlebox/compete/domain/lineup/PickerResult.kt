package com.ivandsky.puzzlebox.compete.domain.lineup

/** The rows that pass the query, newest first, whether the community part is only what was cached, and whether the daily archive has a page left to read. */
data class PickerResult(val rows: List<PickablePuzzle>, val offline: Boolean, val moreDays: Boolean = false)
