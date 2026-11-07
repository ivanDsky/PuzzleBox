package com.ivandsky.puzzlebox.testing.contract

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Behavior every [CustomBoardRepository] adapter must pass. Subclasses supply a fresh, empty,
 * online [repository] and the hooks below; adapter-specific cases live in the subclass.
 * Test names are camel case because this class ships in a main source set that gets dexed.
 */
abstract class CustomBoardRepositoryContract {

    protected abstract val repository: CustomBoardRepository

    /** Makes [uid] the signed-in user for the calls that follow. */
    protected abstract fun signInAs(uid: String)

    /** Leaves no signed-in user, so board reads are denied. */
    protected abstract fun signOut()

    /** Cuts the backend off for the calls that follow. */
    protected abstract fun goOffline()

    protected fun code(value: String = CODE): ShareCode = checkNotNull(ShareCode.parse(value))

    /** Submits each entry as its own signed-in uid, then signs in as [VIEWER]. */
    protected suspend fun submitAll(vararg entries: LeaderboardEntry) {
        entries.forEach {
            signInAs(it.uid)
            assertEquals(SubmitResult.Ok(it), repository.submit(code(), it))
        }
        signInAs(VIEWER)
    }

    private suspend fun board(order: RankOrder, limit: Int = 10) =
        repository.observe(GAME, code(), order, limit).first()

    @Test
    fun timeThenHintsAndHintsThenTimeOrderTheSameEntriesDifferently() = runTest {
        submitAll(FAST_MANY_HINTS, SLOW_NO_HINTS, MIDDLE)

        assertEquals(listOf(FAST_MANY_HINTS, MIDDLE, SLOW_NO_HINTS), board(RankOrder.TimeThenHints))
        assertEquals(listOf(SLOW_NO_HINTS, MIDDLE, FAST_MANY_HINTS), board(RankOrder.HintsThenTime))
    }

    @Test
    fun limitCapsTheList() = runTest {
        submitAll(FAST_MANY_HINTS, SLOW_NO_HINTS, MIDDLE)

        assertEquals(listOf(FAST_MANY_HINTS, MIDDLE), board(RankOrder.TimeThenHints, limit = 2))
    }

    @Test
    fun aSecondSubmitForTheSameUidIsDedupedAndKeepsTheFirstEntry() = runTest {
        submitAll(MIDDLE)
        val faster = MIDDLE.copy(timeMs = 10_000L, completedAtMs = MIDDLE.completedAtMs + 1)

        signInAs(MIDDLE.uid)
        assertEquals(SubmitResult.Deduped(MIDDLE), repository.submit(code(), faster))
        assertEquals(listOf(MIDDLE), board(RankOrder.TimeThenHints))
    }

    @Test
    fun solvesCountEqualsTheEntryCount() = runTest {
        signInAs(VIEWER)
        assertEquals(0, repository.solvesCount(code()))

        submitAll(FAST_MANY_HINTS, SLOW_NO_HINTS, MIDDLE)
        assertEquals(3, repository.solvesCount(code()))
    }

    @Test
    fun aBoardIsKeyedByItsCode() = runTest {
        submitAll(MIDDLE)

        assertEquals(emptyList<LeaderboardEntry>(), repository.observe(GAME, code(OTHER_CODE), RankOrder.TimeThenHints, 10).first())
        assertEquals(0, repository.solvesCount(code(OTHER_CODE)))
    }

    @Test
    fun offlineObserveEmitsNull() = runTest {
        submitAll(MIDDLE)
        goOffline()

        assertNull(board(RankOrder.TimeThenHints))
    }

    @Test
    fun signedOutObserveEmitsNull() = runTest {
        submitAll(MIDDLE)
        signOut()

        assertNull(board(RankOrder.TimeThenHints))
    }

    protected companion object {
        val GAME: GameId = GameId("queens")
        const val CODE: String = "K7Q2MX"
        const val OTHER_CODE: String = "ZZZ999"
        const val VIEWER: String = "viewer-uid"
        val FAST_MANY_HINTS: LeaderboardEntry = LeaderboardEntry("fast-uid", "Fast", "custom/queens/$CODE", 60_000L, 2, 1_700_000_000_000L)
        val SLOW_NO_HINTS: LeaderboardEntry = LeaderboardEntry("slow-uid", "Slow", "custom/queens/$CODE", 90_000L, 0, 1_700_000_000_000L)
        val MIDDLE: LeaderboardEntry = LeaderboardEntry("middle-uid", "Middle", "custom/queens/$CODE", 75_000L, 1, 1_700_000_000_000L)
    }
}
