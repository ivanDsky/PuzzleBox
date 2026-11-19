package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.FriendStatus
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** L3: the registered providers form the ladder; each rung is reached only when the ones above it have nothing. */
class NextThingProvidersTest {

    private val chain = NextThingChain(failures = FailureLog { _, _, cause -> throw cause })
    private val code = ShareCode.parse("K7Q2MX")!!
    private val community = NextThing.CommunityPuzzle(ShareCode.parse("P4STED")!!, beta, "Noor")

    /** Every rung has something to offer. */
    private val all = nextThingFacts(
        todaySet = listOf(
            TodayEntry(alpha, "Alpha", TodayStatus.Solved(40_000)),
            TodayEntry(beta, "Beta", TodayStatus.NotStarted),
        ),
        friendBoard = { friendBoardOf("ann" to 30_000, "me" to 40_000) },
        friendTier = { VisibilityTier.Duel },
        history = listOf(GameHistoryDay(alpha, TODAY.minusDays(2), puzzleNumber = 300, record = null)),
        friendPuzzles = listOf(FriendPuzzleRow(code, alpha, "Knots", "Maya", FriendStatus.New)),
    )
    private val setDone = all.copy(todaySet = listOf(TodayEntry(alpha, "Alpha", TodayStatus.Solved(40_000)), TodayEntry(beta, "Beta", TodayStatus.Solved(50_000))))
    private val noFriend = setDone.copy(friendBoard = { null })
    private val noMissedDay = noFriend.copy(history = emptyList())
    private val noFriendPuzzle = noMissedDay.copy(friendPuzzles = emptyList(), communityPuzzles = { listOf(community) })
    private val nothingLeft = noFriendPuzzle.copy(communityPuzzles = { emptyList() })

    @Test
    fun `an unsolved daily is offered before anything else`() = runTest {
        assertEquals(NextThing.NextDaily(beta), chain.offer(all))
    }

    @Test
    fun `with today's set done the friend to challenge is offered`() = runTest {
        assertEquals(NextThing.ChallengeFriend("ann", "Ann"), chain.offer(setDone))
    }

    @Test
    fun `with no friend to challenge a missed day is offered`() = runTest {
        assertEquals(NextThing.MissedDay(alpha, TODAY.minusDays(2)), chain.offer(noFriend))
    }

    @Test
    fun `with no missed day a new friend puzzle is offered`() = runTest {
        assertEquals(NextThing.FriendPuzzle(code, alpha, "Maya"), chain.offer(noMissedDay))
    }

    @Test
    fun `with no friend puzzle a community puzzle is offered`() = runTest {
        assertEquals(community, chain.offer(noFriendPuzzle))
    }

    @Test
    fun `a new friend puzzle is offered before a community one`() = runTest {
        assertEquals(NextThing.FriendPuzzle(code, alpha, "Maya"), chain.offer(noMissedDay.copy(communityPuzzles = { listOf(community) })))
    }

    @Test
    fun `with nothing left to play the rollover countdown is offered`() = runTest {
        assertEquals(NextThing.DoneForToday(ROLLOVER), chain.offer(nothingLeft))
    }
}
