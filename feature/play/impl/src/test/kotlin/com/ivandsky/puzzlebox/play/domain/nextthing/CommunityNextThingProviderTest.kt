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

/** L3, C2: the newest community puzzle the player has not opened; the list arrives newest first, minus own and hidden. */
class CommunityNextThingProviderTest {

    private fun puzzle(code: String, author: String) = NextThing.CommunityPuzzle(checkNotNull(ShareCode.parse(code)), alpha, author)

    private val newest = puzzle("AAAA22", "Noor")
    private val older = puzzle("BBBB33", "Omar")
    private val oldest = puzzle("CCCC44", "Pia")

    private fun received(of: NextThing.CommunityPuzzle, status: FriendStatus) =
        FriendPuzzleRow(of.code, beta, "Knots", of.authorName, status)

    @Test
    fun `the newest puzzle on the list is offered`() = runTest {
        val facts = nextThingFacts(communityPuzzles = { listOf(newest, older, oldest) })

        assertEquals(newest, CommunityNextThingProvider.offer(facts))
    }

    @Test
    fun `a puzzle the player already opened is skipped, whatever became of it`() = runTest {
        val facts = nextThingFacts(
            friendPuzzles = listOf(received(newest, FriendStatus.New), received(older, FriendStatus.Solved(30_000))),
            communityPuzzles = { listOf(newest, older, oldest) },
        )

        assertEquals(oldest, CommunityNextThingProvider.offer(facts))
    }

    @Test
    fun `when every puzzle on the list was opened nothing is offered`() = runTest {
        val facts = nextThingFacts(
            friendPuzzles = listOf(received(newest, FriendStatus.InProgress(5_000)), received(older, FriendStatus.TakenDown)),
            communityPuzzles = { listOf(newest, older) },
        )

        assertNull(CommunityNextThingProvider.offer(facts))
    }

    @Test
    fun `an empty list offers nothing`() = runTest {
        assertNull(CommunityNextThingProvider.offer(nextThingFacts()))
    }

    @Test
    fun `the community rung sits between the friend puzzle rung and done for today`() {
        val orders = NextThingProviders.sortedBy { it.order }

        val at = orders.indexOf(CommunityNextThingProvider)
        assertEquals(NewFriendPuzzleNextThingProvider, orders[at - 1])
        assertEquals(DoneForTodayNextThingProvider, orders[at + 1])
    }
}
