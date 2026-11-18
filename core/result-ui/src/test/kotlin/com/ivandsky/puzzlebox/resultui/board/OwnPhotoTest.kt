package com.ivandsky.puzzlebox.resultui.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OwnPhotoTest {

    private fun row(player: String, self: Boolean = false, photo: String? = null) =
        DailyBoardRowModel(1, player, 60_000L, 0, isSelf = self, photo = photo, playerId = player)

    private val account = "https://example.com/me.png"

    @Test
    fun `only the player's own row takes the account photo, over a LinkedIn picture`() {
        assertEquals(account, row("me", self = true).withOwnPhoto(account).photo)
        assertEquals(account, row("me", self = true, photo = "/linkedin/me.jpg").withOwnPhoto(account).photo)
        assertNull(row("maya").withOwnPhoto(account).photo)
    }

    @Test
    fun `an account with no photo leaves every row as it is`() {
        val mine = row("me", self = true, photo = "/linkedin/me.jpg")
        assertSame(mine, mine.withOwnPhoto(null))
        assertEquals("/linkedin/me.jpg", mine.withOwnPhoto(null).photo)
    }

    @Test
    fun `a ranked board gives it to the row in the list, the pinned row and the lines`() {
        val state = DailyBoardState.Ranked(
            rows = listOf(row("maya"), row("me", self = true)),
            pinned = row("me", self = true),
            lines = listOf(DailyBoardLine.Real(row("maya"), 1), DailyBoardLine.Real(row("me", self = true), 2), DailyBoardLine.Ghost(GhostRowModel("g", "Gus", 70_000L, false, null), 3)),
        )

        val shown = state.withOwnPhoto(account) as DailyBoardState.Ranked

        assertEquals(listOf(null, account), shown.rows.map { it.photo })
        assertEquals(account, shown.pinned?.photo)
        assertEquals(listOf(null, account), shown.lines!!.filterIsInstance<DailyBoardLine.Real>().map { it.row.photo })
    }

    @Test
    fun `a board that is not a list is unchanged`() {
        assertEquals(DailyBoardState.Loading, DailyBoardState.Loading.withOwnPhoto(account))
        assertEquals(DailyBoardState.SignedOut, DailyBoardState.SignedOut.withOwnPhoto(account))
    }
}
