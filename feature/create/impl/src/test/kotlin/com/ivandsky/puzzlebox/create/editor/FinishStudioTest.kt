package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeDraftCompleter
import com.ivandsky.puzzlebox.create.FakeEditorController
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
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
 * Finish for me in the Studio route (E1): the tool shows only for an editor
 * with a completer, starts disabled, enables with the board, asks for a level, completes the board
 * as one undoable step and says the failure message when the completer gives up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FinishStudioTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = FakeDraftCompleter()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(StudioRootKey, listOf(listOf(StudioRootKey, EditorKey(DRAFT_ID)))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the tool is absent for a game without a completer`() {
        open(game = FakeGameEditor())

        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        composeRule.onAllNodes(hasTestTag(FINISH)).assertCountEquals(0)
    }

    @Test
    fun `it starts disabled on a blank board`() {
        open(marks = 0)

        composeRule.onNodeWithTag(FINISH).assertIsNotEnabled()
    }

    @Test
    fun `it enables with the board, a level pick completes it, and Undo returns the pieces`() {
        open(marks = 2)
        awaitEnabled()

        composeRule.onNodeWithTag(FINISH).performClick()
        assertEquals("the tool asks for a level first", emptyList<Any>(), controller().adopted)
        composeRule.onNodeWithTag(HARD).performClick()
        await { controller().adopted.isNotEmpty() }
        assertEquals(listOf<Difficulty.Level?>(Difficulty.Level.Hard), editor.targets)

        val completion = controller().adopted.single().state.draft as TestDraft
        assertEquals(2 + Random(SEED).nextInt(1, 10), completion.marks)
        composeRule.onNodeWithTag(UNDO).performClick()
        composeRule.waitForIdle()
        assertEquals(TestDraft(2), controller().adopted.last().state.draft)
    }

    @Test
    fun `a completer that gives up shows the message and leaves the board`() {
        editor.completionOf = { _, _, _ -> Completion.GaveUp }
        open(marks = 2)
        awaitEnabled()

        composeRule.onNodeWithTag(FINISH).performClick()
        composeRule.onNodeWithTag(MEDIUM).performClick()
        await { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithText("Couldn't fix this board in time. Change a few highlighted cells and try again.").assertExists()
        assertEquals(emptyList<Any>(), controller().adopted)
    }

    private fun controller(): FakeEditorController = ui.controllers.single()

    private fun awaitEnabled() =
        await { composeRule.onAllNodes(hasTestTag(FINISH) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }

    /** The main looper runs between checks, so work that returns from a background thread lands. */
    private fun await(condition: () -> Boolean) = composeRule.waitUntil(TIMEOUT_MS) {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        condition()
    }

    private fun open(
        marks: Int = 2,
        game: FakeGameEditor = editor,
    ) {
        runBlocking { drafts.upsert(storedRow(game.draft(marks).encode())) }
        val games = GameRegistry(listOf(withEditor(game)))
        val editors = GameEditorRegistry(listOf(ui))
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
                        EditorViewModel(
                            key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(),
                            saves = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                            clock = { NOW },
                            createShared = signedOutPublishing(drafts),
                            publishedPuzzles = FakePublishedPuzzleRepository(),
                            identity = nobodySignedIn(),
                            idTokens = noGoogleAccount,
                            updatePublishState = signedOutUnpublishing(),
                            preferences = FakeUserPreferencesRepository(),
                            finishRandom = Random(SEED),
                        )
                    }
                    idleCreateRoot(drafts)
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { editorEntry(navigator, PlayLayout.SinglePane) }(EditorKey(DRAFT_ID)).Content()
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { ui.controllers.isNotEmpty() }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val TIMEOUT_MS = 10_000L
        const val DRAFT_ID = "draft-1"
        const val SEED = 3
        const val FRAME_MS = 16L
        const val FINISH = "create.tool.finish"
        const val UNDO = "create.tool.undo"
        const val TOAST = "create.toast.finishGaveUp"
        const val MEDIUM = "create.finish.level.Medium"
        const val HARD = "create.finish.level.Hard"

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
                playAgainSession = base.playAgainSession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}
