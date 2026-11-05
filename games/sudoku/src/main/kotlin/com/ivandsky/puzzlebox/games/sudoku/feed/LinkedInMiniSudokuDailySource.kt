package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.PersistedQuery
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * LinkedIn's daily Mini Sudoku: raw body -> [MiniSudokuGameExtractor] -> [MiniSudokuWireParser]
 * -> [SudokuUniquenessGate] -> canonical [SudokuCodec] bytes in a [Puzzle] envelope under
 * `gameId=minisudoku`, with the fetch-time [com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef].
 *
 * A body without the puzzle, or a puzzle that fails parsing, is [FetchError.Unavailable].
 * A parsed puzzle whose givens do not solve uniquely to the shipped solution is
 * [FetchError.Untrusted] and never playable. Transport errors pass through unchanged.
 */
class LinkedInMiniSudokuDailySource(private val transport: FeedTransport) : DailySource {

    override val gameId: GameId = SudokuGames.MINI.id

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val body = when (val fetched = transport.fetch(REQUEST)) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return FetchResult.Err(fetched.error)
        }
        val match = MiniSudokuGameExtractor.extract(body)
            ?: return FetchResult.Err(FetchError.Unavailable("no miniSudokuGamePuzzle in body"))
        val variant = SudokuGames.MINI.variant
        val daily = when (val parsed = MiniSudokuWireParser.parse(match.miniSudokuGamePuzzleJson, date, match.editionText, variant)) {
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

    companion object {
        /** LinkedIn's persisted "today's game" query, verified live for gameTypeId 7 (research 01). */
        const val QUERY_ID: String = "voyagerIdentityDashGames.22685dd4999129d6bed79f33e65dcbc5"

        val REQUEST: FeedRequest = FeedRequest(
            path = "games/mini-sudoku/",
            objectName = "miniSudokuGamePuzzle",
            accepts = { MiniSudokuGameExtractor.extract(it) != null },
            persistedQuery = PersistedQuery(queryId = QUERY_ID, variables = "(gameTypeId:7)"),
        )
    }
}
