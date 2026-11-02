package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleSource
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.time.LocalDate

/**
 * What the queens repository consumes from a daily source: the [PuzzleSource] result plus
 * the fetch-time identity (ADR-0004 — core/data persists date + edition number).
 * Production adapter: [LinkedInQueensDailySource].
 */
interface QueensDailySource {
    data class Daily(val puzzle: Puzzle, val ref: QueensPuzzleRef)

    suspend fun fetchDaily(date: LocalDate): FetchResult<Daily>
}

/**
 * The ADR-0002 LinkedInDaily adapter for Queens (zip LinkedInZipDailySource precedent):
 * raw body -> [QueensGameExtractor] -> [QueensWireParser] -> the A13+A5 gate
 * ([QueensUniquenessGate]) -> canonical [QueensCodec] bytes in a [Puzzle] envelope under
 * `gameId=queens`. Implements [PuzzleSource] for the archive seam.
 *
 * The network rung is a seam ([BodyClient]) so the extractor/gate/envelope path is
 * JVM-testable without OkHttp; core/data binds the Retrofit client at registration.
 *
 * Error split per the FetchError contract: transport/parse failures are
 * [FetchError.Unavailable]; a payload the engine cannot trust is [FetchError.Untrusted] —
 * never playable (A13), no silent fallback.
 */
class LinkedInQueensDailySource(private val bodyClient: BodyClient) : PuzzleSource, QueensDailySource {

    /** Produces the raw body text the extractor scans, or a typed failure. */
    fun interface BodyClient {
        suspend fun fetchDailyBody(): FetchResult<String>
    }

    override suspend fun fetch(date: LocalDate): FetchResult<Puzzle> =
        when (val result = fetchDaily(date)) {
            is FetchResult.Ok -> FetchResult.Ok(result.value.puzzle)
            is FetchResult.Err -> result
        }

    override suspend fun fetchDaily(date: LocalDate): FetchResult<QueensDailySource.Daily> {
        val body = when (val fetched = bodyClient.fetchDailyBody()) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = QueensGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no queensGamePuzzle in body"))
        val daily = when (val parsed = QueensWireParser.parse(match.queensGamePuzzleJson, date, match.editionText)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err ->
                return FetchResult.Err(FetchError.Unavailable("wire parse failed: ${parsed.error}"))
        }
        QueensUniquenessGate.check(daily.puzzle)?.let { gateError ->
            return FetchResult.Err(FetchError.Untrusted("A13 gate rejected daily: $gateError"))
        }
        val envelope = Puzzle(
            gameId = QueensDefinition.id,
            variant = queensVariantId(daily.puzzle.size),
            formatVersion = QueensCodec.VERSION,
            payload = QueensCodec.encode(daily.puzzle), // canonical bytes: storage + dedup (A19)
        )
        return FetchResult.Ok(QueensDailySource.Daily(envelope, daily.ref))
    }
}
