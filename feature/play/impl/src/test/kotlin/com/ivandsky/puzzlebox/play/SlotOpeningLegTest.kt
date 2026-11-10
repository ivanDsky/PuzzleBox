package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A duel's puzzle ([PlaySlot.Leg]) opens from its own saved row, else from the match's
 * reference for that leg, read once: a day through the by-date daily read, a friend's
 * puzzle through the stored row or its re-checked fetch. Either way the board is a replay
 * under the puzzle's own identity, so the leg's solve is the daily's or the friend
 * puzzle's, and no leg save stands in for a daily or friend save or theirs for it.
 */
class SlotOpeningLegTest {

    private val alpha = TestGames.alpha
    private val today = LocalDate.parse("2026-11-04")
    private val pastDay = LocalDate.parse("2026-10-01")
    private val matchCode = checkNotNull(ShareCode.parse("M8T3VQ"))
    private val friendCode = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val dailyLeg = LegRef.Daily(alpha, pastDay, 224)
    private val todayLeg = LegRef.Daily(alpha, today, 258)
    private val friendLeg = LegRef.Custom(alpha, friendCode)

    private val fakes = FakeGames(listOf(alpha))
    private val daily = FakeDailyRepository()
    private val saved = FakeSavedPlayRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val calendar = FakeGameCalendar(today)
    private val matches = CountingMatchRepository(FakeMatchRepository().apply { signedInUid = CHALLENGER.uid })
    private val opening = SlotOpening(
        daily = daily,
        pastDays = LoadPastDay(daily, fakes.games),
        saved = saved,
        calendar = calendar,
        games = fakes.games,
        lane = WriteLane(),
        received = received,
        matches = matches,
        loadShared = LoadSharedPuzzle(
            registry = AlphaShareEditor.registry(),
            shared = shared,
            received = received,
            budget = { Budget.within(1_000L, System::nanoTime) },
            clock = { Instant.EPOCH },
        ),
        dispatchers = testDispatchers(),
    )

    private fun legSlot(index: Int = 0) = PlaySlot.Leg(alpha, matchCode.value, index)

    @Test
    fun aDailyLegOpensTheDaysPuzzleUnderTheDailyIdentity() = runTest {
        createMatch(dailyLeg)
        val puzzle = TestGames.puzzle(alpha, tag = 3)
        daily.setCached(alpha, pastDay, puzzle)

        val origin = opening.open(legSlot()) as SlotOpening.Origin.Restored

        assertArrayEquals(TestGames.replayBlob(alpha, puzzle, PuzzleKey.Daily(pastDay, 224)), origin.session)
        assertEquals("one match read", 1, matches.observes)
    }

    @Test
    fun aLegNamingTodaysDailyOpensItBeforeTheDayIsCaptured() = runTest {
        createMatch(todayLeg)
        val puzzle = TestGames.puzzle(alpha, tag = 4)
        daily.setToday(alpha, DailyToday.Fresh(puzzle))

        val origin = opening.open(legSlot()) as SlotOpening.Origin.Restored

        assertArrayEquals(TestGames.replayBlob(alpha, puzzle, PuzzleKey.Daily(today, 258)), origin.session)
        assertEquals(1, daily.todayCalls(alpha))
    }

    @Test
    fun aCustomLegOpensTheStoredFriendPuzzleUnderItsCodeIdentity() = runTest {
        createMatch(friendLeg)
        received.upsert(friendPuzzle(tag = 5))
        shared.online = false

        val origin = opening.open(legSlot()) as SlotOpening.Origin.Restored

        val stored = Puzzle(alpha, TestGames.variant(alpha), 1, TestGames.puzzle(alpha, tag = 5).payload)
        assertArrayEquals(TestGames.replayBlob(alpha, stored, PuzzleKey.Custom(friendCode.value)), origin.session)
    }

    @Test
    fun aCustomLegIsRetryableOnlyWhenOffline() = runTest {
        createMatch(friendLeg)
        shared.online = false
        assertTrue((opening.open(legSlot()) as SlotOpening.Origin.Unavailable).retryable)

        shared.online = true
        assertFalse("no puzzle shared under the code", (opening.open(legSlot()) as SlotOpening.Origin.Unavailable).retryable)
    }

    @Test
    fun aCustomLegsFirstOpenFetchesVerifiesAndStoresTheFriendPuzzle() = runTest {
        createMatch(friendLeg)
        shared.publish(sharedDoc(AlphaShareEditor.encodeShare(8)))

        val origin = opening.open(legSlot()) as SlotOpening.Origin.Restored

        val verified = TestGames.puzzle(alpha, tag = 8)
        assertArrayEquals("the verified puzzle is kept", verified.payload, received.get(friendCode)?.puzzleBytes)
        assertArrayEquals(TestGames.replayBlob(alpha, verified, PuzzleKey.Custom(friendCode.value)), origin.session)
    }

    @Test
    fun aCustomLegInANewerShareFormatAsksForAnUpdate() = runTest {
        createMatch(friendLeg)
        shared.publish(sharedDoc(AlphaShareEditor.encodeShare(8), shareCodecVersion = AlphaShareEditor.SHARE_VERSION + 1))

        val origin = opening.open(legSlot()) as SlotOpening.Origin.Unavailable

        assertEquals("Update PuzzleBox to play this puzzle.", origin.message)
        assertFalse(origin.retryable)
        assertNull("nothing kept", received.get(friendCode))
    }

