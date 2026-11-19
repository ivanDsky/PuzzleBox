package com.ivandsky.puzzlebox.compete.domain.lineup

/** What one source hands [ObservePickablePuzzles]: its rows with the time each was made, whether they are stale for lack of a connection, and whether the daily archive has a page left. */
internal class SourceRows(val rows: List<Dated>, val offline: Boolean = false, val moreDays: Boolean = false) {

    class Dated(val atMs: Long, val puzzle: PickablePuzzle)
}

/** The meta line of a puzzle: its size from the variant (`queens/n8` → `n8`); the difficulty level is [PickablePuzzle.level]. */
internal fun metaOf(variant: String): String = variant.substringAfter('/')
