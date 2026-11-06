package com.ivandsky.puzzlebox.games.tango.feed

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * Seed-backed wrapper around [LinkedInTangoDailySource] (user ruling 2026-11-02):
 * LinkedIn gates the Tango daily behind a session (research R-Tango-1 live capture),
 * so until auth ships, fetch failures degrade to a BUNDLED seed puzzle instead of an
 * empty board — the user must always have a playable Tango.
 *
 * The seed is the canonical 12-given grid of the retired synthetic #301 fixture, in
 * LinkedIn's lotka wire shape, brute-force verified exactly-one-solution; the A15 gate
 * re-runs at use.
 * It is NOT a fake LinkedIn response: the wrapped error still surfaces upstream, and
 * the seed's ref carries the requested date, never a LinkedIn edition string.
 * Zip/Queens keep their real anonymous fetches — this wrapper is Tango-only.
 */
class TangoSeedBackedSource(private val primary: LinkedInTangoDailySource) : DailySource {

    override val gameId: GameId = TangoDefinition.id

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        when (val primaryResult = primary.fetchDaily(date)) {
            is FetchResult.Ok -> return primaryResult
            is FetchResult.Err -> {
                // Throttled means LinkedIn WILL serve shortly — never mask it with the seed.
                if (primaryResult.error is FetchError.Throttled) return FetchResult.Err(primaryResult.error)
            }
        }
        return seedDaily(date)
    }

    private fun seedDaily(date: LocalDate): FetchResult<DailyFetch> {
        val daily = when (val parsed = LotkaWireParser.parse(SEED_PAYLOAD, date)) {
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
        return FetchResult.Ok(DailyFetch(envelope, daily.ref))
    }

    companion object {
        /** Lotka-shape payload of the verified seed grid: 12 presets, one `=` and one `×` edge. */
        val SEED_PAYLOAD: String =
            """{"gridSize":6,"presetCellIdxes":[0,3,7,10,13,15,16,18,21,27,31,35],""" +
            """"edges":[{"startIdx":0,"endIdx":1,"isEqual":true},{"startIdx":15,"endIdx":21,"isEqual":false}],""" +
            """"solution":["ZERO","ZERO","ONE","ZERO","ONE","ONE","ZERO","ONE","ZERO","ONE","ONE","ZERO",""" +
            """"ONE","ZERO","ONE","ZERO","ZERO","ONE","ZERO","ONE","ZERO","ONE","ONE","ZERO",""" +
            """"ONE","ONE","ZERO","ONE","ZERO","ZERO","ONE","ZERO","ONE","ZERO","ZERO","ONE"]}"""
    }
}
