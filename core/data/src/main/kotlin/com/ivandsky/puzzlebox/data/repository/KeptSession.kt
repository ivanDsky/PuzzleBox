package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * One game's kept session (spec A3): the SessionBlob [envelope] of a paused play, the game's
 * cadence [day] it belongs to, and the displayed [elapsedMs] at the last change. Equality is
 * by content, envelope bytes included.
 */
class KeptSession(
    val game: GameId,
    val day: LocalDate,
    val elapsedMs: Long,
    val envelope: ByteArray,
) {

    override fun equals(other: Any?): Boolean =
        other is KeptSession &&
            game == other.game &&
            day == other.day &&
            elapsedMs == other.elapsedMs &&
            envelope.contentEquals(other.envelope)

    override fun hashCode(): Int {
        var result = game.hashCode()
        result = 31 * result + day.hashCode()
        result = 31 * result + elapsedMs.hashCode()
        result = 31 * result + envelope.contentHashCode()
        return result
    }

    override fun toString(): String =
        "KeptSession(game=$game, day=$day, elapsedMs=$elapsedMs, envelope=${envelope.size} bytes)"
}
