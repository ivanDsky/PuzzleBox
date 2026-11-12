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
 *
 * Version 2 carries the in-progress move log next to the session bytes:
 *
 * ```
 * 0   u8    envelopeVersion = 2
 * 1   u8    gameIdLen; gameIdLen x ASCII (the GameId)
 *     u32   sessionLen, big-endian; sessionLen x the game's session codec bytes
 *     ...   log bytes to the end (at least 1)
 * ```
 */
object SessionBlob {

    private const val VERSION: Int = 1
    private const val VERSION_WITH_LOG: Int = 2

    /** [log] is null for version 1, the move-log bytes for version 2. */
    data class Decoded(val game: GameId, val session: ByteArray, val log: ByteArray? = null)

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

    /** Accepts versions 1 and 2. Null when the bytes are no envelope this format authored — never guessed (A19). */
    fun decode(bytes: ByteArray): Decoded? {
        if (bytes.size < 3) return null
        val version = bytes[0].toInt()
        if (version != VERSION && version != VERSION_WITH_LOG) return null
        val nameLen = bytes[1].toInt() and 0xFF
        if (nameLen == 0 || 2 + nameLen > bytes.size) return null
        val name = bytes.decodeToString(2, 2 + nameLen)
        // ASCII gate: the envelope only ever carried ASCII game ids; anything else is
        // not a name this format authored.
        if (name.isEmpty() || name.any { it.code > 0x7F }) return null
        val bodyStart = 2 + nameLen
        if (version == VERSION) return Decoded(GameId(name), bytes.copyOfRange(bodyStart, bytes.size))

        val sessionStart = bodyStart + Int.SIZE_BYTES
        if (sessionStart > bytes.size) return null
        val sessionLen = readU32(bytes, bodyStart)
        // Compare in Long: a length past Int.MAX_VALUE must read as truncated, not wrap.
        val logStart = sessionStart + sessionLen
        if (logStart >= bytes.size) return null // session overruns, or the log part is empty
        return Decoded(
            GameId(name),
            bytes.copyOfRange(sessionStart, logStart.toInt()),
            bytes.copyOfRange(logStart.toInt(), bytes.size),
        )
    }

    private fun readU32(bytes: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 0 until Int.SIZE_BYTES) v = (v shl 8) or (bytes[at + i].toLong() and 0xFF)
        return v
    }
}
