package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The footer's one line for a lineup. */
class LineupSummaryTest {

    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val names = mapOf(ZIP to "Zip", TANGO to "Tango", QUEENS to "Queens")
    private fun nameOf(id: GameId) = names.getValue(id)

    private fun summary(entries: List<com.ivandsky.puzzlebox.domain.lineup.LineupEntry>, hours: Int = 32, friends: Boolean = false) =
        lineupSummary(entries, hours, friends, now, ZoneOffset.UTC, Locale.US, ::nameOf)

    @Test
    fun `it counts the puzzles and lists the games in lineup order, repeats included`() {
        assertEquals(
            "Best of 4 · Zip, Queens, Queens, Tango · ends Tue 18:00",
            summary(listOf(ZIP, QUEENS, QUEENS, TANGO).map(::AnyGame)),
        )
    }

    @Test
    fun `an exact puzzle reads by its title in quotes`() {
        val exact = ExactPuzzle(LegRef.Daily(QUEENS, LocalDate.of(2026, 11, 1), 3), null, "Crown jewels", null, PuzzleSource.PastDay)

        assertEquals("Best of 2 · Zip, \"Crown jewels\" · ends Tue 18:00", summary(listOf(AnyGame(ZIP), exact)))
    }

    @Test
    fun `friends in adds the same clause as the other presets`() {
        assertEquals("Best of 1 · Tango · ends Wed 10:00 · incl. friends' puzzles", summary(listOf(AnyGame(TANGO)), hours = 48, friends = true))
    }
}
