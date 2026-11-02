package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.network.LinkedInTangoClient
import com.ivandsky.puzzlebox.data.network.TangoGameExtractor
import com.ivandsky.puzzlebox.data.network.TangoWireParser
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleSource
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import java.time.LocalDate

/**
 * The LinkedInDaily adapter for Tango (ADR-0002): wire body -> [TangoWireParser] ->
 * canonical [TangoCodec] bytes in a [Puzzle] envelope. Implements [PuzzleSource] for
 * the archive seam; [fetchDaily] additionally carries the fetch-time identity
 * ([TangoPuzzleRef]) that core/data persists per ADR-0004.
 *
 * Error split per the FetchError contract: transport/parse failures are
 * [FetchError.Unavailable]; the throttle signature is [FetchError.Throttled] (the
 * client decides from the wire, not the URL). A payload naming an unknown variant is
 * [FetchError.Unavailable] (parse family) — ADR-0005 never guesses the variant.
 */
class LinkedInTangoDailySource(private val client: LinkedInTangoClient) : PuzzleSource, TangoDailySource {

    override suspend fun fetch(date: LocalDate): FetchResult<Puzzle> =
        when (val result = fetchDaily(date)) {
            is FetchResult.Ok -> FetchResult.Ok(result.value.puzzle)
            is FetchResult.Err -> result
        }

    override suspend fun fetchDaily(date: LocalDate): FetchResult<TangoDailySource.Daily> {
        val body = when (val fetched = client.fetchDailyBody()) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = TangoGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no tangoGamePuzzle in body"))
        val daily = when (val parsed = TangoWireParser.parse(match.tangoGamePuzzleJson, date, match.editionText)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err -> return FetchResult.Err(FetchError.Unavailable("wire parse failed: ${parsed.error}"))
        }
        val envelope = Puzzle(
            gameId = TangoDefinition.id,
            variant = daily.puzzle.variant,
            formatVersion = TangoCodec.VERSION,
            payload = TangoCodec.encode(daily.puzzle), // canonical bytes: storage + dedup (A19)
        )
        return FetchResult.Ok(TangoDailySource.Daily(envelope, daily.ref))
    }
}
