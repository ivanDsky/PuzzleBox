package com.ivandsky.puzzlebox.testing.game

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleSource
import java.time.LocalDate

/**
 * A deterministic in-memory fixture [PuzzleSource] for tests: serves the given puzzle per
 * date, fails [FetchError.Unavailable] for any other day.
 */
class InMemoryPuzzleSource(
    private val byDate: Map<LocalDate, Puzzle>,
) : PuzzleSource {

    constructor(vararg pairs: Pair<LocalDate, Puzzle>) : this(mapOf(*pairs))

    override suspend fun fetch(date: LocalDate): FetchResult<Puzzle> {
        val puzzle = byDate[date]
            ?: return FetchResult.Err(FetchError.Unavailable("no fixture for $date"))
        return FetchResult.Ok(puzzle)
    }
}
