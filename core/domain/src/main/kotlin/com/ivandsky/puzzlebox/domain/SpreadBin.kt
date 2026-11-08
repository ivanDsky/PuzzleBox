package com.ivandsky.puzzlebox.domain

/** One histogram bin: how many solves fall in it and its bar height in 0..1 (0 when empty). */
data class SpreadBin(val count: Int, val height: Float)
