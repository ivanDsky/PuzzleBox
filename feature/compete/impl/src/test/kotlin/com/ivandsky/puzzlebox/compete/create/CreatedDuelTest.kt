package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.duel.CompeteTime
import com.ivandsky.puzzlebox.compete.share.createdInviteText
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sender's invite text: what [CreatedDuel] carries from Create to the text the share sheet shows. */
class CreatedDuelTest {

    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val crown = ShareCode.parse("B8C3DF")!!
    private val unreadable = ShareCode.parse("D5F7GH")!!
    private val received = FakeReceivedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository()
    private var readShared: SharedPuzzleRepository = shared

    private val legs = listOf(
        LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 9), 1),
        LegRef.Custom(TestGames.beta, crown),
        LegRef.Custom(TestGames.alpha, unreadable),
    )
    private val created = CreatedDuel(unreadable, listOf("Alpha", "Beta", "Alpha"), 24, legs)
    private val inOrder = listOf("Alpha", "\"Crown jewels\"", "Alpha · ${unreadable.display}")

    private fun titles() = ObserveLegTitles(received, FakePublishedPuzzleRepository(), readShared)

    private suspend fun crownIsSaved() = received.upsert(
        ReceivedPuzzle(
            code = crown, gameId = TestGames.beta, variant = TestGames.variant(TestGames.beta), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "Crown jewels", authorName = "Cleo", note = "", authorTimeMs = 1_000L,
            authorHints = 0, authorUid = "cleo", openedAt = now, unpublished = false, postState = PostState.Pending,
            postAttempts = 0, difficulty = null,
        ),
    )

    @Test
    fun `the share text lists the legs in order, a custom one by its quoted title, else game and code`() = runTest {
        crownIsSaved()

        val games = created.inviteGames(titles())
        val text = createdInviteText("Ann", created.puzzles.size, games, created.deadlineHours, created.code, CompeteTime(Clock.fixed(now, ZoneOffset.UTC), Locale.US))

        assertEquals(inOrder, games)
        assertEquals(
            "Ann challenges you: best of 3 — Alpha, \"Crown jewels\", Alpha · ${unreadable.display} · ends Tue 10:00",
            text.lines().first(),
        )
    }

    @Test
    fun `a public read that never answers does not hold the share sheet, the code shows instead`() = runTest {
        crownIsSaved()
        readShared = object : SharedPuzzleRepository by shared {
            override suspend fun fetch(code: ShareCode): SharedFetchResult = awaitCancellation()
        }

        val games = created.inviteGames(titles())

        assertEquals(inOrder, games)
        assertEquals(TITLES_WAIT_MS, testScheduler.currentTime)
    }

    @Test
    fun `without a lineup the games are listed once each as before`() = runTest {
        val flagOff = CreatedDuel(unreadable, listOf("Alpha", "Beta", "Alpha"), 24)

        assertEquals(listOf("Alpha", "Beta"), flagOff.inviteGames(titles()))
    }
}
