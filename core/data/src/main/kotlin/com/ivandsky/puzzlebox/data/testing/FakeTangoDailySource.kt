package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.data.repository.TangoDailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import java.time.LocalDate

/**
 * Handwritten fake of the Tango LinkedIn daily adapter for feature tests: queue the
 * next outcome, record the requested dates. Default fails Unavailable so tests assert
 * the degrade path explicitly by queueing an Ok.
 */
class FakeTangoDailySource : TangoDailySource {

    val requestedDates = mutableListOf<LocalDate>()
    var nextPuzzle: Puzzle? = null
    var nextPuzzleNumber: Int? = null
    var nextError: FetchError =
        FetchError.Unavailable("FakeTangoDailySource: nothing queued")

    override suspend fun fetchDaily(date: LocalDate): FetchResult<TangoDailySource.Daily> {
        requestedDates += date
        val puzzle = nextPuzzle ?: return FetchResult.Err(nextError)
        // Mirror the real adapter: the ref is always built from the requested day.
        return FetchResult.Ok(
            TangoDailySource.Daily(puzzle, TangoPuzzleRef(date = date, puzzleNumber = nextPuzzleNumber)),
        )
    }
}
