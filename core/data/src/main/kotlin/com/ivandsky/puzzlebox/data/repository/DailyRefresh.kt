package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle

/** What [DailyRepository.refreshToday] found. The repository never overwrites a stored daily here. */
sealed interface DailyRefresh {
    /** Nothing was stored; the fetched puzzle is now stored. */
    data class Stored(val puzzle: Puzzle) : DailyRefresh

    /** The fetched puzzle, or the one remembered within the skip window, equals the stored one; nothing written. */
    data object Unchanged : DailyRefresh

    /** A stored puzzle exists and the fetch differs; NOTHING written. */
    data class Changed(val fresh: Puzzle) : DailyRefresh

    data class Failed(val error: FetchError) : DailyRefresh
}
