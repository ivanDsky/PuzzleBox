package com.ivandsky.puzzlebox.game

/** Marker for a game's serializable in-progress state (F1 §2). */
interface GameSession

/**
 * Versioned persistence for [S] (A19): self-describing version byte, forward-compatible
 * reader, canonical encode — the same four rules [GameDefinition] codecs follow, applied
 * to sessions so a replay/archive save survives every future schema change.
 */
interface GameSessionCodec<S : GameSession> {
    fun serialize(session: S): ByteArray
    fun deserialize(bytes: ByteArray): DecodeResult<S>
}
