package com.ivandsky.puzzlebox.data.match

import android.app.Application
import app.cash.turbine.test
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where [RoutingMatchRepository] sends each call, read off the call logs of both stores: a held self code to the
 * self store, every other code and every friend-duel lifecycle call to the remote.
 *
 * Robolectric: a failed lookup logs through android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoutingMatchRepositoryTest {

    private val self = RecordingSelfMatchRepository()
    private val remote = RecordingMatchRepository()
    private val router = RoutingMatchRepository(self, remote)

    @Before
    fun aSelfDuelAndAFriendDuel() = runTest {
        self.save(selfDuel(), past())
        remote.backend.now = START
        remote.backend.signedInUid = ANN.uid
        check(remote.backend.create(friendDraft()) == CreateResult.Created(FRIEND))
        remote.backend.signedInUid = BOB.uid
        check(remote.backend.claim(FRIEND, BOB) == ClaimResult.Claimed)
    }

    @Test
    fun `a held code is observed in the self store only`() = runTest {
        assertEquals(MatchFetch.Found(selfDuel()), router.observe(HELD).first())

        assertEquals(listOf("holds", "observe"), self.calls)
        assertEquals(emptyList<String>(), remote.calls)
    }

    @Test
    fun `my result for a held code is posted to and read from the self store only`() = runTest {
        val mine = result(SelfSeats.ME)

        assertEquals(PostResult.Posted, router.post(HELD, mine))
        assertEquals(ResultRead.Found(mine), router.readResult(HELD, 0, SelfSeats.ME))
        assertEquals(ResultRead.Found(past()[1]), router.readResult(HELD, 1, SelfSeats.PAST))

        assertEquals(listOf("holds", "post", "holds", "readResult", "holds", "readResult"), self.calls)
        assertEquals(emptyList<String>(), remote.calls)
    }

    @Test
    fun `an open observe of a held code follows the self store`() = runTest {
        router.observe(HELD).test {
            assertEquals(MatchFetch.Found(selfDuel()), awaitItem())
            router.post(HELD, result(SelfSeats.ME))
            assertEquals(MatchFetch.Found(selfDuel(mine = setOf(0))), awaitItem())
            self.remove(HELD)
            assertEquals(MatchFetch.NotFound, awaitItem())
        }
    }

    @Test
    fun `a code the self store does not hold goes to the remote after one lookup each`() = runTest {
        val bobs = result(BOB.uid)

        assertEquals(MatchFetch.Found(friendDuel()), router.observe(FRIEND).first())
        assertEquals(PostResult.Posted, router.post(FRIEND, bobs))
        assertEquals(ResultRead.Found(bobs), router.readResult(FRIEND, 0, BOB.uid))

        assertEquals(listOf("holds", "holds", "holds"), self.calls)
        assertEquals(listOf("observe", "post", "readResult"), remote.calls)
    }

    @Test
    fun `create, claim, cancel, decline, the code check and my list never reach the self store`() = runTest {
        remote.backend.signedInUid = ANN.uid

        // The remote knows nothing of the self duel, so its code is free there and the calls answer as for any code.
        assertEquals(PuzzleCodeCheck.Free, router.puzzleCodeTaken(HELD))
        assertEquals(CreateResult.Created(HELD), router.create(friendDraft(HELD)))
        assertEquals(WriteResult.Rejected("only the invited player declines"), router.decline(HELD))
        assertEquals(WriteResult.Done, router.cancel(HELD))
        remote.backend.signedInUid = BOB.uid
        assertEquals(ClaimResult.Cancelled, router.claim(HELD, BOB))
        assertEquals(listOf(friendDuel()), router.observeMine().first())

        assertEquals(emptyList<String>(), self.calls)
        assertEquals(listOf("puzzleCodeTaken", "create", "decline", "cancel", "claim", "observeMine"), remote.calls)
    }

    @Test
    fun `a failing lookup falls back to the remote, for a held code too`() = runTest {
        self.lookupFailure = IllegalStateException("the database is closed")
        val bobs = result(BOB.uid)

        assertEquals(MatchFetch.Found(friendDuel()), router.observe(FRIEND).first())
        assertEquals(PostResult.Posted, router.post(FRIEND, bobs))
        assertEquals(ResultRead.Found(bobs), router.readResult(FRIEND, 0, BOB.uid))
        assertEquals(MatchFetch.NotFound, router.observe(HELD).first())

        assertEquals(listOf("holds", "holds", "holds", "holds"), self.calls)
        assertEquals(listOf("observe", "post", "readResult", "observe"), remote.calls)
    }

    @Test
    fun `a cancelled lookup cancels the call and reaches neither store`() = runTest {
        val cancelled = CancellationException("the screen is gone")
        self.lookupFailure = cancelled

        assertSame(cancelled, runCatching { router.observe(FRIEND).first() }.exceptionOrNull())
        assertSame(cancelled, runCatching { router.post(FRIEND, result(BOB.uid)) }.exceptionOrNull())
        assertSame(cancelled, runCatching { router.readResult(FRIEND, 0, BOB.uid) }.exceptionOrNull())

        assertEquals(listOf("holds", "holds", "holds"), self.calls)
        assertEquals(emptyList<String>(), remote.calls)
    }

    @Test
    fun `observe looks the code up on each collection, not when it is called`() = runTest {
        self.remove(HELD)
        val flow = router.observe(HELD)
        assertEquals(emptyList<String>(), self.calls)

        assertEquals(MatchFetch.NotFound, flow.first())
        assertEquals(listOf("observe"), remote.calls)

        self.save(selfDuel(), past())
        assertEquals(MatchFetch.Found(selfDuel()), flow.first())
        assertEquals(listOf("holds", "holds", "observe"), self.calls)
        assertEquals(listOf("observe"), remote.calls)
    }

    /** The self duel as its store reads it back, with my posted legs. */
    private fun selfDuel(mine: Set<Int> = emptySet()) = Match(
        code = HELD,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = START,
        deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
        challenger = Player(SelfSeats.ME, "Ann", null),
        invitedUid = null,
        opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
        legs = LEGS,
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = mapOf(SelfSeats.PAST to LEGS.indices.toSet(), SelfSeats.ME to mine),
    )

    private fun past() = LEGS.indices.map { result(SelfSeats.PAST, it) }

    private fun friendDraft(code: ShareCode = FRIEND) =
        MatchDraft(code, deadlineHours = 24, ANN, invitedUid = null, LEGS, includeFriends = true, rematchOf = null)

    /** Ann's duel as the remote stores it once Bob claimed it. */
    private fun friendDuel() = Match(
        FRIEND, Match.FORMAT_VERSION, START, deadlineHours = 24, ANN, invitedUid = null, opponent = BOB, LEGS,
        includeFriends = true, cancelled = false, declined = false, rematchOf = null, progress = emptyMap(),
    )

    private fun result(uid: String, legIndex: Int = 0) =
        LegResult(uid, legIndex, timeMs = 80_000L + legIndex, hints = 1, completedAtMs = COMPLETED_AT_MS, gaveUp = false)

    private companion object {
        val HELD = checkNotNull(ShareCode.parse("ABC234"))
        val FRIEND = checkNotNull(ShareCode.parse("K7Q2MX"))
        val START: Instant = Instant.parse("2026-11-07T12:00:00Z")

        /** Inside the friend duel's deadline, so the remote takes the post. */
        val COMPLETED_AT_MS = START.plusSeconds(90).toEpochMilli()
        val ANN = Player("ann-uid", "Ann", photoUrl = null)
        val BOB = Player("bob-uid", "Bob", photoUrl = null)
        val LEGS = listOf(
            MatchLeg(LegRef.Daily(GameId("zip"), LocalDate.of(2026, 11, 7), 431), authorUid = null),
            MatchLeg(LegRef.Daily(GameId("queens"), LocalDate.of(2026, 11, 6), 555), authorUid = null),
        )
    }
}