    @Test
    fun aLegSaveRowRestoresBeforeAnyMatchOrPuzzleRead() = runTest {
        val blob = SessionBlob.encode(alpha, byteArrayOf(4, 2))
        saved.save(legSlot(), blob, elapsedMs = 30_000L)

        val origin = opening.open(legSlot()) as SlotOpening.Origin.Restored

        assertArrayEquals(blob, origin.session)
        assertEquals(0, matches.observes)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun aLegNeverRestoresADailyOrFriendSaveOfTheSameGame() = runTest {
        createMatch(dailyLeg, friendLeg)
        val puzzle = TestGames.puzzle(alpha, tag = 6)
        daily.setCached(alpha, pastDay, puzzle)
        received.upsert(friendPuzzle(tag = 7))
        val daySlot = PlaySlot.Daily(alpha, pastDay)
        val friendSlot = PlaySlot.Custom(alpha, friendCode.value)
        val dayBlob = SessionBlob.encode(alpha, byteArrayOf(1))
        val friendBlob = SessionBlob.encode(alpha, byteArrayOf(2))
        saved.save(daySlot, dayBlob, elapsedMs = 10_000L)
        saved.save(friendSlot, friendBlob, elapsedMs = 20_000L)

        val dayLegOrigin = opening.open(legSlot(0)) as SlotOpening.Origin.Restored
        val friendLegOrigin = opening.open(legSlot(1)) as SlotOpening.Origin.Restored

        assertArrayEquals(TestGames.replayBlob(alpha, puzzle, PuzzleKey.Daily(pastDay, 224)), dayLegOrigin.session)
        val stored = Puzzle(alpha, TestGames.variant(alpha), 1, TestGames.puzzle(alpha, tag = 7).payload)
        assertArrayEquals(TestGames.replayBlob(alpha, stored, PuzzleKey.Custom(friendCode.value)), friendLegOrigin.session)
        saved.save(legSlot(0), SessionBlob.encode(alpha, byteArrayOf(3)), elapsedMs = 30_000L)
        saved.save(legSlot(1), SessionBlob.encode(alpha, byteArrayOf(4)), elapsedMs = 40_000L)
        assertArrayEquals(dayBlob, (opening.open(daySlot) as SlotOpening.Origin.Restored).session)
        assertArrayEquals(friendBlob, (opening.open(friendSlot) as SlotOpening.Origin.Restored).session)
    }

    @Test
    fun anOfflineMatchIsRetryableAndAMissingOneIsNot() = runTest {
        matches.fake.online = false
        assertTrue((opening.open(legSlot()) as SlotOpening.Origin.Unavailable).retryable)

        matches.fake.online = true
        assertFalse("no such match", (opening.open(legSlot()) as SlotOpening.Origin.Unavailable).retryable)

        createMatch(dailyLeg)
        assertFalse("no such leg", (opening.open(legSlot(1)) as SlotOpening.Origin.Unavailable).retryable)

        matches.scripted = MatchFetch.UnsupportedVersion(found = 2)
        assertFalse("a newer match format", (opening.open(legSlot()) as SlotOpening.Origin.Unavailable).retryable)
    }

    @Test
    fun aDailyLegFetchFailureIsRetryableOnlyWhenOfflineOrThrottled() = runTest {
        createMatch(dailyLeg)
        val retryable = listOf(
            FetchError.Unavailable() to true,
            FetchError.Throttled() to true,
            FetchError.NotFound() to false,
            FetchError.Untrusted("bad signature") to false,
        )
        retryable.forEach { (error, expected) ->
            daily.setDay(alpha, pastDay, DailyDay.Failed(error))

            val origin = opening.open(legSlot()) as SlotOpening.Origin.Unavailable

            assertEquals("$error", expected, origin.retryable)
            assertEquals(pastDayUnavailableMessage(pastDay, error), origin.message)
        }
    }

    private suspend fun createMatch(vararg legs: LegRef) {
        matches.fake.create(
            MatchDraft(
                code = matchCode,
                deadlineHours = 24,
                challenger = CHALLENGER,
                invitedUid = null,
                legs = legs.map { MatchLeg(it, authorUid = null) },
                includeFriends = false,
                rematchOf = null,
            ),
        )
    }

    private fun friendPuzzle(tag: Byte) = ReceivedPuzzle(
        code = friendCode,
        gameId = alpha,
        variant = TestGames.variant(alpha),
        sharedFormatVersion = 1,
        puzzleBytes = TestGames.puzzle(alpha, tag).payload,
        title = "Castle",
        authorName = "Ivan",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "author",
        openedAt = Instant.parse("2026-11-04T09:00:00Z"),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun sharedDoc(payload: ByteArray, shareCodecVersion: Int = AlphaShareEditor.SHARE_VERSION) = SharedPuzzleDoc(
        code = friendCode, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = alpha.value,
        variant = TestGames.variant(alpha).value, shareCodecVersion = shareCodecVersion, payload = payload,
        title = "Castle", authorName = "Ivan", note = "", authorUid = AUTHOR, authorTimeMs = 102_000L,
        authorHints = 0, createdAtMs = 0L, unpublished = false,
    )

    /** Counts match reads; [scripted] answers every read when set (a version the fake can't store). */
    private class CountingMatchRepository(val fake: FakeMatchRepository) : MatchRepository by fake {
        var observes = 0
        var scripted: MatchFetch? = null

        override fun observe(code: ShareCode): Flow<MatchFetch> {
            observes++
            return scripted?.let { flowOf(it) } ?: fake.observe(code)
        }
    }

    private companion object {
        val CHALLENGER = Player("challenger", "Ivan", photoUrl = null)
        const val AUTHOR = "author"
    }
}
