package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Create's first-restore state (spec A34): while the cloud's first restore runs the first-time
 * empty state says the drafts are coming back; any other state, or no [CloudSync] at all, keeps
 * today's first-time copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateRestoringTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun show(cloud: CloudSync?) {
        val games = GameRegistry(listOf(TestGames.plugin(GameId("fake"))))
        val drafts = FakeDraftRepository()
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            GameEditorRegistry(emptyList()),
                            GamePlayRegistry(emptyList()),
                            drafts,
                            FakePublishedPuzzleRepository(),
                            CreateDraft(games, drafts, newId = { "new" }, clock = { NOW }),
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = FakeSharedTextStore(),
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            releaseFlags = FakeReleaseFlags(),
                            clock = { NOW },
                            cloudSync = cloud,
                        )
                    }
                },
            )
        }
        val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { createEntry(navigator) }(CreateKey).Content()
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
        show(FakeCloudSync(CloudSyncState.Syncing(firstRestore = true)))

        awaitTag(RESTORING)
        assertNoTag(EMPTY)
    }

    @Test
    fun `restoring gives way to the empty state when the restore ends with nothing`() {
        val cloud = FakeCloudSync(CloudSyncState.Syncing(firstRestore = true))
        show(cloud)
        awaitTag(RESTORING)

        cloud.state.value = CloudSyncState.Synced(atMs = 1L)

        awaitTag(EMPTY)
        assertNoTag(RESTORING)
    }

    @Test
    fun `an ordinary sync pass keeps the empty state`() {
        show(FakeCloudSync(CloudSyncState.Syncing(firstRestore = false)))

        awaitTag(EMPTY)
        assertNoTag(RESTORING)
    }

    @Test
    fun `no cloud sync bound keeps the empty state`() {
        show(null)

        awaitTag(EMPTY)
        assertNoTag(RESTORING)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-11T10:00:00Z")
        const val EMPTY = "create.empty"
        const val RESTORING = "create.restoring"
    }
}
