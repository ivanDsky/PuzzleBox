package com.ivandsky.puzzlebox.games.queens.feed

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The ADR-0002 LinkedInDaily adapter for Queens (Zip's LinkedInZipDailySource shape):
 * raw body -> [QueensGameExtractor] -> [QueensWireParser] -> the A13+A5 gate
 * ([QueensUniquenessGate]) -> canonical [QueensCodec] bytes in a [Puzzle] envelope under
 * `gameId=queens`, with the fetch-time identity
 * ([com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef]) core/data persists per ADR-0004.
 *
 * The body comes through the generic [FeedTransport] ([REQUEST]: stream GET, then the
 * GraphQL rung), so the extractor/gate/envelope path is JVM-testable without OkHttp.
 *
 * Error split per the FetchError contract: transport/parse failures are
 * [FetchError.Unavailable]; a payload the engine cannot trust is [FetchError.Untrusted] —
 * never playable (A13), no silent fallback.
 */
class LinkedInQueensDailySource(private val transport: FeedTransport) : DailySource {

    override val gameId: GameId = QueensDefinition.id

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.fetch(REQUEST)) {
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
        return FetchResult.Ok(DailyFetch(envelope, daily.ref))
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
    }
}
