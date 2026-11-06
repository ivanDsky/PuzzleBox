package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * One game's today status in plain words (A10, A13), the one copy Home's Today card and
 * the games hub both show: the clock reads m:ss (0:42, 12:05).
 */
fun todayStatusLine(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Not started"
    is TodayStatus.InProgress -> "Keep going · ${formatDuration(status.elapsedMs)}"
    is TodayStatus.Solved -> "Solved · ${formatDuration(status.timeMs)}"
    TodayStatus.Offline -> "Offline"
}
