package com.ivandsky.puzzlebox.history

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History's first-restore state (spec A34): while the cloud's first restore runs the empty
 * list says history is coming back; any other state, or no [CloudSync] at all, keeps today's
 * first-time empty copy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HistoryRestoringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(cloud: CloudSync?) {
        val registry = HistoryTestSupport.registry
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository(emptyList())
        val observeGameHistory = ObserveGameHistory(registry, daily, completions)
        val puzzleDay = GetPuzzleDay(registry, Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC))
        val viewModel = HistoryViewModel(
            observeGameHistory = observeGameHistory,
            observeGameStats = ObserveGameStats(registry, observeGameHistory, ComputeStreak(puzzleDay)),
            registry = registry,
            playRegistry = GamePlayRegistry(emptyList()),
            puzzleDay = puzzleDay,
            loadReplay = HistoryTestSupport.loadReplay(FakeReleaseFlags(), registry, daily, completions),
            cloudSync = cloud,
        )
        composeRule.setContent {
            PuzzleBoxTheme {
                HistoryRoute(
                    key = HistoryKey(),
                    layout = HistoryLayout.SinglePane,
                    onBack = null,
                    onPlayAgain = { _, _ -> },
                    onOpenReplay = {},
                    onOpenCompare = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoTag(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `first restore shows the restoring state instead of the empty one`() {
        setContent(FakeCloudSync(CloudSyncState.Syncing(firstRestore = true)))

        awaitTag("history.restoring")
        assertNoTag("history.empty")
    }

    @Test
    fun `restoring gives way to the empty state when the restore ends with nothing`() {
        val cloud = FakeCloudSync(CloudSyncState.Syncing(firstRestore = true))
        setContent(cloud)
        awaitTag("history.restoring")

        cloud.state.value = CloudSyncState.Synced(atMs = 1L)

        awaitTag("history.empty")
        assertNoTag("history.restoring")
    }

    @Test
    fun `an ordinary sync pass keeps the empty state`() {
        setContent(FakeCloudSync(CloudSyncState.Syncing(firstRestore = false)))

        awaitTag("history.empty")
        assertNoTag("history.restoring")
    }

    @Test
    fun `no cloud sync bound keeps the empty state`() {
        setContent(null)

        awaitTag("history.empty")
        assertNoTag("history.restoring")
    }
}
