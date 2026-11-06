package com.ivandsky.puzzlebox.domain

import java.time.LocalDate

/**
 * A game's archive header and stats numbers ([ObserveArchiveProgress], spec A26/A27):
 * [range] is #1 .. today, [total] the puzzles in it, [solved] the game's solved days
 * (archived included, always [GameStats.solved]) and [archivedSolves] those played from
 * the archive.
 */
data class ArchiveProgress(
    val range: ClosedRange<LocalDate>,
    val total: Int,
    val solved: Int,
    val archivedSolves: Int,
)
