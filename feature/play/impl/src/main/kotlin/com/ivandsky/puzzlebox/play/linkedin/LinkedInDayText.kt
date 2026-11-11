package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.readableInk
import com.ivandsky.puzzlebox.gameui.GameCardColors

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

/** [n] as an English ordinal: 1st, 2nd, 3rd, 4th, but 11th, 12th, 13th, then 21st, 101st, 111th. */
internal fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/**
 * How stale a board read at [updatedAtMs] is at [nowMs]. A clock that ran backwards reads
 * "just now" rather than a negative age.
 */
internal fun updatedText(nowMs: Long, updatedAtMs: Long): String {
    val minutes = (nowMs - updatedAtMs).coerceAtLeast(0L) / MS_PER_MINUTE
    val age = when {
        minutes < 1 -> "just now"
        minutes < MINUTES_PER_HOUR -> "$minutes min ago"
        minutes < MINUTES_PER_HOUR * HOURS_PER_DAY -> "${minutes / MINUTES_PER_HOUR} h ago"
        else -> "${minutes / (MINUTES_PER_HOUR * HOURS_PER_DAY)} d ago"
    }
    return "Updated $age"
}

/**
 * The ink of every LinkedIn line on the day card: the card's own content colour where it reads on the fill, else dark
 * ink. Full strength, so the small "Updated" line is as readable as the rank line; the rest of the card keeps its paint.
 */
internal fun linkedInInk(colors: GameCardColors): Color = readableInk(colors.fill, colors.fg)
