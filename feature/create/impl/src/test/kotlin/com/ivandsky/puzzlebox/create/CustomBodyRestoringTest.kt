package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
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
 * The Custom list's first-restore state (spec A34): while the cloud's first restore runs the first-time
 * empty state says the drafts are coming back; any other state keeps today's first-time copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class CustomBodyRestoringTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    private val drafts = FakeDraftRepository()
    private val published = FakePublishedPuzzleRepository()

    private fun show(cloud: CloudSync) {
        val games = GameRegistry(listOf(TestGames.plugin(GameId("fake"))))
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            GameEditorRegistry(emptyList()),
                            GamePlayRegistry(emptyList()),
                            drafts,
                            published,
                            CreateDraft(games, drafts, newId = { "new" }, clock = { NOW }),
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = FakeSharedTextStore(),
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            clock = { NOW },
                            cloudSync = cloud,
                        )
                    }
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                CustomBody(
                    listState = rememberLazyListState(),
                    onOpenDraft = {},
                    onOpenPublished = {},
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

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-11T10:00:00Z")
        const val EMPTY = "create.empty"
        const val RESTORING = "create.restoring"
    }
}
