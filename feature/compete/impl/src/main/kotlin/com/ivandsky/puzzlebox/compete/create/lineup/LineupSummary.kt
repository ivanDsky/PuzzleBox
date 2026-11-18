package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.duel.formatEnds
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private const val SECONDS_PER_HOUR = 3_600L

/**
 * The footer for a lineup: "Best of 4 · Zip, Queens, "Crown jewels", Tango · ends Wed 18:00", the puzzles in order (an
 * exact one by its title in quotes), plus " · incl. friends' puzzles" when friends are in.
 */
internal fun lineupSummary(
    entries: List<LineupEntry>,
    deadlineHours: Int,
    includeFriends: Boolean,
    now: Instant,
    zone: ZoneId,
    locale: Locale,
    nameOf: (GameId) -> String,
): String {
    val puzzles = entries.joinToString(", ") { entry ->
        when (entry) {
            is AnyGame -> nameOf(entry.gameId)
            is ExactPuzzle -> "\"${entry.title}\""
        }
    }
    val ends = formatEnds(now.plusSeconds(deadlineHours * SECONDS_PER_HOUR), zone, locale)
    return "Best of ${entries.size} · $puzzles · ends $ends" + if (includeFriends) " · incl. friends' puzzles" else ""
}
