package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.ShareCode

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/**
 * One New today row: [game] and its [brand], the puzzle's [title] and [author], the [difficulty] level when the
 * puzzle has one, and [age] ("5 min ago"), read when the list loaded.
 */
internal data class CommunityRowModel(
    val code: ShareCode,
    val game: String,
    val brand: GameBrand,
    val title: String,
    val author: String,
    val difficulty: Difficulty.Level?,
    val age: String,
)

/** The row for [doc], aged as of [nowMs]; [game] and [brand] come from the registries, which this file does not see. */
internal fun SharedPuzzleDoc.toRow(game: String, brand: GameBrand, nowMs: Long) = CommunityRowModel(
    code = code,
    game = game,
    brand = brand,
    title = title,
    author = authorName,
    difficulty = difficulty,
    age = ageLabel(createdAtMs, nowMs),
)

/** "just now", "5 min ago", "2 h ago" or "1 d ago"; a clock that reads before [createdAtMs] is "just now". */
internal fun ageLabel(createdAtMs: Long, nowMs: Long): String {
    val age = (nowMs - createdAtMs).coerceAtLeast(0L)
    return when {
        age < MINUTE_MS -> "just now"
        age < HOUR_MS -> "${age / MINUTE_MS} min ago"
        age < DAY_MS -> "${age / HOUR_MS} h ago"
        else -> "${age / DAY_MS} d ago"
    }
}
