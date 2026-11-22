package com.ivandsky.puzzlebox.app.nav

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.rememberCustomSlot
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * The app's [com.ivandsky.puzzlebox.play.CustomSlot] hands Community a New puzzle seam over create's New puzzle sheet
 * (#2013): the host's `open` shows the sheet, picking a Queens size starts a draft and opens its Studio on the current
 * stack, and the Yours list inside the seam has no New puzzle row of its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", application = DrainTestApplication::class)
class CustomSlotNewPuzzleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val games = GameRegistry(listOf(queensPlugin))
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the slot offers a New puzzle seam`() {
        var seam: Any? = null
        show { slot -> seam = slot.newPuzzle }

        assertNotNull(seam)
    }

    @Test
    fun `open shows the New puzzle sheet, and a Queens size starts a draft and opens its Studio`() {
        show { slot ->
            checkNotNull(slot.newPuzzle) { "the app fills the New puzzle seam" }(88.dp) { open ->
                Button(onClick = { open(null) }, modifier = Modifier.testTag(OPEN)) { Text("Create") }
            }
        }

        composeRule.onNodeWithTag(OPEN).performClick()
        composeRule.onNodeWithTag("${GAME}${QueensDefinition.id.value}").performClick()
        composeRule.onNodeWithTag("${SIZE}${QueensEditor.variants.first().value}").performScrollTo().performClick()

        awaitTrue { navigator.state.currentStack.last() is EditorKey }
        assertEquals(listOf(PlayKey, EditorKey("draft-new")), navigator.state.currentStack)
    }

    @Test
    fun `open with Queens starts a Queens draft and opens its Studio with no sheet`() {
        show { slot ->
            checkNotNull(slot.newPuzzle) { "the app fills the New puzzle seam" }(88.dp) { open ->
                Button(onClick = { open(QueensDefinition.id) }, modifier = Modifier.testTag(OPEN)) { Text("Make") }
            }
        }

        composeRule.onNodeWithTag(OPEN).performClick()

        awaitTrue { navigator.state.currentStack.last() is EditorKey }
        assertEquals(listOf(PlayKey, EditorKey("draft-new")), navigator.state.currentStack)
        assertFalse(composeRule.exists("create.new.sheet"))
    }

    @Test
    fun `the Yours list inside the seam has no New puzzle row`() {
        show { slot ->
            checkNotNull(slot.newPuzzle) { "the app fills the New puzzle seam" }(88.dp) {
                checkNotNull(slot.yours) { "the app fills the Yours seam" }(rememberLazyListState())
            }
        }

        awaitTrue { composeRule.exists(EMPTY) }
        assertFalse(composeRule.exists(NEW))
    }

    private fun show(content: @androidx.compose.runtime.Composable (com.ivandsky.puzzlebox.play.CustomSlot) -> Unit) {
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            GameEditorRegistry(listOf(QueensEditorUi())),
                            GamePlayRegistry(emptyList()),
                            drafts,
                            FakePublishedPuzzleRepository(),
                            CreateDraft(games, drafts, newId = { "draft-new" }, clock = { NOW }),
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = FakeSharedTextStore(),
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            clock = { NOW },
                            cloudSync = FakeCloudSync(),
                        )
                    }
                },
            )
        }
        composeRule.setContent { PuzzleBoxTheme { content(rememberCustomSlot(navigator)) } }
    }

    private fun ComposeTestRule.exists(tag: String): Boolean =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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
        const val OPEN = "test.open"
        const val NEW = "custom.new"
        const val EMPTY = "create.empty"
        const val GAME = "create.new.game."
        const val SIZE = "create.new.size."
    }
}
