package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The next puzzles land at the next midnight in the puzzle-day zone (A9) — LA for the
 * daily games. LA midnight on Nov 5 2026 is 08:00Z (PST, UTC-8).
 */
class GetNextRolloverTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private val laRegistry = GameRegistry(listOf(zoned(alpha, la)))

    private fun nextRollover(registry: GameRegistry, at: String) =
        GetNextRollover(registry, Clock.fixed(Instant.parse(at), ZoneOffset.UTC))()

    @Test
    fun `a second before LA midnight the rollover is a second away`() {
        assertEquals(Instant.parse("2026-11-05T08:00:00Z"), nextRollover(laRegistry, "2026-11-05T07:59:59Z"))
    }

    @Test
    fun `at LA midnight the next rollover is a day away`() {
        assertEquals(Instant.parse("2026-11-06T08:00:00Z"), nextRollover(laRegistry, "2026-11-05T08:00:00Z"))
    }

    @Test
    fun `the rollover follows the game's zone, not UTC midnight`() {
        // 23:00Z on Nov 5 is 15:00 in LA: UTC's day has nearly ended, LA's has not.
        assertEquals(Instant.parse("2026-11-06T08:00:00Z"), nextRollover(laRegistry, "2026-11-05T23:00:00Z"))
    }

    @Test
    fun `an empty registry falls back to LA midnight`() {
        val empty = GameRegistry(emptyList())
        assertEquals(Instant.parse("2026-11-05T08:00:00Z"), nextRollover(empty, "2026-11-05T07:59:59Z"))
    }

    /** [game]'s test plugin with its cadence moved to [zone]. */
    private fun zoned(game: GameId, zone: ZoneId): GamePlugin<*, *, *> {
        val base = TestGames.plugin(game)
        val definition = object : GameDefinition by base.definition {
            override val cadence: Cadence = Cadence.Daily(zone)
        }
        return GamePlugin(
            definition = definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
        )
    }
}
