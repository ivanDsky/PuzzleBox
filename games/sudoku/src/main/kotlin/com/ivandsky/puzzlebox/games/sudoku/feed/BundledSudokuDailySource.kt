package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/** The 9x9 game has no live feed (ADR-0015): every date serves [SudokuSamples.classic]. */
class BundledSudokuDailySource : DailySource {

    override val gameId: GameId = SudokuGames.CLASSIC.id

    override suspend fun fetchDaily(date: LocalDate): FetchResult<DailyFetch> {
        val puzzle = SudokuSamples.classic
        val envelope = Puzzle(
            gameId = gameId,
            variant = puzzle.variant.id,
            formatVersion = SudokuCodec.VERSION,
            payload = SudokuCodec.encode(puzzle),
        )
        return FetchResult.Ok(DailyFetch(envelope, SudokuPuzzleRef(gameId, PuzzleKey.Daily(date, null))))
    }
}
