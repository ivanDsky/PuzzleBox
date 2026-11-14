package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** F7: a link into the hub (game page row, Result chip, Today rank row) lands on a board that answers, never an endless Loading. */
class RanksLinkLandingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val yesterday = today.minusDays(1)

    private fun entry(day: LocalDate, uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid, "zip/$day/1", timeMs, 0, 1_700_000_000_000)

    private fun hub(friendsDefault: Boolean) = RanksTestHub(
        FakeLeaderboardRepository(
            mapOf(
                LeaderboardRef("zip", today) to listOf(entry(today, "u1", 70_000), entry(today, "maya", 60_000)),
                LeaderboardRef("zip", yesterday) to listOf(entry(yesterday, "u1", 80_000), entry(yesterday, "maya", 65_000)),
            ),
        ),
        friendsDefault = friendsDefault,
    )

    private suspend fun landing(day: LocalDate?, linkedIn: Boolean = false, friends: Boolean = false): DailyBoardState = withTimeout(5_000) {
        val hub = hub(friends)
        hub.daily.setHistory(ZIP, listOf(HistoryTestSupport.entry(today), HistoryTestSupport.entry(yesterday)))
        hub.completions.setRecords(listOf(HistoryTestSupport.record(today), HistoryTestSupport.record(yesterday)))
        val opening = RanksSelection.opening(RanksBoard.Game(ZIP), day).landing(rankBoards(hub.registry), hub.calendar::today)
        hub.viewModel(linkedIn = linkedIn, opening = opening).uiState.first { it.board.state != DailyBoardState.Loading }.board.state
    }

    @Test
    fun `a solved game's link in Friends scope with LinkedIn on lands on a board`() = runTest {
        assertEquals(listOf("u1"), (landing(null, linkedIn = true, friends = true) as DailyBoardState.Ranked).rows.map { it.name })
        assertEquals(listOf("u1"), (landing(yesterday, linkedIn = true, friends = true) as DailyBoardState.Ranked).rows.map { it.name })
    }

    @Test
    fun `a game's link with no day lands on its today board`() = runTest {
        assertEquals(listOf("maya", "u1"), (landing(null) as DailyBoardState.Ranked).rows.map { it.name })
    }

    @Test
    fun `a game's link at today's day lands on its today board`() = runTest {
        assertEquals(listOf("maya", "u1"), (landing(today) as DailyBoardState.Ranked).rows.map { it.name })
    }

    @Test
    fun `a game's link at a past day lands on that day's board`() = runTest {
        assertEquals(listOf("maya", "u1"), (landing(yesterday) as DailyBoardState.Ranked).rows.map { it.name })
    }
}
