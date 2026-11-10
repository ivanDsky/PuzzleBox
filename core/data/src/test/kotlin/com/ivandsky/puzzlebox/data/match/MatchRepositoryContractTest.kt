package com.ivandsky.puzzlebox.data.match

import app.cash.turbine.test
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.contract.MatchResultsContract
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Runs both halves of the match contract ([MatchResultsContract] extends the base) against [FakeMatchRepository],
 * then pins what only the fake's knobs can show: [FakeMatchRepository.online] and [FakeMatchRepository.now] move every
 * call and every open flow.
 */
class MatchRepositoryContractTest : MatchResultsContract() {

    private val fake = FakeMatchRepository()

    override val repository: MatchRepository get() = fake

    override fun signInAs(uid: String) {
        fake.signedInUid = uid
    }

    override fun signOut() {
        fake.signedInUid = null
    }

    override fun goOffline() {
        fake.online = false
    }

    override fun setNow(now: Instant) {
        fake.now = now
    }

    override fun occupyPuzzleCode(code: ShareCode) = fake.occupyPuzzleCode(code)

    @Test
    fun `offline every write is refused, nothing changes, and back online it is all still there`() = runTest {
        claimed()
        val before = fake.matches
        fake.online = false

        assertEquals(ClaimResult.Offline, fake.claim(code(), BOB))
        assertEquals(WriteResult.Offline, fake.cancel(code()))
        assertEquals(WriteResult.Offline, fake.decline(code()))
        assertEquals(PostResult.Unavailable::class, fake.post(code(), result(BOB.uid))::class)
        assertEquals(PuzzleCodeCheck.Unavailable(offline = true), fake.puzzleCodeTaken(code()))
        assertEquals(before, fake.matches)

        fake.online = true
        assertEquals(MatchFetch.Found(match(opponent = BOB)), fetch())
        assertEquals(ResultRead.Missing, fake.readResult(code(), 0, BOB.uid))
    }

    @Test
    fun `an open observe follows the online knob and every change to the match`() = runTest {
        created()

        fake.observe(code()).test {
            assertEquals(MatchFetch.Found(match()), awaitItem())
            fake.online = false
            assertEquals(MatchFetch.Offline, awaitItem())
            fake.online = true
            assertEquals(MatchFetch.Found(match()), awaitItem())
            fake.cancel(code())
            assertEquals(MatchFetch.Found(match().copy(cancelled = true)), awaitItem())
        }
    }

    @Test
    fun `an open observeMine follows sign in, sign out and new matches`() = runTest {
        signOut()

        fake.observeMine().test {
            assertNull(awaitItem())
            signInAs(ANN.uid)
            assertEquals(emptyList<Match>(), awaitItem())
            fake.create(draft())
            assertEquals(listOf(match()), awaitItem())
            signOut()
            assertNull(awaitItem())
        }
    }

    @Test
    fun `now decides the createdAt of a new match`() = runTest {
        val later = START.plusSeconds(7_200)
        fake.now = later
        created()

        assertEquals(match(createdAt = later), fake.matches[code()])
    }

    @Test
    fun `the daily cap is counted per UTC day and per challenger`() = runTest {
        fake.now = Instant.parse("2026-11-07T23:59:00Z")
        val codes = CAP_SUFFIXES.map { code("K7Q2M$it") }
        codes.take(DAILY_MATCH_CAP).forEach { created(draft(code = it)) }
        signInAs(BOB.uid)
        assertEquals(CreateResult.Created(code()), fake.create(draft(challenger = BOB)))

        signInAs(ANN.uid)
        assertEquals(CreateResult.DailyCapReached, fake.create(draft(code = codes.last())))
        fake.now = Instant.parse("2026-11-08T00:00:00Z")
        assertEquals(CreateResult.Created(codes.last()), fake.create(draft(code = codes.last())))
    }

    @Test
    fun `now moves the claim deadline and the post close`() = runTest {
        created()
        fake.now = match().deadline.minusMillis(1)
        signInAs(BOB.uid)
        assertEquals(ClaimResult.Claimed, fake.claim(code(), BOB))

        fake.now = match().closesAt.minusMillis(1)
        assertEquals(PostResult.Posted, fake.post(code(), result(BOB.uid)))
        fake.now = match().closesAt
        assertEquals(PostResult.Late, fake.post(code(), result(BOB.uid, legIndex = 1)))
    }
}
