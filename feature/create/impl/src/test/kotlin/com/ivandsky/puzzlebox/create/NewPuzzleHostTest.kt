package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.GameEditor
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * [NewPuzzleHost]: a surface that starts puzzles with a button of its own. The host hands its content an
 * `open` action for the New puzzle sheet, reports each stored draft once, and tells a [CustomBody] inside it to drop
 * its New puzzle row and its own sheets, lifting its list and Undo snackbar by the host's clearance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class NewPuzzleHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = FakeGameEditor()
    private val games = GameRegistry(listOf(pluginOf(editor)))
    private val drafts = FakeDraftRepository()
    private val openedDrafts = mutableListOf<String>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `open shows the New puzzle sheet and a pick reports its draft once, with no list on screen`() {
        showHost { open -> Button(onClick = { open(null) }, modifier = Modifier.testTag(OPEN)) { Text("Create") } }

        assertFalse(composeRule.exists(SHEET))
        composeRule.onNodeWithTag(OPEN).performClick()
        composeRule.onNodeWithTag(SHEET).assertExists()
        composeRule.onNodeWithTag(GAME).performClick()

        awaitTrue { openedDrafts.isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals(listOf("draft-1"), openedDrafts)
        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertFalse(composeRule.exists(SHEET))
    }

    @Test
    fun `a Custom list inside the host has no New puzzle row, and open shows one sheet that reports once`() {
        store("plain")
        showHost { open ->
            CustomBody(rememberLazyListState(), onOpenDraft = { openedDrafts += it }, onOpenPublished = {})
            Button(onClick = { open(null) }, modifier = Modifier.testTag(OPEN)) { Text("Create") }
        }
        awaitTag(DRAFT_ROW)

        assertFalse(composeRule.exists(NEW))
        composeRule.onNodeWithTag(OPEN).performClick()
        composeRule.onNodeWithTag(SHEET).assertExists()
        assertEquals(1, composeRule.count(SHEET))
        composeRule.onNodeWithTag(GAME).performClick()

        awaitTrue { openedDrafts.isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals(listOf("draft-1"), openedDrafts)
    }

    @Test
    fun `the first-time state inside the host still starts a draft and opens one Import sheet`() {
        showHost { CustomBody(rememberLazyListState(), onOpenDraft = { openedDrafts += it }, onOpenPublished = {}) }
        awaitTag(EMPTY)

        composeRule.onNodeWithTag(EMPTY_IMPORT).performClick()

        awaitTag(IMPORT_SHEET)
        assertEquals(1, composeRule.count(IMPORT_SHEET))
    }

    @Test
    fun `open with a game starts a draft of that game at once, with no sheet`() {
        showHost { open -> Button(onClick = { open(editor.gameId) }, modifier = Modifier.testTag(OPEN)) { Text("Make") } }

        composeRule.onNodeWithTag(OPEN).performClick()

        awaitTrue { openedDrafts.isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals(listOf("draft-1"), openedDrafts)
        assertEquals(editor.gameId, drafts.rows.getValue("draft-1").gameId)
        assertEquals(editor.variants.first(), drafts.rows.getValue("draft-1").variant)
        assertFalse(composeRule.exists(SHEET))
    }

    @Test
    fun `a Custom list outside any host keeps its New puzzle row`() {
        store("plain")
        showBare()
        awaitTag(DRAFT_ROW)

        assertTrue(composeRule.exists(NEW))
    }

    @Test
    fun `the hosted list ends clear of the host's clearance, and the Undo snackbar sits above it`() {
        (1..8).forEach { store("d$it") }
        showHost { CustomBody(rememberLazyListState(), onOpenDraft = {}, onOpenPublished = {}) }
        awaitTag(DRAFT_ROW)

        composeRule.onNodeWithTag(LIST).performScrollToIndex(8)
        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag(LIST).getUnclippedBoundsInRoot()
        val last = composeRule.onAllNodes(hasTestTag(DRAFT_ROW)).fetchSemanticsNodes().maxBy { it.boundsInRoot.bottom }
        val density = composeRule.density.density
        assertTrue(
            "the last row ends ${last.boundsInRoot.bottom} px, ${CLEARANCE.value * density} px above the list's end ${list.bottom.value * density} px",
            last.boundsInRoot.bottom <= (list.bottom - CLEARANCE).value * density + 1f,
        )
    }

    private fun showHost(content: @androidx.compose.runtime.Composable (open: (GameId?) -> Unit) -> Unit) {
        startKoin()
        composeRule.setContent {
            PuzzleBoxTheme {
                Box {
                    NewPuzzleHost(clearance = CLEARANCE, onOpenDraft = { openedDrafts += it }) { open -> content(open) }
                }
            }
        }
    }

    private fun showBare() {
        startKoin()
        composeRule.setContent {
            PuzzleBoxTheme {
                CustomBody(rememberLazyListState(), onOpenDraft = { openedDrafts += it }, onOpenPublished = {})
            }
        }
    }

    private fun startKoin() {
        var ids = 0
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            GameEditorRegistry(listOf(FakeGameEditorUi())),
                            GamePlayRegistry(emptyList()),
                            drafts,
                            FakePublishedPuzzleRepository(),
                            CreateDraft(games, drafts, newId = { "draft-${++ids}" }, clock = { NOW }),
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
    }

    private fun store(id: String) = runBlocking {
        drafts.upsert(
            Draft(
                id = id,
                gameId = editor.gameId,
                variant = editor.variants.first(),
                draftFormatVersion = editor.draftFormatVersion,
                blob = byteArrayOf(1, 2),
                title = "Title $id",
                updatedAt = NOW,
                testSolve = null,
                reservedCode = null,
                imported = false,
                authorName = "Ivan",
                note = "",
            ),
        )
    }

    private fun pluginOf(editor: GameEditor<TestDraft>): GamePlugin<*, *, *> {
        val base = TestGames.plugin(editor.gameId)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
            editor = editor,
        )
    }

    private fun ComposeTestRule.count(tag: String): Int =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun ComposeTestRule.exists(tag: String): Boolean = count(tag) > 0

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
        val CLEARANCE = 88.dp
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val OPEN = "test.open"
        const val NEW = "custom.new"
        const val LIST = "custom.list"
        const val SHEET = "create.new.sheet"
        const val GAME = "create.new.game.fake"
        const val IMPORT_SHEET = "create.import.sheet"
        const val EMPTY = "create.empty"
        const val EMPTY_IMPORT = "create.empty.import"
        const val DRAFT_ROW = "create.draft.row"
    }
}
