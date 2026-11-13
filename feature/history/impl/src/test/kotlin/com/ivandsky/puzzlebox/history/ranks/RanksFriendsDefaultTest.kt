package com.ivandsky.puzzlebox.history.ranks

import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F1: the hub lands on the player's default scope, Friends until they switch it; a restored selection still wins; a Friends board of the viewer alone says how to fill it. */
class RanksFriendsDefaultTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 13)

    private fun entry(uid: String, timeMs: Long) = LeaderboardEntry(uid, uid, "zip/$today/1", timeMs, 0, 1_700_000_000_000)

    private fun hub(friendsDefault: Boolean) = RanksTestHub(
        FakeLeaderboardRepository(mapOf(LeaderboardRef("zip", today) to listOf(entry("u1", 70_000), entry("maya", 60_000)))),
        friendsDefault = friendsDefault,
    )

    private val link = RanksSelection.opening(RanksBoard.Game(ZIP), null)

    @Test
    fun `with the default on Friends the hub and a link both land on Friends`() {
        val hub = hub(friendsDefault = true)

        assertEquals(RankScope.Friends, hub.viewModel().selection.value.scope)
        assertEquals(RankScope.Friends, hub.viewModel(opening = link).selection.value.scope)
    }

    @Test
    fun `with the default on Global the hub and a link both land on Global`() {
        val hub = hub(friendsDefault = false)

        assertEquals(RankScope.Global, hub.viewModel().selection.value.scope)
        assertEquals(RankScope.Global, hub.viewModel(opening = link).selection.value.scope)
    }

    @Test
    fun `a restored selection keeps its own scope over the default`() {
        val handle = SavedStateHandle()
        RanksSelectionSaver(handle).save(RanksSelection(scope = RankScope.Global))

        val viewModel = hub(friendsDefault = true).viewModel(savedState = handle)

        assertEquals(RankScope.Global, viewModel.selection.value.scope)
    }

    @Test
    fun `a reselect reset lands on the default as it stands now`() = runTest {
        val hub = hub(friendsDefault = true)
        val viewModel = hub.viewModel()
        viewModel.setScope(RankScope.Global)

        viewModel.resetSelection()
        assertEquals(RankScope.Friends, viewModel.selection.value.scope)

        hub.preferences.setRanksFriendsDefault(false)
        viewModel.resetSelection()
        assertEquals(RankScope.Global, viewModel.selection.value.scope)
    }

    @Test
    fun `a Friends board with only the viewer on it is marked alone, and not once a friend is on it`() = runTest {
        val hub = hub(friendsDefault = true)
        val viewModel = hub.viewModel(opening = link)

        val alone = withTimeout(5_000) { viewModel.uiState.first { it.board.state is DailyBoardState.Ranked }.board }
        assertEquals(listOf("u1"), (alone.state as DailyBoardState.Ranked).rows.map { it.name })
        assertTrue(alone.friendsAlone)

        hub.befriend("maya")
        val filled = withTimeout(5_000) { viewModel.uiState.first { (it.board.state as? DailyBoardState.Ranked)?.rows?.size == 2 }.board }
        assertFalse(filled.friendsAlone)
    }

    @Test
    fun `the Global board is never marked alone`() = runTest {
        val viewModel = hub(friendsDefault = false).viewModel(opening = link)

        val board = withTimeout(5_000) { viewModel.uiState.first { it.board.state is DailyBoardState.Ranked }.board }
        assertFalse(board.friendsAlone)
    }
}
