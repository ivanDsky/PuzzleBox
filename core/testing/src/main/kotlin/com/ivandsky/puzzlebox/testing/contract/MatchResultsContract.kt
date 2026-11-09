package com.ivandsky.puzzlebox.testing.contract

import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.PostResult
import com.ivandsky.puzzlebox.data.match.ResultRead
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The result half of [MatchRepositoryContract]: posting a leg result with its progress entry, and the sealed read of
 * the other player's result. An adapter's contract test subclasses this class, so it runs both halves.
 */
abstract class MatchResultsContract : MatchRepositoryContract() {

    /** [player] posts a result for [legIndex] and stays signed in. */
    protected suspend fun posted(player: Player, legIndex: Int = 0): LegResult =
        result(player.uid, legIndex).also { assertEquals(PostResult.Posted, by(player) { post(code(), it) }) }

    protected suspend fun read(player: Player, legIndex: Int = 0) = repository.readResult(code(), legIndex, player.uid)

    @Test
    fun postStoresTheResultAndProgress() = runTest {
        claimed()
        val mine = posted(BOB)

        assertMatch(match(opponent = BOB).copy(progress = mapOf(BOB.uid to setOf(0))))
        assertEquals(ResultRead.Found(mine), read(BOB))
    }

    @Test
    fun secondPostForTheSameLegIsDuplicate() = runTest {
        claimed()
        val first = posted(BOB)

        assertEquals(PostResult.Duplicate, repository.post(code(), result(BOB.uid, timeMs = 5_000)))
        assertEquals(ResultRead.Found(first), read(BOB))
    }

    @Test
    fun postAfterCloseIsLate() = runTest {
        claimed()
        setNow(match().closesAt)

        assertEquals(PostResult.Late, repository.post(code(), result(BOB.uid)))
        assertEquals(ResultRead.Missing, read(BOB))
        assertMatch(match(opponent = BOB))
    }

    @Test
    fun postWithCompletionAfterDeadlineIsLate() = runTest {
        claimed()
        setNow(match().deadline.plusSeconds(3_600))
        val tooLate = result(BOB.uid, completedAt = match().deadline.plusMillis(1))

        assertEquals(PostResult.Late, repository.post(code(), tooLate))
        assertEquals(ResultRead.Missing, read(BOB))
    }

    @Test
    fun postInGraceWithCompletionBeforeDeadlineIsPosted() = runTest {
        claimed()
        setNow(match().closesAt.minusSeconds(1))
        val inTime = result(BOB.uid, completedAt = match().deadline)

        assertEquals(PostResult.Posted, repository.post(code(), inTime))
        assertEquals(ResultRead.Found(inTime), read(BOB))
    }

    @Test
    fun postByAStrangerIsRejected() = runTest {
        claimed()

        assertTrue(by(CAT) { post(code(), result(CAT.uid)) } is PostResult.Rejected)
        assertMatch(match(opponent = BOB))
    }

    @Test
    fun opponentResultIsSealedUntilMyOwnIsPosted() = runTest {
        claimed()
        val theirs = posted(BOB)

        assertEquals(ResultRead.Sealed, by(ANN) { readResult(code(), 0, BOB.uid) })
        posted(ANN, legIndex = 1)
        assertEquals(ResultRead.Sealed, read(BOB))
        posted(ANN, legIndex = 0)
        assertEquals(ResultRead.Found(theirs), read(BOB))
    }

    @Test
    fun opponentResultIsReadableAfterClose() = runTest {
        claimed()
        val theirs = posted(BOB)
        setNow(match().closesAt)

        assertEquals(ResultRead.Found(theirs), by(ANN) { readResult(code(), 0, BOB.uid) })
    }

    @Test
    fun ownResultIsAlwaysReadable() = runTest {
        claimed()
        val mine = posted(BOB)

        assertEquals(ResultRead.Found(mine), read(BOB))
        setNow(match().closesAt.plusSeconds(3_600))
        assertEquals(ResultRead.Found(mine), read(BOB))
    }

    @Test
    fun absentResultIsMissing() = runTest {
        claimed()

        assertEquals(ResultRead.Missing, by(ANN) { readResult(code(), 0, BOB.uid) })
        assertEquals(ResultRead.Missing, read(ANN))
    }

    @Test
    fun readResultOfflineIsOffline() = runTest {
        claimed()
        posted(BOB)
        goOffline()

        assertEquals(ResultRead.Offline, read(BOB))
    }
}
