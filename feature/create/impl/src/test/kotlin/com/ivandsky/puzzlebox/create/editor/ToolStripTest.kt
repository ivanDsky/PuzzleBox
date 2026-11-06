package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeEditorController
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * The Studio tool strip (spec S3, S19): Undo and Redo are enabled only when a step exists,
 * and each tap moves exactly one edit, through the VM's EditHistory into the board's
 * controller and the stored row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolStripTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = FakeGameEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))

    @Before
    fun setUp() {
        runBlocking { drafts.upsert(storedRow(editor.draft(0).encode())) }
        val games = GameRegistry(listOf(withEditor(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
                        EditorViewModel(key, drafts, games, editors, copyDraft, DefaultDispatcherProvider(), clock = { NOW })
                    }
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { editorEntry(navigator, PlayLayout.SinglePane) }(EditorKey(DRAFT_ID)).Content()
            }
        }
        composeRule.waitUntil { ui.controllers.isNotEmpty() }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a fresh draft has nothing to undo or redo`() {
        composeRule.onNodeWithTag(STRIP).assertExists()
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        composeRule.onNodeWithTag(REDO).assertIsNotEnabled()
    }

    @Test
    fun `two paints, two undos and one redo leave the one-paint draft`() {
        val onePaint = editor.draft(0).apply(Mark)
        val twoPaints = onePaint.apply(Mark)
        paint(onePaint)
        paint(twoPaints)
        composeRule.onNodeWithTag(UNDO).assertIsEnabled()
        composeRule.onNodeWithTag(REDO).assertIsNotEnabled()

        composeRule.onNodeWithTag(UNDO).performClick()
        composeRule.onNodeWithTag(UNDO).performClick()
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        composeRule.onNodeWithTag(REDO).assertIsEnabled()
        assertEquals(TestDraft(0), controller().draft.value.state.draft)

        composeRule.onNodeWithTag(REDO).performClick()
        composeRule.onNodeWithTag(UNDO).assertIsEnabled()
        composeRule.onNodeWithTag(REDO).assertIsEnabled()
        assertEquals(onePaint.state.draft, controller().draft.value.state.draft)
        assertEquals(3, controller().adopted.size)
        awaitStored { drafts.rows.getValue(DRAFT_ID).blob.contentEquals(onePaint.encode()) }
        assertTrue(drafts.rows.getValue(DRAFT_ID).blob.contentEquals(onePaint.encode()))
    }

    private fun controller(): FakeEditorController = ui.controllers.single()

    /** Waits for the autosave's 300 ms window to store the row, running the paused main looper's clock. */
    private fun awaitStored(condition: () -> Boolean) {
        composeRule.waitUntil {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    private fun paint(next: EditorDraft<TestDraft>) {
        composeRule.runOnIdle { controller().edit(next) }
        composeRule.waitForIdle()
    }

    /** The author's paint on the fake board: one more mark. */
    private object Mark : Command<TestDraft> {
        override fun apply(state: TestDraft): TestDraft = state.copy(marks = state.marks + 1)
        override fun revert(state: TestDraft): TestDraft = state.copy(marks = state.marks - 1)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val FRAME_MS = 16L
        const val DRAFT_ID = "draft-1"
        const val STRIP = "create.tool.strip"
        const val UNDO = "create.tool.undo"
        const val REDO = "create.tool.redo"

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
