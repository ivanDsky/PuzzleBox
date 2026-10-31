package com.ivandsky.puzzlebox.game.testing

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleSource
import java.time.LocalDate

/**
 * The puzzle seam's second adapter (F1 §2): a deterministic in-memory fixture source for
 * tests and previews. The production adapters (LinkedInDaily, ticket 12; Strands, Phase 3)
 * and the future archive (ADR-0002) implement [PuzzleSource] the same way.
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
