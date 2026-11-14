package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Challenge
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Watch
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every gate of what the viewer may do with another player: the tier, the failed lookup and the spoiler gate. */
class GetPlayerActionsTest {

    private val day = LocalDate.parse("2026-11-08")
    private val board = LeaderboardRef(TestGames.alpha.value, day)
    private val visibility = FakeVisibilityRepository()

    @Test
    fun `a hidden or board player cannot be challenged or watched`() = runTest {
        for (tier in listOf(VisibilityTier.Hidden, VisibilityTier.Board)) {
            visibility.putTier("them", tier)
            assertEquals(PlayerActions(Challenge.Private, Watch.Private), actions(solved = true)("them", board))
        }
    }

    @Test
    fun `a duel player can be challenged but not watched`() = runTest {
        visibility.putTier("them", VisibilityTier.Duel)

        assertEquals(PlayerActions(Challenge.Allowed, Watch.Private), actions(solved = true)("them", board))
    }

    @Test
    fun `a replays player is watchable once the viewer solved that puzzle`() = runTest {
        visibility.putTier("them", VisibilityTier.Replays)

        assertEquals(PlayerActions(Challenge.Allowed, Watch.Allowed), actions(solved = true)("them", board))
    }

    @Test
    fun `watching waits for the viewer's own solve of that game and day`() = runTest {
        visibility.putTier("them", VisibilityTier.Replays)

        assertEquals(Watch.SolveFirst, actions(solved = false)("them", board).watch)
        assertEquals(Watch.SolveFirst, actions(solved = true)("them", board.copy(day = day.plusDays(1))).watch)
        assertEquals(Watch.SolveFirst, actions(solved = true)("them", board.copy(gameId = "beta")).watch)
    }

    @Test
    fun `a LinkedIn import is the viewer's solve`() = runTest {
        visibility.putTier("them", VisibilityTier.Replays)

        assertEquals(Watch.Allowed, actions(solved = true, source = CompletionSource.LinkedIn)("them", board).watch)
    }

    @Test
    fun `a board with no single puzzle has nothing to watch`() = runTest {
        visibility.putTier("them", VisibilityTier.Replays)

        assertEquals(PlayerActions(Challenge.Allowed, Watch.Unavailable), actions(solved = true)("them", null))
    }

    @Test
    fun `a failed lookup fails closed`() = runTest {
        visibility.failingUids += "them"

        assertEquals(PlayerActions(Challenge.Private, Watch.Private), actions(solved = true)("them", board))
    }

    @Test
    fun `one call is one tier read`() = runTest {
        actions(solved = true)("them", board)

        assertEquals(listOf("them"), visibility.lookupCalls)
    }

    private fun actions(
        solved: Boolean,
        source: CompletionSource = CompletionSource.App,
    ): GetPlayerActions {
        val records = if (solved) listOf(record(source)) else emptyList()
        return GetPlayerActions(visibility, FakeCompletionRepository(records))
    }

    private fun record(source: CompletionSource) = CompletionRecord(
        puzzleRef = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(day, 1)),
        time = 1_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = true,
        source = source,
    )
}
