package com.ivandsky.puzzlebox.testing.contract

import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.DAILY_MATCH_CAP
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behaviour every [MatchRepository] adapter must pass: the Firestore rules for duels, seen through the port. This base
 * holds the hooks, the fixtures and the match-document cases (create, claim, cancel, decline, observeMine);
 * [MatchResultsContract] adds the result cases, and an adapter's test subclasses that. Subclasses supply a fresh, empty,
 * online [repository]. Test names are camel case because this class ships in a main source set that gets dexed.
 */
abstract class MatchRepositoryContract {

    protected abstract val repository: MatchRepository

    protected abstract fun signInAs(uid: String)
    protected abstract fun signOut()
    protected abstract fun goOffline()

    /** The server clock: the `createdAt` of the next create and the time every rule compares against. */
    protected abstract fun setNow(now: Instant)

    /** Registers [code] as a published puzzle; puzzle and match codes share one namespace. */
    protected abstract fun occupyPuzzleCode(code: ShareCode)

    @Before
    fun startTheClock() = setNow(START)

    protected fun code(value: String = CODE): ShareCode = checkNotNull(ShareCode.parse(value))

    protected fun draft(code: ShareCode = code(), challenger: Player = ANN, invitedUid: String? = null, hours: Int = 24) =
        MatchDraft(code, hours, challenger, invitedUid, LEGS, includeFriends = true, rematchOf = null)

    /** [draft] as the server stores it; cancelled, declined and progress via `copy`. */
    protected fun match(draft: MatchDraft = draft(), createdAt: Instant = START, opponent: Player? = null) = Match(
        draft.code, Match.FORMAT_VERSION, createdAt, draft.deadlineHours, draft.challenger, draft.invitedUid, opponent,
        draft.legs, draft.includeFriends, cancelled = false, declined = false, draft.rematchOf, progress = emptyMap(),
    )

    protected fun result(uid: String, legIndex: Int = 0, completedAt: Instant = START.plusSeconds(90), timeMs: Long = 80_000) =
        LegResult(uid, legIndex, timeMs, hints = 1, completedAtMs = completedAt.toEpochMilli(), gaveUp = false)

    /** Signs in as [player] and makes one call. */
    protected suspend fun <T> by(player: Player, call: suspend MatchRepository.() -> T): T {
        signInAs(player.uid)
        return repository.call()
    }

    /** [draft]'s challenger creates it and stays signed in. */
    protected suspend fun created(draft: MatchDraft = draft()) =
        assertEquals(CreateResult.Created(draft.code), by(draft.challenger) { create(draft) })

    /** An open match by [ANN] that [BOB] has claimed; [BOB] stays signed in. */
    protected suspend fun claimed() {
        created()
        assertEquals(ClaimResult.Claimed, by(BOB) { claim(code(), BOB) })
    }

    protected suspend fun fetch(code: ShareCode = code()): MatchFetch = repository.observe(code).first()

    protected suspend fun assertMatch(expected: Match) = assertEquals(MatchFetch.Found(expected), fetch(expected.code))

    @Test
    fun createThenObserveReturnsTheMatch() = runTest {
        created()

        assertMatch(match())
        assertEquals(true, repository.puzzleCodeTaken(code()))
    }

    @Test
    fun createTwiceByTheSameChallengerIsCreated() = runTest {
        created()
        setNow(START.plusSeconds(60))

        assertEquals(CreateResult.Created(code()), repository.create(draft(hours = 48)))
        assertMatch(match())
    }

    @Test
    fun createWithAnotherChallengersCodeIsCodeTaken() = runTest {
        created()

        assertEquals(CreateResult.CodeTaken, by(BOB) { create(draft(challenger = BOB)) })
        assertMatch(match())
    }

    @Test
    fun createWithAPublishedPuzzleCodeIsCodeTaken() = runTest {
        occupyPuzzleCode(code())

        assertEquals(true, by(ANN) { puzzleCodeTaken(code()) })
        assertEquals(CreateResult.CodeTaken, repository.create(draft()))
        assertEquals(MatchFetch.NotFound, fetch())
    }

    @Test
    fun twentyFirstCreateOfTheDayIsDailyCapReached() = runTest {
        val codes = CAP_SUFFIXES.take(DAILY_MATCH_CAP + 1).map { code("K7Q2M$it") }
        codes.dropLast(1).forEach { created(draft(code = it)) }

        assertEquals(CreateResult.DailyCapReached, repository.create(draft(code = codes.last())))
        assertEquals(MatchFetch.NotFound, fetch(codes.last()))
        assertEquals(false, repository.puzzleCodeTaken(codes.last()))
    }

    @Test
    fun createSignedOutIsSignedOut() = runTest {
        signOut()

        assertEquals(CreateResult.SignedOut, repository.create(draft()))
        assertEquals(MatchFetch.NotFound, fetch())
    }

    @Test
    fun createOfflineIsOffline() = runTest {
        signInAs(ANN.uid)
        goOffline()

        assertEquals(CreateResult.Offline, repository.create(draft()))
        assertEquals(MatchFetch.Offline, fetch())
    }

    @Test
    fun claimSetsTheOpponentOnce() = runTest {
        claimed()

        assertMatch(match(opponent = BOB))
    }

