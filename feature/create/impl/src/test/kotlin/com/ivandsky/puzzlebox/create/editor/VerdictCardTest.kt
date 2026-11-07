package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeEditorController
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
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
 * The verdict card (02 handoff, "Pill popover"; spec S6, A8): tapping the pill opens it,
 * Check again after Undetermined runs exactly one more analysis, and a Unique board shows its
 * difficulty, or "needs guessing", on both the pill and the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = FakeGameEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `tapping the pill opens the card`() {
        open(marks = 1)
        composeRule.onNodeWithTag(CARD).assertDoesNotExist()

        composeRule.onNodeWithTag(PILL).performClick()

        composeRule.onNodeWithTag(CARD).assertExists()
    }

    @Test
    fun `check again after Undetermined submits to the pipeline exactly once more`() {
        editor.analysis = { _, _ -> Verdict.Undetermined }
        open(marks = 1)
        awaitPill("Undetermined")
        val before = editor.analyzed.size
        openCard()

        composeRule.onNodeWithTag(CHECK_AGAIN).performClick()
        awaitPill("Checking")
        awaitPill("Undetermined")
        ShadowLooper.idleMainLooper(SETTLE_MS, TimeUnit.MILLISECONDS)
        assertEquals(before + 1, editor.analyzed.size)
        composeRule.onNodeWithTag(CARD).assertExists()
    }

    @Test
    fun `check again is offered only after Undetermined`() {
        open(marks = 1)
        awaitPill("Unique")
        openCard()

        composeRule.onNodeWithTag(CARD).assertExists()
        composeRule.onNodeWithTag(CHECK_AGAIN).assertDoesNotExist()
    }

    @Test
    fun `a graded Unique shows its level on the pill and the card`() {
        editor.analysis = { draft, _ -> Verdict.Unique(Solution(listOf(draft.marks)), Difficulty(MediumRung)) }
        open(marks = 1)
        awaitPill("Unique")
        assertGrade(PILL_GRADE, "Medium")

        openCard()

        assertGrade(CARD_GRADE, "Medium")
    }

    @Test
    fun `a Unique that needs guessing says so on the pill and the card`() {
        editor.analysis = { draft, _ -> Verdict.Unique(Solution(listOf(draft.marks)), needsGuessing = true) }
        open(marks = 1)
        awaitPill("Unique")
        assertGrade(PILL_GRADE, NEEDS_GUESSING)

        openCard()

        assertGrade(CARD_GRADE, NEEDS_GUESSING)
    }

    @Test
    fun `a fix's Select closes the card and leaves its cells selected`() {
        open(marks = 1)
        openCard()
        composeRule.onNodeWithTag(CARD).assertExists()

        composeRule.onNodeWithTag(FIX_SELECT).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CARD).assertDoesNotExist()
        assertEquals(FakeEditorController.FIX_CELLS, ui.controllers.single().selected)
    }

    /** Taps the pill and lets the card's popover settle in its window. */
    private fun openCard() {
        composeRule.onNodeWithTag(PILL).performClick()
        composeRule.waitForIdle()
    }

    private fun open(marks: Int) {
        runBlocking { drafts.upsert(storedRow(editor.draft(marks).encode())) }
        val games = GameRegistry(listOf(withEditor(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
                        EditorViewModel(key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(), saves = CoroutineScope(SupervisorJob() + Dispatchers.Main), clock = { NOW }, createShared = signedOutPublishing(drafts))
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

    /** Waits for the pill's [state], running the paused main looper's clock so the pipeline's debounce elapses. */
    private fun awaitPill(state: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            composeRule.onAllNodes(hasTestTag(PILL) and hasState(state)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertGrade(tag: String, state: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            composeRule.onAllNodes(hasTestTag(tag) and hasState(state), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hasState(state: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    private object MediumRung : LadderRung {
        override val id: String = "test.medium"
        override val level: Difficulty.Level = Difficulty.Level.Medium
        override val captionKey: String = "test.medium"
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val SETTLE_MS = 1_000L
        const val PILL = "create.editor.pill"
        const val PILL_GRADE = "create.editor.pill.grade"
        const val CARD = "create.verdict.card"
        const val CARD_GRADE = "create.verdict.grade"
        const val CHECK_AGAIN = "create.verdict.checkAgain"
        const val FIX_SELECT = "fake.editor.fix.select"
        const val NEEDS_GUESSING = "NeedsGuessing"

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
