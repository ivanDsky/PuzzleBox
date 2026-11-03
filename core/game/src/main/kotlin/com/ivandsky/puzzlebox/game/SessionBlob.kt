package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId

/**
 * Self-describing transport envelope for a game session carried across a boundary that
 * does not know the game (ADR-0005: a persisted artifact names its own game). The
 * history route packs [encode]d blobs; the play route [decode]s them and hands the
 * inner bytes to that game's [GameSessionCodec].
 *
 * ```
 * 0   u8    envelopeVersion = 1
 * 1   u8    gameIdLen; gameIdLen x ASCII (the GameId)
 *     ...   the game's session codec bytes, verbatim
 * ```
 */
object SessionBlob {

    private const val VERSION: Int = 1

    data class Decoded(val game: GameId, val session: ByteArray)

    fun encode(game: GameId, session: ByteArray): ByteArray {
        val name = game.value
        require(name.isNotEmpty() && name.length <= 0xFF) { "gameId must be 1..255 ASCII chars" }
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        return ByteArray(2 + nameBytes.size + session.size).also { out ->
            out[0] = VERSION.toByte()
            out[1] = nameBytes.size.toByte()
            nameBytes.copyInto(out, destinationOffset = 2)
            session.copyInto(out, destinationOffset = 2 + nameBytes.size)
        }
    }

    /** Null when the bytes are no envelope this version authored — never guessed (A19). */
    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.size < 3) return null
        if (bytes[0].toInt() != VERSION) return null
        val nameLen = bytes[1].toInt() and 0xFF
        if (nameLen == 0 || 2 + nameLen > bytes.size) return null
        val name = bytes.decodeToString(2, 2 + nameLen)
        // ASCII gate: the envelope only ever carried ASCII game ids; anything else is
        // not a name this format authored.
        if (name.isEmpty() || name.any { it.code > 0x7F }) return null
        return Decoded(GameId(name), bytes.copyOfRange(2 + nameLen, bytes.size))
    }
}
