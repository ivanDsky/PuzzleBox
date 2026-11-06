package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * Puzzle identity (ADR-0004, ADR-0018): [gameId] is stored because one engine serves two
 * games; [key] is a day's daily (puzzle number = the LinkedIn edition, null for the bundled
 * board) or a friend's puzzle by share code.
 */
data class SudokuPuzzleRef(override val gameId: GameId, override val key: PuzzleKey) : PuzzleRef {
    constructor(gameId: GameId, date: LocalDate, puzzleNumber: Int?) :
        this(gameId, PuzzleKey.Daily(date, puzzleNumber)) // deleted in P12
}
