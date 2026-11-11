package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * The seam's game slots in the real Studio: the game draws its board for the Studio's layout; on
 * a tablet its pane tools follow the tool strip in the side pane, and go with it while the publish
 * details show; on a phone there are none. Its publish fields close the details form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class GameEditorSlotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi(gameSlots = true)
    private val drafts = FakeDraftRepository()
    private val identity = FakeIdentityService(initial = AUTHOR, signInResult = AuthResult.Ok(AUTHOR))
    private val games = GameRegistry(listOf(pluginOf(editor)))
    private val editors = GameEditorRegistry(listOf(ui))
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))
    private lateinit var vm: EditorViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a phone draws the single-pane board and its publish fields close the details`() {
        open(PlayLayout.SinglePane, passed = true)

        awaitTag("fake.editor.board.SinglePane")
        awaitTag(DETAILS)
        awaitTrue { composeRule.onAllNodes(hasTestTag(FIELDS) and hasAnyAncestor(hasTestTag(DETAILS))).fetchSemanticsNodes().size == 1 }
        awaitGone(PANE_TOOLS)
    }

    @Test
    fun `a phone shows no pane tools beside the tool strip`() {
        open(PlayLayout.SinglePane, passed = false)

        awaitTag(STRIP)
        awaitGone(PANE_TOOLS)
        awaitGone(FIELDS)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `a tablet draws the two-pane board and the pane tools in the side pane under the strip`() {
        open(PlayLayout.TwoPane, passed = false)

        awaitTag("fake.editor.board.TwoPane")
        awaitTag(STRIP)
        awaitTrue { composeRule.onAllNodes(hasTestTag(PANE_TOOLS) and hasAnyAncestor(hasTestTag(SIDE_PANE))).fetchSemanticsNodes().size == 1 }
        val strip = composeRule.onNodeWithTag(STRIP).fetchSemanticsNode().boundsInRoot
        val tools = composeRule.onNodeWithTag(PANE_TOOLS).fetchSemanticsNode().boundsInRoot
        assertTrue("pane tools $tools under the strip $strip", tools.top >= strip.bottom)
        awaitGone(FIELDS)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `on a tablet the publish details take the pane tools' place with the strip and Edit board brings them back`() {
        open(PlayLayout.TwoPane, passed = true)

        awaitTag(DETAILS)
        awaitGone(PANE_TOOLS)
        awaitTrue { composeRule.onAllNodes(hasTestTag(FIELDS) and hasAnyAncestor(hasTestTag(SIDE_PANE))).fetchSemanticsNodes().size == 1 }

        composeRule.onNodeWithTag(EDIT_BOARD).performClick()

        awaitTag(PANE_TOOLS)
        awaitGone(FIELDS)
    }

    private fun editorViewModel(): EditorViewModel = EditorViewModel(
        EditorKey(DRAFT_ID), drafts, games, editors, GamePlayRegistry(emptyList()),
        CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }), DefaultDispatcherProvider(),
        saves = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        clock = { NOW },
        createShared = CreateSharedPuzzle(
            drafts = drafts,
            shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid },
            published = FakePublishedPuzzleRepository(),
            identity = identity,
            validate = ValidatePublish(),
            random = Random(0),
            clock = { NOW },
        ),
        publishedPuzzles = FakePublishedPuzzleRepository(),
        identity = identity,
        idTokens = OneAccountGoogleIdTokenProvider,
        updatePublishState = signedOutUnpublishing(),
        preferences = FakeUserPreferencesRepository(),
    )

    /** Opens the stored draft in [layout], its test passed when [passed]. */
    private fun open(layout: PlayLayout, passed: Boolean) {
        val board = editor.draft(marks = 1)
        runBlocking { drafts.upsert(storedRow(board, passed)) }
        startKoin {
            modules(
                module {
                    viewModel { (_: EditorKey) -> editorViewModel().also { vm = it } }
                    idleCreateRoot(drafts)
                },
            )
        }
        val entries = entryProvider<NavKey> { editorEntry(navigator, layout) }
        composeRule.setContent {
            PuzzleBoxTheme {
                val top = navigator.state.currentStack.last()
                key(top) { entries(top).Content() }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { ui.controllers.isNotEmpty() }
    }

    private fun awaitTag(tag: String) =
        awaitTrue { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitGone(tag: String) =
        awaitTrue { composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    /** Google with one account: every call hands out its ID token. */
    private object OneAccountGoogleIdTokenProvider : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available(TOKEN)
    }

    /** The stand-in board with sharing: a mark count travels as its one byte. */
    private class SharingEditor(
        private val base: FakeGameEditor = FakeGameEditor(),
    ) : GameEditor<TestDraft> by base, EditorShareCodec<TestDraft>, DraftExport<TestDraft> {
        override val shareFormatVersion: Int = 1
        override fun encodeShare(draft: TestDraft): ByteArray = byteArrayOf(draft.marks.toByte())
        override fun decodeShare(bytes: ByteArray): DecodeResult<TestDraft> = DecodeResult.Ok(TestDraft(bytes.single().toInt()))
        override fun toPuzzle(draft: TestDraft, solution: Solution): Puzzle =
            Puzzle(gameId, variants.first(), shareFormatVersion, encodeShare(draft))

        fun draft(marks: Int): EditorDraft<TestDraft> = EditorDraft(this, DraftState(TestDraft(marks)))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        val AUTHOR = Identity(uid = "author", displayName = "Ivan", photoUrl = null)
        const val TOKEN = "google-id-token"
        const val DRAFT_ID = "draft-1"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val DETAILS = "create.details"
        const val STRIP = "create.tool.strip"
        const val EDIT_BOARD = "create.tool.editBoard"
        const val SIDE_PANE = "create.studio.sidePane"
        const val PANE_TOOLS = "fake.editor.paneTools"
        const val FIELDS = "fake.editor.publishFields"

        fun storedRow(board: EditorDraft<TestDraft>, passed: Boolean) = Draft(
            id = DRAFT_ID,
            gameId = board.gameId,
            variant = board.variant,
            draftFormatVersion = board.editor.draftFormatVersion,
            blob = board.encode(),
            title = "Castle",
            updatedAt = NOW,
            testSolve = if (passed) TestSolve(board.layoutKey, timeMs = 102_000, hints = 0) else null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )

        fun pluginOf(editor: GameEditor<TestDraft>): GamePlugin<*, *, *> {
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
