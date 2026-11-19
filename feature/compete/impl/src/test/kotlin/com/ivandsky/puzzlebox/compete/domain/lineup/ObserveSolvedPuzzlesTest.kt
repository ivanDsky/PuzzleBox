package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Past you add sheet's list: what the player solved and a Past you duel can play, newest first solve first, narrowed on the device. */
class ObserveSolvedPuzzlesTest {

    private val registry = TestGames.registry(listOf(alpha, beta))
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val observe = ObserveSolvedPuzzles(registry, ComputeSolvedPool(registry, completions, received), completions, received)

    private val crown = ShareCode.parse("K7Q2MX")!!
    private val unsolved = ShareCode.parse("M3N4P5")!!
    private val takenDown = ShareCode.parse("R6S7T8")!!
    private val own = ShareCode.parse("V2W3X4")!!

    private fun solve(game: GameId, key: PuzzleKey, atMs: Long?, isRecord: Boolean = true) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, key),
        time = 30_000L,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = atMs?.let(Instant::ofEpochMilli),
        isRecord = isRecord,
    )

    private fun daily(day: Int, number: Int? = day) = PuzzleKey.Daily(LocalDate.of(2026, 10, day), puzzleNumber = number)

    private suspend fun hold(code: ShareCode, game: GameId, title: String, author: String = "Bo", unpublished: Boolean = false, level: Difficulty.Level? = null) =
        received.upsert(
            ReceivedPuzzle(
                code = code, gameId = game, variant = TestGames.variant(game), sharedFormatVersion = 1,
                puzzleBytes = byteArrayOf(TestGames.MAGIC), title = title, authorName = author, note = "", authorTimeMs = 1_000L,
                authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH, unpublished = unpublished,
                postState = PostState.Pending, postAttempts = 0, difficulty = level,
            ),
        )

    private fun query(
        text: String = "",
        sources: Set<PuzzleSource> = emptySet(),
        games: Set<GameId> = emptySet(),
    ) = PickerQuery(text, fresh = emptySet(), sources = sources, games = games)

    private var stored = emptyList<CompletionRecord>()

    private suspend fun seed() {
        hold(crown, alpha, "Crown jewels", level = Difficulty.Level.Hard)
        hold(unsolved, beta, "Never played")
        hold(takenDown, alpha, "Gone", unpublished = true)
        stored = listOf(
            solve(alpha, daily(1), atMs = 1_000),
            solve(beta, daily(2), atMs = 3_000),
            solve(alpha, daily(3), atMs = 2_000),
            solve(alpha, PuzzleKey.Custom(crown.value), atMs = 4_000),
            solve(alpha, PuzzleKey.Custom(takenDown.value), atMs = 5_000),
            solve(alpha, PuzzleKey.Custom(own.value), atMs = 6_000),
            solve(alpha, daily(4, number = null), atMs = 7_000),
            solve(beta, daily(5), atMs = 8_000, isRecord = false),
        )
        completions.setRecords(stored)
    }

    private suspend fun TestScope.rows(q: PickerQuery = query()): List<PickablePuzzle> {
        val latest = MutableStateFlow<PickerResult?>(null)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { observe(flowOf(q), flowOf(1)).collect { latest.value = it } }
        advanceUntilIdle()
        job.cancel()
        return checkNotNull(latest.value).rows
    }

    @Test
    fun `lists the solved dailies and the held friend's puzzle, newest first solve first, all played`() = runTest {
        seed()

        val rows = rows()

        assertEquals(listOf("Crown jewels", "Beta #2", "Alpha #3", "Alpha #1"), rows.map { it.entry.title })
        assertTrue(rows.all { it.played && !it.isNew })
        val day = rows[1]
        assertEquals(ExactPuzzle(LegRef.Daily(beta, LocalDate.of(2026, 10, 2), 2), null, "Beta #2", null, PuzzleSource.PastDay), day.entry)
        assertEquals("Oct 2, 2026", day.meta)
        assertNull(day.code)
        val friend = rows[0]
        assertEquals(ExactPuzzle(LegRef.Custom(alpha, crown), "bo", "Crown jewels", "Bo", PuzzleSource.Friend), friend.entry)
        assertEquals(crown, friend.code)
        assertEquals("v1", friend.meta)
        assertEquals(Difficulty.Level.Hard, friend.level)
        assertNull(day.level)
    }

    @Test
    fun `a puzzle the pool cannot play, has no first solve, or is the player's own is not listed`() = runTest {
        seed()

        val titles = rows().map { it.entry.title }

        assertFalse("Never played" in titles) // held, never solved
        assertFalse("Gone" in titles) // taken down
        assertFalse(titles.any { it == "Alpha #4" || it == "Beta #5" }) // no source number; play-again only
        assertEquals(4, titles.size) // the own puzzle is solved but not held, so the pool leaves it out
    }

    @Test
    fun `a missing first solve time sorts last`() = runTest {
        completions.setRecords(listOf(solve(alpha, daily(1), atMs = null), solve(alpha, daily(2), atMs = 5), solve(alpha, daily(3), atMs = 9)))

        assertEquals(listOf("Alpha #3", "Alpha #2", "Alpha #1"), rows().map { it.entry.title })
    }

    @Test
    fun `text finds the friend's puzzle by title and by author`() = runTest {
        seed()

        assertEquals(listOf("Crown jewels"), rows(query(text = "crown")).map { it.entry.title })
        hold(crown, alpha, "Crown jewels", author = "Crowner")
        assertEquals(listOf("Crown jewels"), rows(query(text = "CROWNER")).map { it.entry.title })
        assertEquals(listOf("Crown jewels"), rows(query(text = crown.value)).map { it.entry.title })
    }

    @Test
    fun `chips narrow by source and by game`() = runTest {
        seed()

        assertEquals(listOf(PuzzleSource.Friend), rows(query(sources = setOf(PuzzleSource.Friend))).map { it.entry.source })
        assertEquals(listOf("Beta #2"), rows(query(games = setOf(beta))).map { it.entry.title })
        assertTrue(rows(query(sources = setOf(PuzzleSource.PastDay), games = setOf(beta), text = "alpha")).isEmpty())
    }

    @Test
    fun `a new completion adds its row without collecting again`() = runTest {
        seed()
        val latest = MutableStateFlow<PickerResult?>(null)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { observe(flowOf(query()), flowOf(1)).collect { latest.value = it } }
        advanceUntilIdle()
        assertEquals(4, checkNotNull(latest.value).rows.size)

        completions.setRecords(stored + solve(beta, daily(9), atMs = 9_000))
        advanceUntilIdle()
        job.cancel()

        assertEquals("Beta #9", checkNotNull(latest.value).rows.first().entry.title)
    }

    @Test
    fun `the result is never offline and has no more days`() = runTest {
        seed()
        val latest = MutableStateFlow<PickerResult?>(null)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { observe(flowOf(query()), flowOf(1)).collect { latest.value = it } }
        advanceUntilIdle()
        job.cancel()

        assertFalse(checkNotNull(latest.value).offline)
        assertFalse(checkNotNull(latest.value).moreDays)
    }
}
