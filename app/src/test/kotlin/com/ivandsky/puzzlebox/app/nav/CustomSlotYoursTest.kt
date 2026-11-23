package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.rememberCustomSlot
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * M12-3: the app's [com.ivandsky.puzzlebox.play.CustomSlot] hands Community a Yours seam, the Custom list: New puzzle,
 * Continue and Published by you, or the first-time empty state when there is nothing; and a summary with the drafts-plus-published count and the first published puzzle's code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", application = DrainTestApplication::class)
class CustomSlotYoursTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val games = GameRegistry(listOf(queensPlugin))
    private val drafts = FakeDraftRepository()
    private val published = FakePublishedPuzzleRepository()
    private val navigator = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `yours shows New puzzle, Continue and Published by you, with no friends and no community`() {
        runBlocking { newDraft("draft-1") }
        publish("K7Q2MX", minutesAgo = 5)
        show()

        awaitTag(PUBLISHED)

        assertTrue(composeRule.exists(NEW))
        assertTrue(composeRule.exists(CONTINUE))
        assertTrue(composeRule.exists(DRAFT_ROW))
        assertTrue(composeRule.exists(PUBLISHED_ROW))
        assertFalse(composeRule.exists(EMPTY))
        assertFalse(composeRule.textShown("From friends"))
        assertFalse(composeRule.textShown("New today"))
    }

    @Test
    fun `yours with nothing made shows the first-time empty state`() {
        show()

        awaitTag(EMPTY)

        assertFalse(composeRule.exists(NEW))
    }

    private suspend fun newDraft(id: String) =
        CreateDraft(games, drafts, newId = { id }, clock = { NOW })(QueensDefinition.id, QueensEditor.variants.first())

    private fun publish(code: String, minutesAgo: Long) = runBlocking {
        published.upsert(
            PublishedPuzzle(
                code = checkNotNull(ShareCode.parse(code)),
                gameId = QueensDefinition.id,
                variant = QueensEditor.variants.first(),
                title = "Title $code",
                authorTimeMs = 102_000L,
                authorHints = 0,
                createdAt = NOW.minusSeconds(minutesAgo * 60),
                unpublished = false,
                solvesCount = null,
                solvesCountAt = null,
            ),
        )
    }

    private fun show() {
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            GameEditorRegistry(listOf(QueensEditorUi())),
                            GamePlayRegistry(emptyList()),
                            drafts,
                            published,
                            CreateDraft(games, drafts, newId = { "draft-new" }, clock = { NOW }),
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = FakeSharedTextStore(),
                            syncSolves = SyncSolvesCounts(published, FakeCustomBoardRepository(), clock = { NOW }),
                            clock = { NOW },
                            cloudSync = FakeCloudSync(),
                        )
                    }
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                val slot = rememberCustomSlot(navigator)
                checkNotNull(slot.yours) { "the app fills the Yours seam" }(rememberLazyListState())
            }
        }
    }

    private fun ComposeTestRule.exists(tag: String): Boolean =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeTestRule.textShown(text: String): Boolean =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun awaitTag(tag: String) = awaitTrue { composeRule.exists(tag) }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-16T10:00:00Z")
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val NEW = "custom.new"
        const val CONTINUE = "custom.continue"
        const val PUBLISHED = "custom.published"
        const val EMPTY = "create.empty"
        const val DRAFT_ROW = "create.draft.row"
        const val PUBLISHED_ROW = "create.published.row"
    }
}
