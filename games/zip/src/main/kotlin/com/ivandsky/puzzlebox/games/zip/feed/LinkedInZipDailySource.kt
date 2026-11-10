package com.ivandsky.puzzlebox.games.zip.feed

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
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.LocalDate

/**
 * The ADR-0002 LinkedInDaily adapter (ticket 12): wire body -> [ZipWireParser] -> the
 * A16 oracle gate -> canonical [ZipCodec] bytes in a [Puzzle] envelope, with the
 * fetch-time identity ([com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef]) core/data persists
 * per ADR-0004. Today comes through the generic [FeedTransport] ([REQUEST]: stream GET, then
 * the page-shell scan); a past day comes by number from games-by-id ([EDITION], ADR-0017).
 *
 * Error split per the FetchError contract: transport/parse failures are [FetchError.Unavailable];
 * a payload the engine cannot trust, or a by-id body of another number, is
 * [FetchError.Untrusted] — never playable (A16, A6); a number LinkedIn has no puzzle for,
 * or one above today's, is [FetchError.NotFound].
 *
 * [clock] only bounds past numbers by today's (A4): LinkedIn serves tomorrow's puzzle too.
 */
class LinkedInZipDailySource(
    private val transport: FeedTransport,
    private val clock: Clock = Clock.systemUTC(),
) : DailySource {

    override val gameId: GameId = ZipDefinition.id

    override val archiveSince: LocalDate = EDITION.day1

    override val linkedInGameTypeId: Int? = EDITION.gameTypeId

    override fun puzzleNumber(day: LocalDate): Int? = EDITION.numberOrNull(day)

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.fetch(REQUEST)) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = TrailGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no trailGamePuzzle in body"))
        return trusted(match.trailGamePuzzleJson, date, match.editionText)
    }

    override suspend fun fetchPast(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = EDITION.fetch(transport, date, today())) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = TrailGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no trailGamePuzzle in by-id body"))
        // The edition is the checked request number; the body's own fields name today's (A6).
        return trusted(match.trailGamePuzzleJson, date, EDITION.number(date).toString())
    }

    /** Parse -> A16 oracle gate -> canonical envelope, shared by today's and past days' bodies. */
    private fun trusted(trailGamePuzzleJson: String, date: LocalDate, editionText: String?): FetchResult<DailyFetch> {
        val daily = when (val parsed = ZipWireParser.parse(trailGamePuzzleJson, date, editionText)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err -> return FetchResult.Err(FetchError.Unavailable("wire parse failed: ${parsed.error}"))
        }
        oracleGate(daily)?.let { return FetchResult.Err(it) }
        val envelope = Puzzle(
            gameId = ZipDefinition.id,
            variant = daily.puzzle.variant,
            formatVersion = ZipCodec.VERSION,
            payload = ZipCodec.encode(daily.puzzle), // canonical bytes: storage + dedup (A19)
        )
        return FetchResult.Ok(DailyFetch(envelope, daily.ref))
    }

    /**
     * A16 oracle (ZipDefinition recipe): the shipped solution must verify against the
     * rules AND agree with the exhaustive solver (exactly one solution, equal to shipped).
     */
    private fun oracleGate(daily: ZipWireParser.ZipDaily): FetchError? {
        val puzzle = daily.puzzle
        val shipped = puzzle.solution
            ?: return FetchError.Untrusted("daily ships no solution; A16 cannot validate")
        if (!ZipRules.verifyPath(puzzle, shipped)) {
            return FetchError.Untrusted("shipped solution violates zip rules")
        }
        val solver = ZipSolver()
        val count = solver.countSolutions(puzzle)
        if (count != 1) return FetchError.Untrusted("solver found $count solutions, need exactly 1")
        val found = solver.solve(puzzle)
        if (found != shipped) return FetchError.Untrusted("solver disagrees with shipped solution")
        return null
    }

    private fun today(): LocalDate = when (val cadence = ZipDefinition.cadence) {
        is Cadence.Daily -> LocalDate.now(clock.withZone(cadence.zone))
    }

    companion object {
        /** Zip's feed: the stream GET, then the page-shell scan (no GraphQL rung). */
        val REQUEST = FeedRequest(
            path = "games/zip/",
            objectName = "trailGamePuzzle",
            accepts = { TrailGameExtractor.extract(it) != null },
            scanPageShell = true,
        )

        /** Zip's by-number edition: LinkedIn gameTypeId 6, #1 on 2025-03-18 PT (A3). */
        val EDITION = LinkedInEdition(
            gameTypeId = 6,
            day1 = LocalDate.of(2025, 3, 18),
            path = REQUEST.path,
            objectName = REQUEST.objectName,
        )
    }
}
