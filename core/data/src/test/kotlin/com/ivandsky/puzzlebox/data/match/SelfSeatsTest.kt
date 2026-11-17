package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfSeatsTest {

    @Test
    fun `the seat strings and the no-deadline span are the shared ones`() {
        assertEquals("self-me", SelfSeats.ME)
        assertEquals("self-past", SelfSeats.PAST)
        assertEquals("Past you", SelfSeats.PAST_NAME)
        assertEquals(876_000, SelfSeats.NO_DEADLINE_HOURS)
    }

    @Test
    fun `a match challenged from my self seat is a self duel`() {
        assertTrue(match(challenger = SelfSeats.ME, opponent = SelfSeats.PAST).isSelf)
    }

    @Test
    fun `a friend match is not a self duel`() {
        assertFalse(match(challenger = "ann-uid", opponent = "bob-uid").isSelf)
        assertFalse(match(challenger = "ann-uid", opponent = null).isSelf)
        // The challenger decides: a stray self seat on the other side does not make a self duel.
        assertFalse(match(challenger = "ann-uid", opponent = SelfSeats.ME).isSelf)
        assertFalse(match(challenger = SelfSeats.PAST, opponent = "bob-uid").isSelf)
    }

    @Test
    fun `my seat in a self duel is the self seat signed in or out`() {
        val self = match(challenger = SelfSeats.ME, opponent = SelfSeats.PAST)

        assertEquals(SelfSeats.ME, self.mySeat("ann-uid"))
        assertEquals(SelfSeats.ME, self.mySeat(null))
    }

    @Test
    fun `my seat in a friend duel is the identity uid`() {
        val friend = match(challenger = "ann-uid", opponent = "bob-uid")

        assertEquals("bob-uid", friend.mySeat("bob-uid"))
        assertEquals("eve-uid", friend.mySeat("eve-uid"))
        assertNull(friend.mySeat(null))
    }

    @Test
    fun `a self duel deadline stays decades away`() {
        val self = match(challenger = SelfSeats.ME, opponent = SelfSeats.PAST).copy(deadlineHours = SelfSeats.NO_DEADLINE_HOURS)

        assertTrue(self.deadline.isAfter(self.createdAt.plusSeconds(90L * 365 * 24 * 3_600)))
    }

    @Test
    fun `a held code gives the self seat signed in or out`() = runBlocking {
        val self = heldSelf()

        assertEquals(SelfSeats.ME, self.seatFor(code, "ann-uid"))
        assertEquals(SelfSeats.ME, self.seatFor(code, null))
    }

    @Test
    fun `a code not held gives the identity uid and null stays null`() = runBlocking {
        val self = FakeSelfMatchRepository()

        assertEquals("ann-uid", self.seatFor(code, "ann-uid"))
        assertNull(self.seatFor(code, null))
    }

    private val code = requireNotNull(ShareCode.parse("ABC234"))

    private suspend fun heldSelf() = FakeSelfMatchRepository().also {
        it.save(match(challenger = SelfSeats.ME, opponent = SelfSeats.PAST), past = emptyList())
    }

    private fun match(challenger: String, opponent: String?) = Match(
        code = requireNotNull(ShareCode.parse("ABC234")),
        formatVersion = Match.FORMAT_VERSION,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        deadlineHours = 24,
        challenger = Player(challenger, "Ann", null),
        invitedUid = null,
        opponent = opponent?.let { Player(it, "Bob", null) },
        legs = listOf(MatchLeg(LegRef.Daily(GameId("alpha"), LocalDate.of(2026, 10, 1), 224), authorUid = null)),
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = emptyMap(),
    )
}
