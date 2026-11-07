package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
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
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
 * The tablet Studio (spec A3/A4, 02 handoff "Tablet"): [PlayLayout.TwoPane] puts the verdict
 * card inline in the 372 dp side pane, with the tool strip (its game hint slot too) and the
 * actions, and drops the pill; the board, with whatever the game draws under its grid, stays
 * in the left pane. [PlayLayout.SinglePane] keeps the pill and has no side pane.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land")
class StudioTwoPaneTest {

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
    fun `two-pane shows the inline card in the side pane and no pill`() {
        openStudio(PlayLayout.TwoPane)

        composeRule.onNodeWithTag(SIDE_PANE).assertExists()
        composeRule.onNode(inSidePane(CARD)).assertExists()
        composeRule.onNode(inSidePane(STRIP)).assertExists()
        composeRule.onNode(inSidePane(TEST_SOLVE)).assertExists()
        composeRule.onNodeWithTag(PILL).assertDoesNotExist()
        composeRule.onNodeWithTag(BOARD_SLOT).assertExists()
        composeRule.onNode(hasTestTag(BOARD_SLOT) and hasAnyAncestor(hasTestTag(SIDE_PANE))).assertDoesNotExist()
    }

    @Test
    fun `single-pane shows the pill, no side pane, and the card only from the pill`() {
        openStudio(PlayLayout.SinglePane)

        composeRule.onNodeWithTag(PILL).assertExists()
        composeRule.onNodeWithTag(SIDE_PANE).assertDoesNotExist()
        composeRule.onNodeWithTag(CARD).assertDoesNotExist()

        composeRule.onNodeWithTag(PILL).performClick()
        composeRule.onNodeWithTag(CARD).assertExists()
        composeRule.onNodeWithTag(SIDE_PANE).assertDoesNotExist()
    }

    @Test
    fun `two-pane puts the tool strip's game hint in the side pane and keeps the board's under-grid rows left`() {
        composeRule.setContent { PuzzleBoxTheme { Studio(PlayLayout.TwoPane) } }

        composeRule.onNode(inSidePane(HINT)).assertExists()
        composeRule.onNode(hasTestTag(COACH) and hasAnyAncestor(hasTestTag(BOARD_SLOT))).assertExists()
        composeRule.onNode(inSidePane(COACH)).assertDoesNotExist()
    }

    @Test
    fun `single-pane has the hint and the board's rows without a side pane`() {
        composeRule.setContent { PuzzleBoxTheme { Studio(PlayLayout.SinglePane) } }

        composeRule.onNodeWithTag(HINT).assertExists()
        composeRule.onNode(hasTestTag(COACH) and hasAnyAncestor(hasTestTag(BOARD_SLOT))).assertExists()
        composeRule.onNodeWithTag(SIDE_PANE).assertDoesNotExist()
    }

    private fun inSidePane(tag: String): SemanticsMatcher = hasTestTag(tag) and hasAnyAncestor(hasTestTag(SIDE_PANE))

    private fun openStudio(layout: PlayLayout) {
        runBlocking { drafts.upsert(storedRow(editor.draft(0).encode())) }
        val games = GameRegistry(listOf(withEditor(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        startKoin {
            // Every Studio test runs with friend sharing on (ADR-0022).
            modules(module { single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.FriendSharing) } })
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
                        EditorViewModel(key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(), saves = CoroutineScope(SupervisorJob() + Dispatchers.Main), clock = { NOW }, createShared = signedOutPublishing(drafts), identity = nobodySignedIn(), idTokens = noGoogleAccount)
                    }
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { editorEntry(navigator, layout) }(EditorKey(DRAFT_ID)).Content()
            }
        }
        composeRule.waitUntil { ui.controllers.isNotEmpty() }
    }

    /** The stateless Studio with a board that draws a coach row under its grid, and a strip with a game hint. */
    @Composable
    private fun Studio(layout: PlayLayout) {
        StudioScreen(
            title = "Untitled 1",
            subtitle = "Fake 1×1",
            saving = false,
            verdict = Verdict.Checking,
            layout = layout,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier ->
                Column(boardModifier) {
                    Box(Modifier.weight(1f))
                    Box(Modifier.testTag(COACH))
                }
            },
            toolStrip = { stripModifier ->
                ToolStrip(
                    canUndo = false,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = listOf(VariantId("fake/1")),
                    size = VariantId("fake/1"),
                    sizeLabel = { "1×1" },
                    onResize = {},
                    onClear = {},
                    hint = { hintModifier -> Box(hintModifier.testTag(HINT)) },
                    modifier = stripModifier,
                )
            },
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val SIDE_PANE = "create.studio.sidePane"
        const val CARD = "create.verdict.card"
        const val PILL = "create.editor.pill"
        const val STRIP = "create.tool.strip"
        const val TEST_SOLVE = "create.action.testSolve"
        const val BOARD_SLOT = "create.editor.board"
        const val HINT = "test.tool.hint"
        const val COACH = "test.board.coach"

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
