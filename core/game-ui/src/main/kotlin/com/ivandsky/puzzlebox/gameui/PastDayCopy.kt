package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.PastDayStatus

/*
 * The words for one past day (spec A20) on the game page's day card: its status and its
 * one action.
 */

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
