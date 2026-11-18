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
    fun `a custom exact row shows its title over game, author and code`() {
        val code = requireNotNull(ShareCode.parse("K7Q2MX"))
        val exact = ExactPuzzle(LegRef.Custom(ZIP, code), "bo", "Crown jewels", "Bo", PuzzleSource.Friend)

        assertEquals(LineupRowModel("Crown jewels", "Zip · by Bo · K7Q2MX"), exact.rowModel("Zip", includeFriends = false))
    }

    @Test
    fun `a past daily exact row shows its title over its game, with no code`() {
        val exact = ExactPuzzle(LegRef.Daily(ZIP, LocalDate.of(2026, 11, 1), 12), null, "Zip #12", null, PuzzleSource.PastDay)

        assertEquals(LineupRowModel("Zip #12", "Zip"), exact.rowModel("Zip", includeFriends = true))
    }
}
