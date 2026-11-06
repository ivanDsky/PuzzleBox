package com.ivandsky.puzzlebox.domain

import java.time.LocalDate

/**
 * One day of a game's archive month ([ObserveArchiveMonth], spec A26/A27): the puzzle
 * [number], its [PastDayStatus], whether it was [archived] (opened after its own day, A21)
 * and [openedOn], the game-zone day its puzzle was first fetched (null = never opened).
 */
data class ArchiveDay(
    val date: LocalDate,
    val number: Int,
    val status: PastDayStatus,
    val archived: Boolean,
    val openedOn: LocalDate?,
)
