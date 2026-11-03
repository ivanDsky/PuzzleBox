package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.network.TangoWireParser
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleSource
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import java.time.LocalDate

/**
 * Seed-backed wrapper around [LinkedInTangoDailySource] (user ruling 2026-11-02):
 * LinkedIn gates the Tango daily behind a session (research R-Tango-1 live capture),
 * so until auth ships, fetch failures degrade to a BUNDLED seed puzzle instead of an
 * empty board — the user must always have a playable Tango.
 *
 * The seed is the canonical 12-given fixture grid (fixtures/linkedin-tango-rsc-301.txt
 * provenance), brute-force verified exactly-one-solution; the A15 gate re-runs at use.
 * It is NOT a fake LinkedIn response: the wrapped error still surfaces upstream, and
 * the seed's ref carries the requested date, never a LinkedIn edition string.
 * Zip/Queens keep their real anonymous fetches — this wrapper is Tango-only.
 */
class TangoSeedBackedSource(private val primary: LinkedInTangoDailySource) : PuzzleSource, TangoDailySource {

    override suspend fun fetch(date: LocalDate): FetchResult<Puzzle> =
        when (val result = fetchDaily(date)) {
            is FetchResult.Ok -> FetchResult.Ok(result.value.puzzle)
            is FetchResult.Err -> result
        }

    override suspend fun fetchDaily(date: LocalDate): FetchResult<TangoDailySource.Daily> {
        when (val primaryResult = primary.fetchDaily(date)) {
            is FetchResult.Ok -> return primaryResult
            is FetchResult.Err -> {
                // Throttled means LinkedIn WILL serve shortly — never mask it with the seed.
                if (primaryResult.error is FetchError.Throttled) return FetchResult.Err(primaryResult.error)
            }
        }
        return seedDaily(date)
    }

    private fun seedDaily(date: LocalDate): FetchResult<TangoDailySource.Daily> {
        val daily = when (val parsed = TangoWireParser.parse(SEED_PAYLOAD, date)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err ->
                return FetchResult.Err(FetchError.Unavailable("seed parse failed: ${parsed.error}"))
        }
        if (!TangoUniqueness.isUniquelySolvable(daily.puzzle)) {
            return FetchResult.Err(FetchError.Unavailable("seed is not uniquely solvable"))
        }
        val envelope = Puzzle(
            gameId = TangoDefinition.id,
            variant = daily.puzzle.variant,
            formatVersion = TangoCodec.VERSION,
            payload = TangoCodec.encode(daily.puzzle),
        )
        return FetchResult.Ok(TangoDailySource.Daily(envelope, daily.ref))
    }

    companion object {
        /** Wire-shape payload of the verified fixture grid (see fixture 301 for provenance). */
        val SEED_PAYLOAD: String =
            """{"gridSize":6,"given":[["S","","","S","",""],["","M","","","M",""],["","S","","S","S",""],["S","","","M","",""],["","","","M","",""],["","S","","","","M"]],""" +
            """"constraints":[{"r1":0,"c1":0,"r2":0,"c2":1,"kind":"="},{"r1":2,"c1":3,"r2":3,"c2":3,"kind":"x"}],"zone":"America/Los_Angeles"}"""
    }
}
