package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.nextthing.NextThing
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L4 and ADR-0031 §15: the nearest friend is offered for a challenge only when their tier allows one. */
class ChallengeFriendNextThingProviderTest {

    private val tierReads = mutableListOf<String>()
    private val tiers = mutableMapOf<String, VisibilityTier>()

    private fun facts(vararg times: Pair<String, Long>, viewerUid: String? = "me") = nextThingFacts(
        viewerUid = viewerUid,
        friendBoard = { friendBoardOf(*times) },
        friendTier = { uid ->
            tierReads += uid
            tiers[uid]
        },
    )

    @Test
    fun `the friend just above is offered when their tier allows a challenge`() = runTest {
        tiers["ann"] = VisibilityTier.Duel

        val offer = ChallengeFriendNextThingProvider.offer(facts("ann" to 30_000, "me" to 40_000, "bea" to 50_000))

        assertEquals(NextThing.ChallengeFriend("ann", "Ann"), offer)
    }

    @Test
    fun `with nobody above the friend just below is offered`() = runTest {
        tiers["bea"] = VisibilityTier.Replays

        val offer = ChallengeFriendNextThingProvider.offer(facts("me" to 40_000, "bea" to 50_000))

        assertEquals(NextThing.ChallengeFriend("bea", "Bea"), offer)
    }

    @Test
    fun `a friend above whose tier forbids a challenge is not swapped for the friend below`() = runTest {
        tiers["ann"] = VisibilityTier.Board
        tiers["bea"] = VisibilityTier.Duel

        assertNull(ChallengeFriendNextThingProvider.offer(facts("ann" to 30_000, "me" to 40_000, "bea" to 50_000)))
    }

    @Test
    fun `a failed tier read offers nothing`() = runTest {
        assertNull(ChallengeFriendNextThingProvider.offer(facts("ann" to 30_000, "me" to 40_000)))
    }

    @Test
    fun `only the picked friend's tier is read, once`() = runTest {
        tiers["ann"] = VisibilityTier.Duel

        ChallengeFriendNextThingProvider.offer(facts("cy" to 20_000, "ann" to 30_000, "me" to 40_000, "bea" to 50_000))

        assertEquals(listOf("ann"), tierReads)
    }

    @Test
    fun `a player alone on the board is offered nobody and no tier is read`() = runTest {
        assertNull(ChallengeFriendNextThingProvider.offer(facts("me" to 40_000)))
        assertEquals(emptyList<String>(), tierReads)
    }

    @Test
    fun `a signed-out player is offered nobody`() = runTest {
        tiers["ann"] = VisibilityTier.Duel

        assertNull(ChallengeFriendNextThingProvider.offer(facts("ann" to 30_000, "bea" to 50_000, viewerUid = null)))
    }

    @Test
    fun `a board that cannot be read offers nothing`() = runTest {
        assertNull(ChallengeFriendNextThingProvider.offer(nextThingFacts(friendBoard = { null }, friendTier = { VisibilityTier.Duel })))
    }
}
