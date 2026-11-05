package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.GameRegistry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * When the next puzzles land: the next midnight in the zone [GetPuzzleDay] reads (the
 * first registered game's cadence zone, LA for the daily games), so Home's and Result's
 * countdowns end exactly when the puzzle day rolls over.
 */
class GetNextRollover(
    private val registry: GameRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {

    operator fun invoke(): Instant {
        val zone = registry.puzzleZone()
        return LocalDate.now(clock.withZone(zone)).plusDays(1).atStartOfDay(zone).toInstant()
    }
}
