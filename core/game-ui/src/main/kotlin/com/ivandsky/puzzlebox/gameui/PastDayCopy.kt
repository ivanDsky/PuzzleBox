package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.PastDayStatus

/*
 * The words for one past day (spec A20), shared by History's day header and the hub's
 * past-puzzle picker headline: "<Game> #N", its status, and its one action.
 */

/** "Zip #592"; a day without a source number reads just the game's name. */
fun pastDayHeadline(gameName: String, number: Int?): String =
    if (number == null) gameName else "$gameName #$number"

/** "Solved 1:35", "In progress", "Saved on this device" or "Needs a connection the first time". */
fun pastDayStatusLine(status: PastDayStatus): String = when (status) {
    is PastDayStatus.Solved -> "Solved ${formatDuration(status.timeMs)}"
    is PastDayStatus.InProgress -> "In progress"
    PastDayStatus.Stored -> "Saved on this device"
    PastDayStatus.NeedsConnection -> "Needs a connection the first time"
}

/** The day's one action: "Play again" once solved, "Keep going" while in progress, else "Play". */
fun pastDayAction(status: PastDayStatus): String = when (status) {
    is PastDayStatus.Solved -> "Play again"
    is PastDayStatus.InProgress -> "Keep going"
    PastDayStatus.Stored, PastDayStatus.NeedsConnection -> "Play"
}