/** The self store with a log of the calls the router made, and a lookup that can be made to fail. */
private class RecordingSelfMatchRepository(
    private val store: FakeSelfMatchRepository = FakeSelfMatchRepository(),
) : SelfMatchRepository by store {

    val calls = mutableListOf<String>()
    var lookupFailure: Exception? = null

    override suspend fun holds(code: ShareCode): Boolean {
        calls += "holds"
        lookupFailure?.let { throw it }
        return store.holds(code)
    }

    override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck =
        store.puzzleCodeTaken(code).also { calls += "puzzleCodeTaken" }
    override suspend fun create(draft: MatchDraft): CreateResult = store.create(draft).also { calls += "create" }
    override suspend fun claim(code: ShareCode, me: Player): ClaimResult = store.claim(code, me).also { calls += "claim" }
    override suspend fun cancel(code: ShareCode): WriteResult = store.cancel(code).also { calls += "cancel" }
    override suspend fun decline(code: ShareCode): WriteResult = store.decline(code).also { calls += "decline" }
    override fun observe(code: ShareCode): Flow<MatchFetch> = store.observe(code).also { calls += "observe" }
    override fun observeMine(): Flow<List<Match>?> = store.observeMine().also { calls += "observeMine" }
    override suspend fun post(code: ShareCode, result: LegResult): PostResult = store.post(code, result).also { calls += "post" }
    override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead =
        store.readResult(code, legIndex, uid).also { calls += "readResult" }
}

/** The remote with a log of the calls the router made; the test arranges friend duels on [backend] directly. */
private class RecordingMatchRepository(val backend: FakeMatchRepository = FakeMatchRepository()) : MatchRepository {

    val calls = mutableListOf<String>()

    override suspend fun puzzleCodeTaken(code: ShareCode): PuzzleCodeCheck =
        backend.puzzleCodeTaken(code).also { calls += "puzzleCodeTaken" }
    override suspend fun create(draft: MatchDraft): CreateResult = backend.create(draft).also { calls += "create" }
    override suspend fun claim(code: ShareCode, me: Player): ClaimResult = backend.claim(code, me).also { calls += "claim" }
    override suspend fun cancel(code: ShareCode): WriteResult = backend.cancel(code).also { calls += "cancel" }
    override suspend fun decline(code: ShareCode): WriteResult = backend.decline(code).also { calls += "decline" }
    override fun observe(code: ShareCode): Flow<MatchFetch> = backend.observe(code).also { calls += "observe" }
    override fun observeMine(): Flow<List<Match>?> = backend.observeMine().also { calls += "observeMine" }
    override suspend fun post(code: ShareCode, result: LegResult): PostResult = backend.post(code, result).also { calls += "post" }
    override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead =
        backend.readResult(code, legIndex, uid).also { calls += "readResult" }
}
