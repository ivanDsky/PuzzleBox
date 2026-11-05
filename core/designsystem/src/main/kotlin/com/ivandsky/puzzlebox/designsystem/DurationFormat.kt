package com.ivandsky.puzzlebox.designsystem

import java.util.Locale

/**
 * The one time format for every surface: m:ss ("0:58", "12:05"), h:mm:ss from one hour
 * ("1:02:03"). Sub-second time floors; negatives clamp to "0:00". Locale-stable.
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
