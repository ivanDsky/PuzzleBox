package com.ivandsky.puzzlebox.games.strands

import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.feed.NytStrandsDailySource
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

/** Real NYT days committed under `src/test/resources/fixtures/<yyyy-MM-dd>.json`. */
object StrandsFixtures {

    /** The verbatim feed body for [date]. */
    fun json(date: LocalDate): String =
        checkNotNull(StrandsFixtures::class.java.getResourceAsStream("/fixtures/$date.json")) { "no fixture for $date" }
            .use { it.readBytes().decodeToString() }

    /** [date]'s puzzle, built by the production feed path (parse, gate, codec). */
    fun puzzle(date: LocalDate): StrandsPuzzle = runBlocking {
        val fetched = NytStrandsDailySource { FetchResult.Ok(json(date)) }.fetchDaily(date)
        val daily = checkNotNull(fetched.getOrNull()) { "fixture $date did not fetch: $fetched" }
        checkNotNull(StrandsCodec.decode(daily.puzzle.payload).getOrNull())
    }
}
