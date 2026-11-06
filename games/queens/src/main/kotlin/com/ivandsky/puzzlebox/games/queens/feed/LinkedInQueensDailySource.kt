package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.LinkedInEdition
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.LocalDate

/**
 * The ADR-0002 LinkedInDaily adapter for Queens (Zip's LinkedInZipDailySource shape):
 * raw body -> [QueensGameExtractor] -> [QueensWireParser] -> the A13+A5 gate
 * ([QueensUniquenessGate]) -> canonical [QueensCodec] bytes in a [Puzzle] envelope under
 * `gameId=queens`, with the fetch-time identity
 * ([com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef]) core/data persists per ADR-0004.
 *
 * The body comes through the generic [FeedTransport]: today's via [REQUEST] (stream GET,
 * then the GraphQL rung), a past day's by number from games-by-id ([EDITION], ADR-0017),
 * so the extractor/gate/envelope path is JVM-testable without OkHttp.
 *
 * Error split per the FetchError contract: transport/parse failures are
 * [FetchError.Unavailable]; a payload the engine cannot trust, or a by-id body of another
 * number, is [FetchError.Untrusted] — never playable (A13, A6), no silent fallback; a number
 * LinkedIn has no puzzle for, or one above today's, is [FetchError.NotFound].
 *
 * [clock] only bounds past numbers by today's (A4): LinkedIn serves tomorrow's puzzle too.
 */
class LinkedInQueensDailySource(
    private val transport: FeedTransport,
    private val clock: Clock = Clock.systemUTC(),
) : DailySource {

    override val gameId: GameId = QueensDefinition.id

    override val archiveSince: LocalDate = EDITION.day1

    override fun puzzleNumber(day: LocalDate): Int? = EDITION.numberOrNull(day)

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.fetch(REQUEST)) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = QueensGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no queensGamePuzzle in body"))
        return trusted(match.queensGamePuzzleJson, date, match.editionText)
    }

    override suspend fun fetchPast(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = EDITION.fetch(transport, date, today())) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = QueensGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no queensGamePuzzle in by-id body"))
        // The edition is the checked request number; the body's own fields name today's (A6).
        return trusted(match.queensGamePuzzleJson, date, EDITION.number(date).toString())
    }

    /** Parse -> A13 gate -> canonical envelope, shared by today's and past days' bodies. */
    private fun trusted(queensGamePuzzleJson: String, date: LocalDate, editionText: String?): FetchResult<DailyFetch> {
        val daily = when (val parsed = QueensWireParser.parse(queensGamePuzzleJson, date, editionText)) {
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
        return FetchResult.Ok(DailyFetch(envelope, daily.ref))
    }

    private fun today(): LocalDate = when (val cadence = QueensDefinition.cadence) {
        is Cadence.Daily -> LocalDate.now(clock.withZone(cadence.zone))
    }

    companion object {
        // gameTypeId 3 = Queens (research 02; linkedin-games-scraper GAME_TYPE_IDS).
        private const val GAME_TYPE_ID = 3

        // Documented minimal GraphQL request (research 02). The exact production queryId
        // document is queens-09's real-capture job; the extractor accepts ANY body that
        // carries the queensGamePuzzle object.
        const val GRAPHQL_QUERY: String =
            """{"query":"query QueensDailyGame { voyagerIdentityDashGames(gameTypeId: $GAME_TYPE_ID) { included { gamePuzzle { queensGamePuzzle { gridSize colorGrid { colors } solution { row col } } } } } }","variables":{"gameTypeId":$GAME_TYPE_ID}}"""

        /** Queens' feed: the stream GET, then the GraphQL rung (session seams; no page scan). */
        val REQUEST = FeedRequest(
            path = "games/queens/",
            objectName = "queensGamePuzzle",
            accepts = { QueensGameExtractor.extract(it) != null },
            graphqlBody = GRAPHQL_QUERY,
        )

        /** Queens' by-number edition: LinkedIn gameTypeId 3, #1 on 2024-05-01 PT (A3). */
        val EDITION = LinkedInEdition(
            gameTypeId = GAME_TYPE_ID,
            day1 = LocalDate.of(2024, 5, 1),
            path = REQUEST.path,
            objectName = REQUEST.objectName,
        )
    }
}
