package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
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
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The phone's tool slot: once a Unique board's test passes, the publish details take the tool
 * strip's place, so the board keeps its size; Edit board brings the tools back without touching
 * the test, and a board edit then clears it (Undo toast) while the tools stay. On a tablet the
 * tools and the details sit side by side as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class ToolSlotSwapTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi()
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
    fun `a passed test on a phone swaps the tools for the details`() {
        open(PlayLayout.SinglePane)

        awaitTag(DETAILS)
        awaitGone(STRIP)
        awaitTag(EDIT_BOARD)
    }

    @Test
    fun `with friend sharing off a passed test keeps the tools and opens no details`() {
        open(PlayLayout.SinglePane, flags = FakeReleaseFlags())
        awaitTrue { vm.testSolve.passed.value != null }
        composeRule.waitForIdle()

        awaitTag(STRIP)
        awaitGone(DETAILS)
        awaitGone(EDIT_BOARD)
        awaitGone(SUMMARY)
    }

    @Test
    fun `Edit board brings the tools back and keeps the test, and the summary reopens the details`() {
        open(PlayLayout.SinglePane)
        awaitTag(EDIT_BOARD)

        composeRule.onNodeWithTag(EDIT_BOARD).performClick()

        awaitTag(STRIP)
        awaitGone(DETAILS)
        awaitTag(SUMMARY)
        assertNotNull("Edit board keeps the test", vm.testSolve.passed.value)

        composeRule.onNodeWithTag(SUMMARY).performClick()

        awaitTag(DETAILS)
        awaitGone(STRIP)
    }

    @Test
    fun `a board edit after Edit board clears the test and keeps the tools, and Undo brings the details back`() {
        open(PlayLayout.SinglePane)
        awaitTag(EDIT_BOARD)
        composeRule.onNodeWithTag(EDIT_BOARD).performClick()
        awaitTag(STRIP)

        val opened = editor.draft(marks = 1)
        composeRule.runOnIdle { ui.controllers.single().edit(opened.apply(ReplaceDraft(opened.state.draft, TestDraft(marks = 2)))) }

        awaitTag(TOAST)
        awaitTrue { vm.testSolve.passed.value == null }
        awaitTag(STRIP)
        awaitGone(DETAILS)
        awaitGone(SUMMARY)

        composeRule.onNode(hasAnyAncestor(hasTestTag(TOAST)) and hasClickAction()).performClick()

        awaitTrue { vm.testSolve.passed.value != null }
        awaitTag(DETAILS)
        awaitGone(STRIP)
    }

    @Test
    fun `on a 360 by 640 phone the board keeps its size across the swap`() {
        assertBoardKeepsItsSize()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `on a 411 by 891 phone the board keeps its size across the swap`() {
        assertBoardKeepsItsSize()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `on a tablet the tools and the details sit side by side`() {
        open(PlayLayout.TwoPane)

        awaitTag(DETAILS)
        awaitTag(STRIP)
        awaitGone(EDIT_BOARD)
    }

    private fun assertBoardKeepsItsSize() {
        open(PlayLayout.SinglePane)
        awaitTag(EDIT_BOARD)
        val withDetails = boardBounds()

        composeRule.onNodeWithTag(EDIT_BOARD).performClick()
        awaitTag(STRIP)
        composeRule.waitForIdle()

        assertEquals(withDetails, boardBounds())
    }

    private fun boardBounds(): Rect = composeRule.onNodeWithTag(BOARD).fetchSemanticsNode().boundsInRoot

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
        identity = identity,
        idTokens = OneAccountGoogleIdTokenProvider,
    )

    /** Opens the stored, test-passed draft; Studio tests run with friend sharing on unless [flags] say otherwise (ADR-0022). */
    private fun open(layout: PlayLayout, flags: ReleaseFlags = FakeReleaseFlags(ReleaseFlag.FriendSharing)) {
        val board = editor.draft(marks = 1)
        runBlocking { drafts.upsert(storedRow(board)) }
        startKoin {
            modules(module { single<ReleaseFlags> { flags } })
            modules(module { viewModel { (_: EditorKey) -> editorViewModel().also { vm = it } } })
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
        const val SUMMARY = "create.details.summary"
        const val TOAST = "create.toast.testCleared"
        const val BOARD = "create.editor.board"

        fun storedRow(board: EditorDraft<TestDraft>) = Draft(
            id = DRAFT_ID,
            gameId = board.gameId,
            variant = board.variant,
            draftFormatVersion = board.editor.draftFormatVersion,
            blob = board.encode(),
            title = "Castle",
            updatedAt = NOW,
            testSolve = TestSolve(board.layoutKey, timeMs = 102_000, hints = 0),
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
                replaySession = base.replaySession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}
