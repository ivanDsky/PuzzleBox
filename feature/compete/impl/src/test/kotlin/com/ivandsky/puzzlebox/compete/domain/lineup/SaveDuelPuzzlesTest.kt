package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Accepting a duel keeps its exact puzzles with the friend's puzzles (A16): each opened once, side by side, a failed one skipped. */
class SaveDuelPuzzlesTest {

    private val opened = mutableListOf<ShareCode>()
    private var answer: suspend (ShareCode) -> OpenOutcome = { OpenOutcome.NoLongerAvailable }
    private val save = SaveDuelPuzzles { code -> opened += code; answer(code) }

    private fun code(value: String) = requireNotNull(ShareCode.parse(value))

    private fun savedRow(code: ShareCode) = ReceivedPuzzle(
        code = code, gameId = TestGames.alpha, variant = TestGames.variant(TestGames.alpha), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "T", authorName = "Bo", note = "", authorTimeMs = 1_000L,
        authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH, unpublished = false, postState = PostState.Pending,
        postAttempts = 0, difficulty = null,
    )

    @Test
    fun `both exact puzzles of a two-custom duel are opened, once each, and counted as saved`() = runTest {
        answer = { OpenOutcome.Opened(savedRow(it)) }

        val saved = save(listOf(code("K7Q2MX"), code("B8C3DF"), code("K7Q2MX")))

        assertEquals(2, saved)
        assertEquals(listOf(code("B8C3DF"), code("K7Q2MX")), opened.sortedBy { it.value })
    }

    @Test
    fun `a puzzle taken down or offline is skipped and the others are still saved`() = runTest {
        answer = { if (it == code("K7Q2MX")) OpenOutcome.Opened(savedRow(it)) else if (it == code("B8C3DF")) OpenOutcome.NoLongerAvailable else OpenOutcome.Offline }

        assertEquals(1, save(listOf(code("B8C3DF"), code("K7Q2MX"), code("D5F7GH"))))
        assertEquals(3, opened.size)
    }

    @Test
    fun `a read that never answers is given up after the time limit and does not hold the others`() = runTest {
        answer = { if (it == code("B8C3DF")) awaitCancellation() else OpenOutcome.Opened(savedRow(it)) }

        assertEquals(1, save(listOf(code("B8C3DF"), code("K7Q2MX"))))
        assertEquals(5_000L, testScheduler.currentTime)
    }
}
