package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultPostState.Late
import com.ivandsky.puzzlebox.data.match.ResultPostState.LocalOnly
import com.ivandsky.puzzlebox.data.match.ResultPostState.Pending
import com.ivandsky.puzzlebox.data.match.ResultPostState.Posted
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reveal-ready rule over hand-built two-leg matches; Ann challenges, Bob holds the seat, the viewer is Ann. */
class RevealReadyTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)

    private val proto = match()

    private fun legs(count: Int) = List(count) { index ->
        val game = if (index % 2 == 0) TestGames.alpha else TestGames.beta
        MatchLeg(LegRef.Daily(game, LocalDate.of(2026, 11, 1 + index), index + 1), authorUid = null)
    }

    private fun result(uid: String, leg: Int, timeMs: Long = 60_000) = LegResult(uid, leg, timeMs, 0, 1_000, false)

    private fun match(
        opponent: Player? = bob,
        cancelled: Boolean = false,
        declined: Boolean = false,
        bobsProgress: Set<Int> = emptySet(),
    ) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = Instant.EPOCH,
        deadlineHours = 24,
        challenger = ann,
        invitedUid = bob.uid,
        opponent = opponent,
        legs = legs(2),
        includeFriends = false,
        cancelled = cancelled,
        declined = declined,
        rematchOf = null,
        progress = mapOf(bob.uid to bobsProgress),
    )

    private fun row(leg: Int, state: ResultPostState) = OutboxResult(code, result(ann.uid, leg), state, attempts = 1)

    @Test
    fun revealIsReadyWhenMyRowsAreLateOrLocalOnlyAndTheOpponentFinished() {
        val match = match(bobsProgress = setOf(0, 1))
        assertTrue(match.revealReady(ann.uid, listOf(row(0, Late), row(1, LocalOnly)), Instant.EPOCH))
    }

    @Test
    fun revealIsNotReadyWhileMyPostIsPendingEvenIfTheOpponentIsAhead() {
        val match = match(bobsProgress = setOf(0, 1))
        assertFalse(match.revealReady(ann.uid, listOf(row(0, Posted), row(1, Pending)), Instant.EPOCH))
    }

    @Test
    fun revealIsReadyOnceTheMatchIsClosed() {
        assertTrue(match().revealReady(ann.uid, emptyList(), proto.closesAt))
    }

    @Test
    fun revealIsNeverReadyForACancelledDeclinedOrUnclaimedMatch() {
        val done = listOf(row(0, Posted), row(1, Posted))
        for (match in listOf(match(cancelled = true), match(declined = true), match(opponent = null))) {
            val both = match.copy(progress = mapOf(bob.uid to setOf(0, 1)))
            assertFalse(both.revealReady(ann.uid, done, both.closesAt))
            assertFalse(both.revealReady(ann.uid, done, Instant.EPOCH))
        }
    }

    @Test
    fun revealIsReadyWhenMyProgressNamesThePuzzlesIPlayedOnAnotherDevice() {
        val match = match(bobsProgress = setOf(0, 1)).let { it.copy(progress = it.progress + (ann.uid to setOf(1))) }
        assertTrue(match.revealReady(ann.uid, listOf(row(0, Posted)), Instant.EPOCH))
        assertFalse(match.revealReady(ann.uid, emptyList(), Instant.EPOCH))
    }

    @Test
    fun aPendingRowOnThisDeviceStaysNotDoneEvenWhenMyProgressNamesThatPuzzle() {
        val match = match(bobsProgress = setOf(0, 1)).let { it.copy(progress = it.progress + (ann.uid to setOf(0, 1))) }
        assertFalse(match.revealReady(ann.uid, listOf(row(0, Posted), row(1, Pending)), Instant.EPOCH))
    }
}
