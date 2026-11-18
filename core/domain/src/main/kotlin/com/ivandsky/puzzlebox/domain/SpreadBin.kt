package com.ivandsky.puzzlebox.domain

/**
 * One histogram bin: the solves from [fromMs] up to (not including) [toMs], and its bar height
 * in 0..1 (0 when empty, an empty bin stays on the axis but draws no bar). An [open] bin is the
 * slow tail: it holds every solve from [fromMs] on, [toMs] only sets its drawn width.
 */
data class SpreadBin(val count: Int, val height: Float, val fromMs: Long, val toMs: Long, val open: Boolean = false)
