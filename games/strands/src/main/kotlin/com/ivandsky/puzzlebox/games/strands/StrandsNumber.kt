package com.ivandsky.puzzlebox.games.strands

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The puzzle number is derived from the date (A10): the feed's `id` is not monotonic with
 * the date (research 02), so it never names a puzzle.
 */
object StrandsNumber {

    /** The first Strands day, #1. */
    val EPOCH: LocalDate = LocalDate.of(2024, 3, 4)

    fun of(date: LocalDate): Int = ChronoUnit.DAYS.between(EPOCH, date).toInt() + 1
}
