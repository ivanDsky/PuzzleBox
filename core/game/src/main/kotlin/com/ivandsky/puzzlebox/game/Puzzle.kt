package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.GameId

/**
 * Game-agnostic puzzle envelope (finding F2-2): identity, rules pin, and an opaque,
 * versioned payload. Core never opens [payload] — the owning game's plugin exposes the
 * typed view. [formatVersion] is the payload's self-describing schema version (A19), so
 * archive and editors can parse any envelope ever stored (ADR-0002/0003).
 */
class Puzzle(
    val gameId: GameId,
    val variant: VariantId,
    val formatVersion: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is Puzzle &&
            gameId == other.gameId &&
            variant == other.variant &&
            formatVersion == other.formatVersion &&
            payload.contentEquals(other.payload)

    override fun hashCode(): Int {
        var h = gameId.hashCode()
        h = 31 * h + variant.hashCode()
        h = 31 * h + formatVersion
        h = 31 * h + payload.contentHashCode()
        return h
    }

    override fun toString(): String =
        "Puzzle(gameId=$gameId, variant=$variant, formatVersion=$formatVersion, ${payload.size} bytes)"
}
