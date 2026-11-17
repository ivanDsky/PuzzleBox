package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec

/**
 * [puzzle] as a session nobody has played: its walls and numbered dots. The path that solves it, and anything
 * else the payload carries past the board, is left behind here, before a session exists: nothing that draws or
 * speaks the session can give it away. Null for another game's puzzle and for a payload that does not decode.
 */
internal fun zipGivensSession(puzzle: Puzzle): ZipSession? {
    if (puzzle.gameId != ZipDefinition.id) return null
    val decoded = ZipCodec.decode(puzzle.payload).getOrNull() ?: return null
    return ZipSession(ref = null, puzzle = decoded.copy(solution = null, unknownTail = ByteArray(0)))
}
