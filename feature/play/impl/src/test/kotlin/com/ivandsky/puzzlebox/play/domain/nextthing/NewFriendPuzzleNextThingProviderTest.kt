package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.FriendStatus
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L3: only a received puzzle in the New state is a new friend puzzle. */
class NewFriendPuzzleNextThingProviderTest {

    private fun row(code: String, author: String, status: FriendStatus) =
        FriendPuzzleRow(ShareCode.parse(code)!!, beta, "Knots", author, status)

    @Test
    fun `the first new puzzle is offered, past the played ones`() = runTest {
        val facts = nextThingFacts(
            friendPuzzles = listOf(
                row("K7Q2MX", "Maya", FriendStatus.Solved(30_000)),
                row("P4STED", "Noor", FriendStatus.New),
                row("AB12CD", "Omar", FriendStatus.New),
            ),
        )

        assertEquals(NextThing.FriendPuzzle(ShareCode.parse("P4STED")!!, beta, "Noor"), NewFriendPuzzleNextThingProvider.offer(facts))
    }

    @Test
    fun `puzzles in any other state are not offered`() = runTest {
        val facts = nextThingFacts(
            friendPuzzles = listOf(
                row("K7Q2MX", "Maya", FriendStatus.Solved(30_000)),
                row("P4STED", "Noor", FriendStatus.InProgress(12_000)),
                row("AB12CD", "Omar", FriendStatus.TakenDown),
                row("EF34GH", "Pia", FriendStatus.NeedsUpdate),
            ),
        )

        assertNull(NewFriendPuzzleNextThingProvider.offer(facts))
    }
}
