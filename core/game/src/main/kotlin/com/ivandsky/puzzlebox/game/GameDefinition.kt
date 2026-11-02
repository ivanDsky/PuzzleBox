package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId

/**
 * Identity + wire format of one game (F1 §2). Symmetric from day one: editors (ADR-0003)
 * and the archive (ADR-0002) depend on decode/encode being inverses over [Puzzle] envelopes.
 *
 * Implementations return [DecodeResult] — they never throw for stored bytes; [encode] is
 * the one fail-fast seam for caller bugs: a foreign envelope, or a caller-authored
 * own-game payload the game cannot decode (ZipDefinition.encode throws
 * [IllegalArgumentException] for both today).
 */
interface GameDefinition {
    val id: GameId
    val displayName: String
    val cadence: Cadence

    fun decode(bytes: ByteArray): DecodeResult<Puzzle>

    /** Canonical encoding: exactly one byte sequence per puzzle (archive dedup, editor diff). */
    fun encode(puzzle: Puzzle): ByteArray
}
