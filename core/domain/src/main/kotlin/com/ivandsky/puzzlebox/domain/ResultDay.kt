package com.ivandsky.puzzlebox.domain

import java.time.LocalDate

/**
 * One dot of the Results week: a puzzle day, whether any game was solved on it, and whether it is today.
 * A [frozen] day was held by a streak freeze; it is never [solved].
 */
data class ResultDay(val date: LocalDate, val solved: Boolean, val today: Boolean, val frozen: Boolean = false)
