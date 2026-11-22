package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

private val Solved = RecapCard.Solved(
    total = 42,
    perGame = listOf(
        RecapCard.Solved.PerGame(RecapFixtures.zip, 18),
        RecapCard.Solved.PerGame(RecapFixtures.queens, 15),
        RecapCard.Solved.PerGame(RecapFixtures.sudoku, 9),
    ),
    hintFree = 36,
    longestHintFreeRun = 9,
)

private val SolvedImported = Solved.copy(importedDays = 312)

private val SecondSolved = RecapCard.Solved(total = 7, perGame = listOf(RecapCard.Solved.PerGame(RecapFixtures.zip, 7)))

internal val RecapFixtures.solved: RecapCard.Solved get() = Solved

/** A second page so a story has somewhere to go; the later tickets' cards stand here until they land. */
internal val RecapFixtures.secondSolved: RecapCard.Solved get() = SecondSolved

/** A year built from imported results alone: 312 puzzle days. */
internal val RecapFixtures.solvedImported: RecapCard.Solved get() = SolvedImported
