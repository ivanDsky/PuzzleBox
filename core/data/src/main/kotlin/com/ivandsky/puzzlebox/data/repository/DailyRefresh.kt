package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle

/** What [DailyRepository.refreshToday] found. The repository never overwrites a stored daily here. */
sealed interface DailyRefresh {
    /** Nothing was stored; the fetched puzzle is now stored. */
    data class Stored(val puzzle: Puzzle) : DailyRefresh

    /** Fetched equals stored, or a refresh succeeded within the skip window; nothing written. */
    data object Unchanged : DailyRefresh

    /** A stored puzzle exists and the fetch differs; NOTHING written. */
    data class Changed(val fresh: Puzzle) : DailyRefresh

    data class Failed(val error: FetchError) : DailyRefresh
}
