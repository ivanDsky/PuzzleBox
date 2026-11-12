package com.ivandsky.puzzlebox.play.linkedin

import com.ivandsky.puzzlebox.domain.linkedin.SavedBoardState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the LinkedIn line of a day says, per [SavedBoardState] (spec A36). A saved board names the time it was read
 * as a fixed [Saved.asOf], never a relative age, and whether it is [Saved.final]; a day that can no longer be read
 * says so and how to keep the next one. A day still inside the days LinkedIn serves has no line: the sync chip and
 * its read are the whole of that state.
 */
internal sealed interface DayLine {

    /** A board was read and kept: [asOf] is the read time as the line shows it, [final] whether the day was over. */
    data class Saved(val asOf: String, val final: Boolean) : DayLine {
        /** The mark beside the time. */
        val mark: String get() = if (final) FINAL else PARTIAL

        /** The whole line for a screen reader. */
        val description: String get() = "Saved board as of $asOf, ${mark.lowercase(Locale.US)}"
    }

    /** No board was kept for the day and none can be read any more. */
    data object NeverSaved : DayLine

    companion object {
        const val FINAL = "Final"
        const val PARTIAL = "Partial"
        const val NEVER_SAVED = "Not saved. Open a day within 7 days to keep its board."
    }
}

// The copy is English-only, so the time follows it rather than the device locale; only the zone is the device's.
private val AsOf = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

/** The line this state draws in [zone] (the device's), or null while a read can still make a board. */
internal fun SavedBoardState.toDayLine(zone: ZoneId): DayLine? = when (this) {
    is SavedBoardState.Saved -> DayLine.Saved(AsOf.format(Instant.ofEpochMilli(asOfMs).atZone(zone)), final)
    SavedBoardState.NeverSaved -> DayLine.NeverSaved
    SavedBoardState.NotYetRead -> null
}
