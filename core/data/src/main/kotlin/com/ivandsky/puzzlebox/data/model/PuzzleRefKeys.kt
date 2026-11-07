package com.ivandsky.puzzlebox.data.model

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * Normalized record identity (ADR-0004; binding ticket-12 contract): `gameId + key`
 * encoded as one stable string key. A daily is `zip/2026-10-31/592`, or `zip/2026-10-31`
 * for a ref without a puzzle number. A custom puzzle is `custom/queens/K7Q2MX` (ADR-0018,
 * spec A11): it never starts with `"$gameId/"`, so per-game bests and daily boards never
 * see it. Identity never rides the codec bytes (A19): the key is derived from the
 * [PuzzleRef] the game's ScoreModel emits, and decoded back through the authoring game's
 * [com.ivandsky.puzzlebox.game.GamePlugin.refOf].
 */
object PuzzleRefKeys {

    /** Daily: "$gameId/$date[/$n]" (unchanged). Custom: "custom/$gameId/$puzzleId" (since P07). */
    fun encode(ref: PuzzleRef): String = when (val key = ref.key) {
        is PuzzleKey.Daily -> daily(ref.gameId.value, key.date, key.puzzleNumber)
        is PuzzleKey.Custom -> "${PuzzleKey.Custom.NAMESPACE}/${ref.gameId.value}/${key.puzzleId}"
    }

    /**
     * Inverse of [encode]; null for keys no game in [registry] authored (never reinterpreted).
     * A custom key goes through `registry[gameId].refOf(PuzzleKey.Custom(id))` (since P11).
     */
    fun decode(key: String, registry: GameRegistry): PuzzleRef? {
        val parts = key.split('/')
        val gameId = parts.getOrNull(0) ?: return null
        if (gameId == PuzzleKey.Custom.NAMESPACE) return custom(parts, registry)
        val date = parts.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val number = when (parts.size) {
            2 -> null
            3 -> parts[2].toIntOrNull() ?: return null
            else -> return null
        }
        return registry[GameId(gameId)]?.refOf?.invoke(PuzzleKey.Daily(date, number))
    }

    private fun custom(parts: List<String>, registry: GameRegistry): PuzzleRef? {
        if (parts.size != 3 || parts[1].isEmpty() || parts[2].isEmpty()) return null
        return registry[GameId(parts[1])]?.refOf?.invoke(PuzzleKey.Custom(parts[2]))
    }

    private fun daily(gameId: String, date: LocalDate, puzzleNumber: Int?): String =
        buildString {
            append(gameId)
            append('/')
            append(date)
            puzzleNumber?.let { append('/').append(it) }
        }
}
