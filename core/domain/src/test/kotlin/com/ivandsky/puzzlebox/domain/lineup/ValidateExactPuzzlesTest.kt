package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Create's online check of the lineup's exact puzzles (A17): one read each, the first gone one by position. */
class ValidateExactPuzzlesTest {

    private val shared = ReadCountingSharedPuzzleRepository()
    private val check = ValidateExactPuzzles(shared)

    private fun code(value: String) = requireNotNull(ShareCode.parse(value))
    private fun exact(value: String) = ExactPuzzle(LegRef.Custom(alpha, code(value)), "bo", "T", "Bo", PuzzleSource.Friend)

    @Test
    fun `every exact puzzle is read once and published ones pass`() = runTest {
        shared.store(code("K7Q2MX"))
        shared.store(code("B8C3DF"))

        val result = check(listOf(AnyGame(beta), exact("K7Q2MX"), AnyGame(alpha), exact("B8C3DF")))

        assertEquals(ValidateExactPuzzles.Result.Available, result)
        assertEquals(listOf(code("B8C3DF"), code("K7Q2MX")), shared.reads.sortedBy { it.value })
    }

    @Test
    fun `the first taken down puzzle is refused by its position from 1`() = runTest {
        shared.store(code("K7Q2MX"))
        shared.store(code("B8C3DF"), unpublished = true)
        shared.store(code("D5F7GH"), unpublished = true)

        val result = check(listOf(exact("K7Q2MX"), exact("B8C3DF"), exact("D5F7GH")))

        assertEquals(ValidateExactPuzzles.Result.Unavailable(2), result)
    }

    @Test
    fun `a puzzle that is gone or a version this app cannot read is unavailable`() = runTest {
        shared.store(code("K7Q2MX"))
        shared.fetchAnswer = { SharedFetchResult.UnsupportedVersion(9) }
        assertEquals(ValidateExactPuzzles.Result.Unavailable(1), check(listOf(exact("K7Q2MX"))))

        shared.fetchAnswer = { SharedFetchResult.NotFound }
        assertEquals(ValidateExactPuzzles.Result.Unavailable(2), check(listOf(AnyGame(alpha), exact("B8C3DF"))))
    }

    @Test
    fun `offline is offline, never a claim that a puzzle is gone`() = runTest {
        shared.store(code("K7Q2MX"))
        shared.online = false

        assertEquals(ValidateExactPuzzles.Result.Offline, check(listOf(exact("K7Q2MX"))))
    }

    @Test
    fun `a read that never answers makes its puzzle unavailable after the time limit and does not hold the others`() = runTest {
        shared.store(code("K7Q2MX"))
        val stored = shared.fetchAnswer
        shared.fetchAnswer = { requested -> if (requested == code("B8C3DF")) awaitCancellation() else stored(requested) }

        val result = check(listOf(exact("K7Q2MX"), exact("B8C3DF")))

        assertEquals(ValidateExactPuzzles.Result.Unavailable(2), result)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test
    fun `Any and daily entries are not read`() = runTest {
        val daily = ExactPuzzle(LegRef.Daily(alpha, LocalDate.of(2026, 11, 9), 7), null, "Day", null, PuzzleSource.PastDay)

        val result = check(listOf(AnyGame(alpha), daily))

        assertEquals(ValidateExactPuzzles.Result.Available, result)
        assertEquals(emptyList<ShareCode>(), shared.reads)
    }
}
