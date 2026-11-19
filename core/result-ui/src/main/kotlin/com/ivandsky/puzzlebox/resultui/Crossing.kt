package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.designsystem.formatDuration
import kotlin.math.abs

/** Where a clock stands against past you's time: [over] once the whole seconds reach it, and [clock] the whole seconds left or over, as `m:ss`. */
data class Crossing(val over: Boolean, val clock: String)

/** The one crossing rule: the bar, its fill and the tablet time card flip on the same whole second, so none of them keeps its own. */
fun crossing(elapsedMs: Long, targetMs: Long): Crossing {
    val shownSecond = elapsedMs / 1000L
    val targetSecond = targetMs / 1000L
    return Crossing(over = shownSecond >= targetSecond, clock = formatDuration(abs(targetSecond - shownSecond) * 1000L))
}
