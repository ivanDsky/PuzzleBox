package com.ivandsky.puzzlebox.data.linkedin

/** One connection's row on a day's LinkedIn board. */
data class LinkedInGhost(
    val memberRef: String,
    val name: String,
    val timeMs: Long?,
    val hintFree: Boolean,
    val flawless: Boolean,
)
