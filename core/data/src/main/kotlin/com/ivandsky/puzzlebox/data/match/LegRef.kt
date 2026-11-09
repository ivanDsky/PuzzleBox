package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** One puzzle of a duel. [wire] uses the `PuzzleRefKeys` strings, so a leg and its completion row agree. */
sealed interface LegRef {
    val gameId: GameId

    data class Daily(override val gameId: GameId, val day: LocalDate, val number: Int) : LegRef
    data class Custom(override val gameId: GameId, val code: ShareCode) : LegRef

    /** Same strings as PuzzleRefKeys: "zip/2026-10-01/224", "custom/zip/K7Q2MX". */
    val wire: String
        get() = when (this) {
            is Daily -> "${gameId.value}/$day/$number"
            is Custom -> "${PuzzleKey.Custom.NAMESPACE}/${gameId.value}/${code.value}"
        }

    companion object {
        /** Registry-free inverse of [wire]; null for anything unreadable, including an unnumbered daily. */
        fun parse(wire: String): LegRef? {
            val parts = wire.split('/')
            if (parts.size != 3 || parts[0].isEmpty() || parts[1].isEmpty()) return null
            return if (parts[0] == PuzzleKey.Custom.NAMESPACE) custom(parts) else daily(parts)
        }

        private fun custom(parts: List<String>): LegRef? {
            val code = ShareCode.parse(parts[2])?.takeIf { it.value == parts[2] } ?: return null
            return Custom(GameId(parts[1]), code)
        }

        private fun daily(parts: List<String>): LegRef? {
            val day = try {
                LocalDate.parse(parts[1])
            } catch (_: DateTimeParseException) {
                return null
            }
            val number = parts[2].toIntOrNull()?.takeIf { it >= 1 } ?: return null
            return Daily(GameId(parts[0]), day, number)
        }
    }
}
