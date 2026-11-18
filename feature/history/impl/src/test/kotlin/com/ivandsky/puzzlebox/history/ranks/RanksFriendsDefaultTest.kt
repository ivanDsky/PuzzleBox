package com.ivandsky.puzzlebox.history.ranks

import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** F1: the hub lands on the player's default scope, Friends once they have a friend and until they switch it; a restored selection still wins; a Friends board of the viewer alone says how to fill it. */
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
    fun `with the default on Friends and a friend the hub and a link both land on Friends`() = runTest {
        val hub = hub(friendsDefault = true)
        hub.befriend("maya")

        assertEquals(RankScope.Friends, hub.viewModel().selection.value.scope)
        assertEquals(RankScope.Friends, hub.viewModel(opening = link).selection.value.scope)
    }

    @Test
    fun `with the default on Friends but no friends the hub lands on Global`() {
        val hub = hub(friendsDefault = true)

        assertEquals(RankScope.Global, hub.viewModel().selection.value.scope)
        assertEquals(RankScope.Global, hub.viewModel(opening = link).selection.value.scope)
    }

    @Test
    fun `with the default on Friends and signed out the hub lands on Global`() {
        val hub = RanksTestHub(FakeLeaderboardRepository(), identity = null, friendsDefault = true)

        assertEquals(RankScope.Global, hub.viewModel().selection.value.scope)
    }

    @Test
    fun `a scope picked before the landing arrives is kept`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val hub = RanksTestHub(FakeLeaderboardRepository(), friendsDefault = true, storedDefaultGate = gate)
        val viewModel = hub.viewModel()

        viewModel.setScope(RankScope.Friends)
        gate.complete(Unit)

        assertEquals(RankScope.Friends, viewModel.selection.value.scope)
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
    fun `with a stored Global no Friends state is shown before the first loaded one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val hub = RanksTestHub(
            FakeLeaderboardRepository(mapOf(LeaderboardRef("zip", today) to listOf(entry("u1", 70_000)))),
            friendsDefault = false,
            storedDefaultGate = gate,
        )
        val viewModel = hub.viewModel(opening = link)
        val seen = mutableListOf<RankScope>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { if (it.board.state !is DailyBoardState.Loading) seen += it.scope }
        }

        assertTrue("held in loading while the default is unread", seen.isEmpty())
        gate.complete(Unit)
        withTimeout(5_000) { viewModel.uiState.first { it.board.state is DailyBoardState.Ranked } }
        collecting.cancel()

        assertEquals(RankScope.Global, seen.first())
        assertFalse(RankScope.Friends in seen)
    }

    @Test
    fun `a restored Global selection loads straight into Global with a Friends default`() = runTest {
        val handle = SavedStateHandle()
        RanksSelectionSaver(handle).save(RanksSelection(scope = RankScope.Global))
        val viewModel = hub(friendsDefault = true).viewModel(savedState = handle)

        val loaded = withTimeout(5_000) { viewModel.uiState.first { it.board.state !is DailyBoardState.Loading } }

        assertEquals(RankScope.Global, loaded.scope)
    }

    @Test
    fun `a reselect reset lands on the default as it stands now`() = runTest {
        val hub = hub(friendsDefault = true)
        hub.befriend("maya")
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
        viewModel.setScope(RankScope.Friends)

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
