package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.LinkedInEdition
import com.ivandsky.puzzlebox.game.PersistedQuery
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.model.GameId
import java.time.Clock
import java.time.LocalDate

/**
 * LinkedIn's daily Mini Sudoku: raw body -> [MiniSudokuGameExtractor] -> [MiniSudokuWireParser]
 * -> [SudokuUniquenessGate] -> canonical [SudokuCodec] bytes in a [Puzzle] envelope under
 * `gameId=minisudoku`, with the fetch-time [com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef].
 * Today comes by [REQUEST]; a past day comes by number from games-by-id ([EDITION], ADR-0017).
 *
 * A body without the puzzle, or a puzzle that fails parsing, is [FetchError.Unavailable].
 * A parsed puzzle whose givens do not solve uniquely to the shipped solution, or a by-id body
 * of another number, is [FetchError.Untrusted] and never playable (A6). A number LinkedIn has
 * no puzzle for, or one outside #1..today's, is [FetchError.NotFound]. Transport errors pass
 * through unchanged.
 *
 * [clock] only bounds past numbers by today's (A4): LinkedIn serves tomorrow's puzzle too.
 */
class LinkedInMiniSudokuDailySource(
    private val transport: FeedTransport,
    private val clock: Clock = Clock.systemUTC(),
) : DailySource {

    override val gameId: GameId = SudokuGames.MINI.id

    override val archiveSince: LocalDate = EDITION.day1

    override fun puzzleNumber(day: LocalDate): Int? = if (day.isBefore(archiveSince)) null else EDITION.number(day)

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.fetch(REQUEST)) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = MiniSudokuGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no miniSudokuGamePuzzle in body"))
        return trusted(match.miniSudokuGamePuzzleJson, date, match.editionText)
    }

    override suspend fun fetchPast(date: LocalDate): FetchResult<DailyFetch> {
        val number = EDITION.number(date)
        val todays = EDITION.number(today())
        if (number !in 1..todays) {
            return FetchResult.Err(FetchError.NotFound("Mini Sudoku #$number is outside #1..#$todays"))
        }
        val body = when (val fetched = transport.fetch(EDITION.request(number))) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        EDITION.check(body, number)?.let { return FetchResult.Err(it) }
        val match = MiniSudokuGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no miniSudokuGamePuzzle in by-id body"))
        // The edition is the checked request number; the body's puzzleId names today's (A6).
        return trusted(match.miniSudokuGamePuzzleJson, date, number.toString())
    }

    /** Parse -> uniqueness gate -> canonical envelope, shared by today's and past days' bodies. */
    private fun trusted(puzzleJson: String, date: LocalDate, editionText: String?): FetchResult<DailyFetch> {
        val variant = SudokuGames.MINI.variant
        val daily = when (val parsed = MiniSudokuWireParser.parse(puzzleJson, date, editionText, variant)) {
            is DecodeResult.Ok -> parsed.value
            is DecodeResult.Err -> return FetchResult.Err(FetchError.Unavailable("wire parse failed: ${parsed.error}"))
        }
        if (!SudokuUniquenessGate.isTrusted(daily.puzzle)) {
            return FetchResult.Err(FetchError.Untrusted("givens do not solve uniquely to the shipped solution"))
        }
        val envelope = Puzzle(
            gameId = SudokuGames.MINI.id,
            variant = variant.id,
            formatVersion = SudokuCodec.VERSION,
            payload = SudokuCodec.encode(daily.puzzle),
        )
        return FetchResult.Ok(DailyFetch(envelope, daily.ref))
    }

    private fun today(): LocalDate = when (val cadence = SudokuGames.MINI.cadence) {
        is Cadence.Daily -> LocalDate.now(clock.withZone(cadence.zone))
    }

    companion object {
        /** LinkedIn's persisted "today's game" query, verified live for gameTypeId 7 (research 01). */
        const val QUERY_ID: String = "voyagerIdentityDashGames.22685dd4999129d6bed79f33e65dcbc5"

        val REQUEST: FeedRequest = FeedRequest(
            path = "games/mini-sudoku/",
            objectName = "miniSudokuGamePuzzle",
            accepts = { MiniSudokuGameExtractor.extract(it) != null },
            persistedQuery = PersistedQuery(queryId = QUERY_ID, variables = "(gameTypeId:7)"),
        )

        /** Mini Sudoku's by-number edition: LinkedIn gameTypeId 7, #1 on 2025-08-12 PT (A3). */
        val EDITION: LinkedInEdition = LinkedInEdition(
            gameTypeId = 7,
            day1 = LocalDate.of(2025, 8, 12),
            path = REQUEST.path,
            objectName = REQUEST.objectName,
        )
    }
}
