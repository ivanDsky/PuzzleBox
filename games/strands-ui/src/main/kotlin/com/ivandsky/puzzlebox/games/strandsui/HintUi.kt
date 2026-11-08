package com.ivandsky.puzzlebox.games.strandsui

/** The hint control: the hints banked to spend ([count]) and the dots filled toward the next ([meterThirds] of 3). */
data class HintUi(val count: Int, val meterThirds: Int) {
    val available: Boolean get() = count > 0
}
