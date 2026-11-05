package com.ivandsky.puzzlebox.games.strandsui

/** The hint control: whether a hint can be spent, and the meter's thirds toward the next one. */
data class HintUi(val available: Boolean, val meterThirds: Int)
