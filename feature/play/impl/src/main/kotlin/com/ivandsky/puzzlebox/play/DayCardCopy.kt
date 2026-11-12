package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.pastDayAction
import com.ivandsky.puzzlebox.gameui.pastDayStatusLine

// The words of the day card's lines. The status line comes in two parts so a day switch moves only the part that
// differs: "Solved" stays put and its time morphs.

/** The status slot's value: today's status (null while loading) or a past day's. */
internal sealed interface DayLine {
    data class Today(val status: TodayStatus?) : DayLine
    data class Past(val status: PastDayStatus) : DayLine
}

/**
 * The status line's words and, apart from them, the time that follows ("Solved" and " 1:35"). Both are null while
 * today loads; the time is null when the state has none.
 */
internal fun statusParts(line: DayLine): Pair<String?, String?> {
    val (full, time) = when (line) {
        is DayLine.Today -> when (val status = line.status) {
            null -> return null to null
            is TodayStatus.Solved -> dayStatusLine(status) to formatDuration(status.timeMs)
            is TodayStatus.InProgress -> dayStatusLine(status) to formatDuration(status.elapsedMs)
            TodayStatus.NotStarted, TodayStatus.Offline -> dayStatusLine(status) to null
        }
        is DayLine.Past -> when (val status = line.status) {
            is PastDayStatus.Solved -> pastDayStatusLine(status) to formatDuration(status.timeMs)
            is PastDayStatus.InProgress -> pastLine(status) to formatDuration(status.elapsedMs)
            PastDayStatus.Stored, PastDayStatus.NeedsConnection -> pastDayStatusLine(status) to null
        }
    }
    return if (time == null) full to null else full.removeSuffix(time).trimEnd() to " $time"
}

/** Which mark the status line draws: today's and a past day's solved check are the same mark, so they are one layer. */
internal fun markKind(line: DayLine): String = when (line) {
    is DayLine.Today -> when (line.status) {
        null -> "loading"
        TodayStatus.NotStarted -> "not_started"
        is TodayStatus.InProgress -> "in_progress"
        is TodayStatus.Solved -> "solved"
        TodayStatus.Offline -> "offline"
    }
    is DayLine.Past -> when (line.status) {
        is PastDayStatus.Solved -> "solved"
        is PastDayStatus.InProgress -> "in_progress"
        PastDayStatus.Stored -> "saved"
        PastDayStatus.NeedsConnection -> "needs_connection"
    }
}

/** Today's status on the day card: the time follows the state's words. */
internal fun dayStatusLine(status: TodayStatus): String = when (status) {
    is TodayStatus.Solved -> "Solved ${formatDuration(status.timeMs)}"
    is TodayStatus.InProgress -> "In progress · ${formatDuration(status.elapsedMs)}"
    TodayStatus.NotStarted -> "Today's puzzle"
    TodayStatus.Offline -> "Can't load it · check your connection"
}

/** A past day's status; in progress adds its saved clock, as today's line does. */
private fun pastLine(status: PastDayStatus): String = when (status) {
    is PastDayStatus.InProgress -> "In progress · ${formatDuration(status.elapsedMs)}"
    else -> pastDayStatusLine(status)
}

/** What the day means for the streak: today counts; an archive day never does. */
internal fun note(past: ArchiveDay?): String = when {
    past == null -> "Counts toward your streak"
    past.archived -> past.openedOn?.let { "Opened ${longDate(it)} · doesn't count toward your streak" }
        ?: "Doesn't count toward your streak"
    past.status is PastDayStatus.Solved -> "Solved on the day"
    else -> "Counts toward your best, never your streak"
}

internal fun actionLabel(line: DayLine): String = when (line) {
    is DayLine.Past -> pastDayAction(line.status)
    is DayLine.Today -> when (line.status) {
        null, TodayStatus.NotStarted, TodayStatus.Offline -> "Play"
        is TodayStatus.InProgress -> "Keep going"
        is TodayStatus.Solved -> "See result"
    }
}
