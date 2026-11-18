package com.ivandsky.puzzlebox.domain.lineup

/** What one source hands [ObservePickablePuzzles]: its rows with the time each was made, whether they are stale for lack of a connection, and whether the daily archive has a page left. */
internal class SourceRows(val rows: List<Dated>, val offline: Boolean = false, val moreDays: Boolean = false) {

    class Dated(val atMs: Long, val puzzle: PickablePuzzle)
}

/** The meta line of a puzzle: its size from the variant (`queens/n8` → `n8`) and its difficulty level, when it has one. */
internal fun metaOf(variant: String, level: String?): String =
    listOfNotNull(variant.substringAfter('/').ifEmpty { null }, level).joinToString(" · ")
