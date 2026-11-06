package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The puzzle day (A9) rolls over at the first registered game's cadence midnight — LA
 * for the daily games — whatever zone the phone is in. LA midnight on Nov 5 2026 is
 * 08:00Z (PST, UTC-8).
 */
class GetPuzzleDayTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private val nov4 = LocalDate.of(2026, 11, 4)
    private val nov5 = LocalDate.of(2026, 11, 5)

    private fun puzzleDay(registry: GameRegistry, at: String) =
        GetPuzzleDay(registry, Clock.fixed(Instant.parse(at), ZoneOffset.UTC))

    private val laRegistry = GameRegistry(listOf(zoned(alpha, la), zoned(beta, ZoneOffset.UTC)))

    @Test
    fun `evening in Kyiv is the same puzzle day`() {
        // 21:00 Kyiv (UTC+2) = 11:00 LA, Nov 4.
        assertEquals(nov4, puzzleDay(laRegistry, "2026-11-04T19:00:00Z")())
    }

    @Test
    fun `next morning in Kyiv is still yesterday's puzzle day`() {
        // 09:30 Kyiv on Nov 5 = 23:30 LA on Nov 4.
        assertEquals(nov4, puzzleDay(laRegistry, "2026-11-05T07:30:00Z")())
    }

    @Test
    fun `the day rolls over exactly at LA midnight`() {
        assertEquals(nov4, puzzleDay(laRegistry, "2026-11-05T07:59:59Z")())
        assertEquals(nov5, puzzleDay(laRegistry, "2026-11-05T08:00:00Z")())
    }

    @Test
    fun `the first registered game's zone wins`() {
        val utcFirst = GameRegistry(listOf(zoned(beta, ZoneOffset.UTC), zoned(alpha, la)))
        assertEquals(nov5, puzzleDay(utcFirst, "2026-11-05T07:30:00Z")())
    }

    @Test
    fun `an empty registry falls back to LA midnight`() {
        val empty = GameRegistry(emptyList())
        assertEquals(nov4, puzzleDay(empty, "2026-11-05T07:59:59Z")())
        assertEquals(nov5, puzzleDay(empty, "2026-11-05T08:00:00Z")())
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
            replaySession = base.replaySession,
            rendererModel = base.rendererModel,
        )
    }
}
