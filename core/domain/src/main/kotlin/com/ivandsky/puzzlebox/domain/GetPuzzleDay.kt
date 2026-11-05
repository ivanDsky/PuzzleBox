package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameRegistry
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The app's current puzzle day (A9): the day the first registered game's
 * [Cadence.Daily] zone is on, so every app-level "today" rolls over with the puzzles
 * (LA midnight for Zip), not with the phone's clock. An empty registry is a wiring bug
 * (DiGraphTest enforces ≥1); America/Los_Angeles is the fallback, matching
 * [GetFeaturedGame]'s Zip fallback.
 */
class GetPuzzleDay(
    private val registry: GameRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {

    operator fun invoke(): LocalDate = LocalDate.now(clock.withZone(zone()))

    private fun zone(): ZoneId =
        when (val cadence = registry.all.firstOrNull()?.definition?.cadence) {
            is Cadence.Daily -> cadence.zone
            null -> FALLBACK_ZONE
        }

    private companion object {
        val FALLBACK_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
    }
}
