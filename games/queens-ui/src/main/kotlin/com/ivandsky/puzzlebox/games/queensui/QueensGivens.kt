package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec

/**
 * [puzzle] as a session nobody has played: its coloured regions. Where the queens go, and anything else the
 * payload carries past the board, is left behind here, before a session exists: nothing that draws or speaks the
 * session can give it away. Null for another game's puzzle and for a payload that does not decode.
 */
internal fun queensGivensSession(puzzle: Puzzle): QueensSession? {
    if (puzzle.gameId != QueensDefinition.id) return null
    val decoded = QueensCodec.decode(puzzle.payload).getOrNull() ?: return null
    return QueensSession(ref = null, puzzle = decoded.copy(solution = null, unknownTail = ByteArray(0)))
}