    @Test
    fun secondClaimIsTaken() = runTest {
        claimed()

        assertEquals(ClaimResult.Taken, by(CAT) { claim(code(), CAT) })
        assertMatch(match(opponent = BOB))
    }

    @Test
    fun ownRetryOfClaimIsClaimed() = runTest {
        claimed()

        assertEquals(ClaimResult.Claimed, repository.claim(code(), BOB))
        assertMatch(match(opponent = BOB))
    }

    @Test
    fun challengerCannotClaimOwnMatch() = runTest {
        created()

        assertTrue(repository.claim(code(), ANN) is ClaimResult.Rejected)
        assertMatch(match())
    }

    @Test
    fun claimByAnotherThanTheInvitedIsRejected() = runTest {
        val invited = draft(invitedUid = CAT.uid).also { created(it) }

        assertTrue(by(BOB) { claim(code(), BOB) } is ClaimResult.Rejected)
        assertMatch(match(invited))
        assertEquals(ClaimResult.Claimed, by(CAT) { claim(code(), CAT) })
        assertMatch(match(invited, opponent = CAT))
    }

    @Test
    fun claimOfCancelledOrDeclinedIsCancelled() = runTest {
        created()
        repository.cancel(code())
        val invited = draft(code = code(OTHER_CODE), invitedUid = CAT.uid).also { created(it) }
        by(CAT) { decline(invited.code) }

        assertEquals(ClaimResult.Cancelled, repository.claim(invited.code, CAT))
        assertEquals(ClaimResult.Cancelled, by(BOB) { claim(code(), BOB) })
        assertMatch(match().copy(cancelled = true))
        assertMatch(match(invited).copy(declined = true))
    }

    @Test
    fun claimAfterDeadlineIsClosed() = runTest {
        created()
        setNow(match().deadline)

        assertEquals(ClaimResult.Closed, by(BOB) { claim(code(), BOB) })
        assertMatch(match())
    }

    @Test
    fun cancelWhileUnclaimedIsDone() = runTest {
        created()

        assertEquals(WriteResult.Done, repository.cancel(code()))
        assertEquals(WriteResult.Done, repository.cancel(code()))
        assertMatch(match().copy(cancelled = true))
    }

    @Test
    fun cancelAfterClaimIsRejected() = runTest {
        claimed()

        assertTrue(by(ANN) { cancel(code()) } is WriteResult.Rejected)
        assertMatch(match(opponent = BOB))
    }

    @Test
    fun cancelByTheOpponentIsRejected() = runTest {
        val invited = draft(invitedUid = BOB.uid).also { created(it) }

        assertTrue(by(BOB) { cancel(code()) } is WriteResult.Rejected)
        assertMatch(match(invited))
    }

    @Test
    fun declineByTheInvitedIsDone() = runTest {
        val invited = draft(invitedUid = CAT.uid).also { created(it) }

        assertEquals(WriteResult.Done, by(CAT) { decline(code()) })
        assertEquals(WriteResult.Done, repository.decline(code()))
        assertMatch(match(invited).copy(declined = true))
    }

    @Test
    fun declineByAStrangerIsRejected() = runTest {
        val invited = draft(invitedUid = CAT.uid).also { created(it) }

        assertTrue(by(BOB) { decline(code()) } is WriteResult.Rejected)
        assertTrue(by(ANN) { decline(code()) } is WriteResult.Rejected)
        assertMatch(match(invited))
    }

    @Test
    fun observeMineIsNullWhenSignedOut() = runTest {
        created()
        signOut()

        assertNull(repository.observeMine().first())
    }

    @Test
    fun observeMineListsMatchesWhereIAmAParticipantNewestFirst() = runTest {
        claimed()
        setNow(START.plusSeconds(60))
        val invitingBob = draft(code = code(OTHER_CODE), challenger = CAT, invitedUid = BOB.uid).also { created(it) }
        setNow(START.plusSeconds(120))
        val withoutBob = draft(code = code(THIRD_CODE)).also { created(it) }
        val claimedByBob = match(opponent = BOB)

        assertEquals(listOf(match(invitingBob, START.plusSeconds(60)), claimedByBob), by(BOB) { observeMine().first() })
        assertEquals(listOf(match(withoutBob, START.plusSeconds(120)), claimedByBob), by(ANN) { observeMine().first() })
    }

    protected companion object {
        const val CODE: String = "K7Q2MX"
        const val OTHER_CODE: String = "ZZZ999"
        const val THIRD_CODE: String = "AB12CD"

        /** With "K7Q2M" in front: one valid code more than [DAILY_MATCH_CAP]. */
        const val CAP_SUFFIXES: String = "0123456789ABCDEFGHJKM"
        val START: Instant = Instant.parse("2026-11-07T12:00:00Z")
        val ANN = Player("ann-uid", "Ann", photoUrl = null)
        val BOB = Player("bob-uid", "Bob", photoUrl = "https://example.com/bob.png")
        val CAT = Player("cat-uid", "Cat", photoUrl = null)
        val LEGS = listOf(
            MatchLeg(LegRef.Daily(GameId("zip"), LocalDate.of(2026, 11, 7), 431), authorUid = null),
            MatchLeg(LegRef.Custom(GameId("queens"), checkNotNull(ShareCode.parse("Q7Q2MX"))), authorUid = CAT.uid),
        )
    }
}
