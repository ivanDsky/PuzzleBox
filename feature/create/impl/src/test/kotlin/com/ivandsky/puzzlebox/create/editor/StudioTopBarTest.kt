package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The Studio top bar (02 handoff "Top bar", "Overflow menu"; spec A25): Rename's Save is
 * enabled only for a 1–60 character title and stores it; the overflow's Duplicate copies
 * the draft through CreateDraftCopy and opens the copy in place of the source; Delete
 * returns to the Create root, whose snackbar Undo puts back an equal draft.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioTopBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = FakeGameEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val stored = storedRow(editor.draft(marks = 1).encode())
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `save is disabled for an empty title and for 61 characters and enabled at 60`() {
        open()
        openRename()

        composeRule.onNodeWithTag(FIELD).performTextReplacement("")
        composeRule.onNodeWithTag(SAVE).assertIsNotEnabled()

        composeRule.onNodeWithTag(FIELD).performTextReplacement("x".repeat(Draft.TITLE_MAX + 1))
        composeRule.onNodeWithTag(SAVE).assertIsNotEnabled()

        composeRule.onNodeWithTag(FIELD).performTextReplacement("x".repeat(Draft.TITLE_MAX))
        composeRule.onNodeWithTag(SAVE).assertIsEnabled()
    }

    @Test
    fun `saving a title stores it on the draft`() {
        open()
        openRename()

        composeRule.onNodeWithTag(FIELD).performTextReplacement("Castle")
        composeRule.onNodeWithTag(SAVE).performClick()

        awaitTrue { drafts.rows[DRAFT_ID]?.title == "Castle" }
        composeRule.onNodeWithTag(RENAME_SHEET).assertDoesNotExist()
    }

    @Test
    fun `the overflow's rename item opens the rename sheet`() {
        open()

        composeRule.onNodeWithTag(MORE).performClick()
        composeRule.onNodeWithTag(MENU_RENAME).performClick()

        composeRule.onNodeWithTag(RENAME_SHEET).assertExists()
    }

    @Test
    fun `duplicate copies the draft through CreateDraftCopy and opens the copy's editor key`() {
        open()

        composeRule.onNodeWithTag(MORE).performClick()
        composeRule.onNodeWithTag(MENU_DUPLICATE).performClick()

        awaitTrue { navigator.state.currentStack.last() == EditorKey(COPY_ID) }
        assertEquals(listOf(CreateKey, EditorKey(COPY_ID)), navigator.state.currentStack)
        assertEquals(stored.copy(id = COPY_ID, title = "Untitled 1 2"), drafts.rows[COPY_ID])
    }

    @Test
    fun `delete returns to Create, whose snackbar Undo restores an equal draft`() {
        open()

        composeRule.onNodeWithTag(MORE).performClick()
        composeRule.onNodeWithTag(MENU_DELETE).performClick()

        awaitTrue { navigator.state.currentStack == listOf(CreateKey) && drafts.rows[DRAFT_ID] == null }
        val undo = hasAnyAncestor(hasTestTag(SNACKBAR)) and hasClickAction()
        awaitTrue { composeRule.onAllNodes(undo).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNode(undo).performClick()

        awaitTrue { drafts.rows[DRAFT_ID] != null }
        assertEquals(stored, drafts.rows[DRAFT_ID])
    }

    private fun openRename() {
        composeRule.onNodeWithTag(TITLE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RENAME_SHEET).assertExists()
    }

    private fun open() {
        runBlocking { drafts.upsert(stored) }
        val games = GameRegistry(listOf(withEditor(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        val copyDraft = CreateDraftCopy(drafts, newId = { COPY_ID }, clock = { NOW })
        startKoin {
            // Every Studio test runs with friend sharing on (ADR-0022).
            modules(module { single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.FriendSharing) } })
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        EditorViewModel(key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(), saves = CoroutineScope(SupervisorJob() + Dispatchers.Main), clock = { NOW }, createShared = signedOutPublishing(drafts), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(), idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing())
                    }
                    viewModel {
                        CreateViewModel(
                            games,
                            editors,
                            GamePlayRegistry(emptyList()),
                            drafts,
                            FakePublishedPuzzleRepository(),
                            CreateDraft(games, drafts, newId = { "new-1" }, clock = { NOW }),
                            copyDraft,
                            DefaultDispatcherProvider(),
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            releaseFlags = get(),
                            clock = { NOW },
                        )
                    }
                },
            )
        }
        val entries = entryProvider<NavKey> {
            createEntry(navigator)
            editorEntry(navigator, PlayLayout.SinglePane)
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                val top = navigator.state.currentStack.last()
                key(top) { entries(top).Content() }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { ui.controllers.isNotEmpty() }
    }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val COPY_ID = "copy-1"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val TITLE = "create.studio.title"
        const val MORE = "create.studio.more"
        const val MENU_RENAME = "create.studio.rename"
        const val MENU_DUPLICATE = "create.studio.duplicate"
        const val MENU_DELETE = "create.studio.delete"
        const val RENAME_SHEET = "create.rename.sheet"
        const val FIELD = "create.rename.field"
        const val SAVE = "create.rename.save"
        const val SNACKBAR = "create.snackbar"

        fun storedRow(blob: ByteArray) = Draft(
            id = DRAFT_ID,
            gameId = FakeGameEditor().gameId,
            variant = FakeGameEditor().variants.first(),
            draftFormatVersion = 1,
            blob = blob,
            title = "Untitled 1",
            updatedAt = NOW,
            testSolve = null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )

        fun withEditor(editor: FakeGameEditor): GamePlugin<*, *, *> {
            val base = TestGames.plugin(editor.gameId)
            return GamePlugin(
                definition = base.definition,
                rules = base.rules,
                session = base.session,
                score = base.score,
                stateOf = base.stateOf,
                initialSession = base.initialSession,
                refOf = base.refOf,
                replaySession = base.replaySession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}
