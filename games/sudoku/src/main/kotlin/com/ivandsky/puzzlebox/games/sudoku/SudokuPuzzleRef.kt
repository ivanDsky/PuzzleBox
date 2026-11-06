package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * Daily identity (ADR-0004). [gameId] is stored because one engine serves two games;
 * [puzzleNumber] is the LinkedIn edition, null for the bundled board.
 */
data class SudokuPuzzleRef(
    override val gameId: GameId,
    override val date: LocalDate,
    override val puzzleNumber: Int?,
) : PuzzleRef
