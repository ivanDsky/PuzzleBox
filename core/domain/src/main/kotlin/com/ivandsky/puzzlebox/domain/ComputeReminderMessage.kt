package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.ReminderMessage

/**
 * Today's reminder, or null to skip it. Skipped when any puzzle of today's set is solved (the player already played) or
 * when none is playable (every puzzle failed to load). The body lists the playable games, [TodayStatus.NotStarted] or
 * [TodayStatus.InProgress], in the given registry order; an [TodayStatus.Offline] game is left out.
 */
class ComputeReminderMessage {

    operator fun invoke(entries: List<TodayEntry>): ReminderMessage? {
        if (entries.any { it.status is TodayStatus.Solved }) return null
        val playable = entries.filter { it.status is TodayStatus.NotStarted || it.status is TodayStatus.InProgress }
        if (playable.isEmpty()) return null
        return ReminderMessage(
            title = "Today's puzzles are waiting",
            body = "Open today: " + playable.joinToString(", ") { it.displayName } + ".",
        )
    }
}
