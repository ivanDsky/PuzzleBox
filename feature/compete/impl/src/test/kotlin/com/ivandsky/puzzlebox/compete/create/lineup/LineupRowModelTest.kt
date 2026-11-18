package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The words on a lineup row, per kind of entry. */
class LineupRowModelTest {

    @Test
    fun `an Any row names its game and says what it can draw`() {
        assertEquals(
            LineupRowModel("Any Queens", "A past daily neither of you has played"),
            AnyGame(QUEENS).rowModel("Queens", includeFriends = false),
        )
        assertEquals(
            LineupRowModel("Any Queens", "A past daily or friend's puzzle neither of you has played"),
            AnyGame(QUEENS).rowModel("Queens", includeFriends = true),
        )
    }

    @Test
    fun `a friend's exact row names the friend once, not as author and as source`() {
        val code = requireNotNull(ShareCode.parse("K7Q2MX"))
        val exact = ExactPuzzle(LegRef.Custom(ZIP, code), "bo", "Crown jewels", "Bo", PuzzleSource.Friend)

        assertEquals(LineupRowModel("Crown jewels", "Zip · From Bo"), exact.rowModel("Zip", includeFriends = false))
    }

    @Test
    fun `a past daily exact row shows its title over its game and Past day`() {
        val exact = ExactPuzzle(LegRef.Daily(ZIP, LocalDate.of(2026, 11, 1), 12), null, "Zip #12", null, PuzzleSource.PastDay)

        assertEquals(LineupRowModel("Zip #12", "Zip · Past day"), exact.rowModel("Zip", includeFriends = true))
    }

    @Test
    fun `a community exact row shows its author and its source`() {
        val exact = ExactPuzzle(LegRef.Daily(ZIP, LocalDate.of(2026, 11, 1), 12), null, "Moon", "Ana", PuzzleSource.Community)

        assertEquals(LineupRowModel("Moon", "Zip · by Ana · Community"), exact.rowModel("Zip", includeFriends = false))
    }

    @Test
    fun `for Past you an Any row says it draws from what you've solved, friends' puzzles when they are in`() {
        assertEquals(
            LineupRowModel("Any Queens", "A daily you've solved"),
            AnyGame(QUEENS).rowModel("Queens", includeFriends = false, pastYou = true),
        )
        assertEquals(
            LineupRowModel("Any Queens", "A daily or friend's puzzle you've solved"),
            AnyGame(QUEENS).rowModel("Queens", includeFriends = true, pastYou = true),
        )
    }
}
