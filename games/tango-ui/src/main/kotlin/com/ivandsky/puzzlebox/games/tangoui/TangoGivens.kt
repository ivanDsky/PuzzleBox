package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec

/**
 * [puzzle] as a session nobody has played: its given suns and moons and its marks. A Tango puzzle carries no
 * solution; what its payload carries past the board is left behind here all the same, before a session exists.
 * Null for another game's puzzle and for a payload that does not decode.
 */
internal fun tangoGivensSession(puzzle: Puzzle): TangoSession? {
    if (puzzle.gameId != TangoDefinition.id) return null
    val decoded = TangoCodec.decode(puzzle.payload).getOrNull() ?: return null
    return TangoSession(ref = null, puzzle = decoded.copy(unknownTail = ByteArray(0)))
}
