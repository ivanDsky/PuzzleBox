package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * Normalized record identity (ADR-0004; binding ticket-12 contract): `gameId +
 * day/puzzleNumber` encoded as one stable string key — `zip/2026-10-31/592`, or
 * `zip/2026-10-31` for a ref without a puzzle number. Identity never rides the codec
 * bytes (A19): the key is derived from the [PuzzleRef] the game's ScoreModel emits, and
 * decoded back through the authoring game's [com.ivandsky.puzzlebox.game.GamePlugin.refOf].
 */
object PuzzleRefKeys {

    /** Stable string form of the composite identity; [decode] is its only inverse. */
    fun encode(ref: PuzzleRef): String = keyed(ref.gameId.value, ref.date, ref.puzzleNumber)

    /** Inverse of [encode]; null for keys no game in [registry] authored (never reinterpreted). */
    fun decode(key: String, registry: GameRegistry): PuzzleRef? {
        val parts = key.split('/')
        val gameId = parts.getOrNull(0) ?: return null
        val date = parts.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val number = when (parts.size) {
            2 -> null
            3 -> parts[2].toIntOrNull() ?: return null
            else -> return null
        }
        return registry[GameId(gameId)]?.refOf?.invoke(date, number)
    }

    private fun keyed(gameId: String, date: LocalDate, puzzleNumber: Int?): String =
        buildString {
            append(gameId)
            append('/')
            append(date)
            puzzleNumber?.let { append('/').append(it) }
        }
}
