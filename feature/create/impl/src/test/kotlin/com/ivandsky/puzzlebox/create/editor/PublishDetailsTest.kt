package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import com.ivandsky.puzzlebox.game.editor.PublishBlock
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
 * The inline publish details (spec A15, A25): the author name starts as the account's display
 * name, a value over its cap disables Publish with an inline error, typed values are stored on
 * the draft, and signing in from the sheet fills the name unless the author typed one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class PublishDetailsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid }
    private val games = GameRegistry(listOf(pluginOf(editor)))
    private val editors = GameEditorRegistry(listOf(ui))
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))
    private lateinit var identity: FakeIdentityService
    private lateinit var vm: EditorViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `signed in, the author field starts as the display name`() {
        open(signedIn = true)

        awaitTag(AUTHOR_FIELD)
        assertEquals(AUTHOR.displayName, vm.publish.state.value.details.authorName)
        assertEquals(AUTHOR.displayName, fieldText(AUTHOR_FIELD))
    }

    @Test
    fun `an author name over its cap disables Publish with an inline error, and one at the cap clears it`() {
        open(signedIn = true)
        awaitTag(AUTHOR_FIELD)

        composeRule.onNodeWithTag(AUTHOR_FIELD).performTextReplacement("a".repeat(Draft.AUTHOR_MAX + 1))

        awaitTag(AUTHOR_ERROR)
        awaitTag("create.readiness.${PublishBlock.AUTHOR_TOO_LONG}")
        composeRule.onNodeWithTag(PUBLISH).assertIsNotEnabled()

        composeRule.onNodeWithTag(AUTHOR_FIELD).performTextReplacement("a".repeat(Draft.AUTHOR_MAX))

        awaitGone(AUTHOR_ERROR)
        awaitTrue { composeRule.onAllNodes(hasTestTag(PUBLISH) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a note over its cap disables Publish with an inline error`() {
        open(signedIn = true)
        awaitTag(ADD_NOTE)
        composeRule.onNodeWithTag(ADD_NOTE).performClick()
        awaitTag(NOTE_FIELD)

        composeRule.onNodeWithTag(NOTE_FIELD).performTextReplacement("n".repeat(Draft.NOTE_MAX + 1))

        awaitTag(NOTE_ERROR)
        awaitTag("create.readiness.${PublishBlock.NOTE_TOO_LONG}")
        composeRule.onNodeWithTag(PUBLISH).assertIsNotEnabled()
    }

    @Test
    fun `typed details are stored on the draft and come back when it reopens`() {
        open(signedIn = true)
        awaitTag(AUTHOR_FIELD)
        composeRule.onNodeWithTag(AUTHOR_FIELD).performTextReplacement(TYPED_AUTHOR)
        composeRule.onNodeWithTag(ADD_NOTE).performClick()
        awaitTag(NOTE_FIELD)
        composeRule.onNodeWithTag(NOTE_FIELD).performTextReplacement(TYPED_NOTE)

        awaitTrue { drafts.rows.getValue(DRAFT_ID).let { it.authorName == TYPED_AUTHOR && it.note == TYPED_NOTE } }

        val reopened = editorViewModel()
        awaitTrue { reopened.publish.state.value.details.let { it.authorName == TYPED_AUTHOR && it.note == TYPED_NOTE } }
    }

    @Test
    fun `signed out, signing in from the sheet fills the author name and publishes it`() {
        open(signedIn = false)
        awaitTag(AUTHOR_FIELD)
        assertEquals("", vm.publish.state.value.details.authorName)

        publishThroughSignIn()

        awaitTrue { shared.docs.size == 1 }
        assertEquals(AUTHOR.displayName, shared.docs.values.single().authorName)
        awaitTrue { drafts.rows.getValue(DRAFT_ID).authorName == AUTHOR.displayName }
    }

    @Test
    fun `signed out, a typed author name is kept through sign-in`() {
        open(signedIn = false)
        awaitTag(AUTHOR_FIELD)
        composeRule.onNodeWithTag(AUTHOR_FIELD).performTextReplacement(TYPED_AUTHOR)

        publishThroughSignIn()

        awaitTrue { shared.docs.size == 1 }
        assertEquals(TYPED_AUTHOR, shared.docs.values.single().authorName)
    }

    private fun publishThroughSignIn() {
        val enabled = hasTestTag(PUBLISH) and isEnabled()
        awaitTrue { composeRule.onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(enabled).performClick()
        awaitTag(GOOGLE)
        composeRule.onNodeWithTag(GOOGLE).performClick()
    }

    private fun fieldText(tag: String): String? =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.EditableText) { null }?.text

    private fun editorViewModel(): EditorViewModel = EditorViewModel(
        EditorKey(DRAFT_ID), drafts, games, editors, GamePlayRegistry(emptyList()),
        CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }), DefaultDispatcherProvider(),
        saves = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        clock = { NOW },
        createShared = CreateSharedPuzzle(
            drafts = drafts,
            shared = shared,
            published = FakePublishedPuzzleRepository(),
            identity = identity,
            validate = ValidatePublish(),
            random = Random(0),
            clock = { NOW },
        ),
        identity = identity,
        idTokens = OneAccountGoogleIdTokenProvider,
    )

    private fun open(signedIn: Boolean) {
        identity = FakeIdentityService(initial = AUTHOR.takeIf { signedIn }, signInResult = AuthResult.Ok(AUTHOR))
        val board = editor.draft(marks = 1)
        runBlocking { drafts.upsert(storedRow(board)) }
        startKoin {
            modules(module { viewModel { (_: EditorKey) -> editorViewModel().also { vm = it } } })
        }
        val entries = entryProvider<NavKey> { editorEntry(navigator, PlayLayout.SinglePane) }
        composeRule.setContent {
            PuzzleBoxTheme {
                val top = navigator.state.currentStack.last()
                key(top) { entries(top).Content() }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { ui.controllers.isNotEmpty() }
    }

    private fun awaitTag(tag: String) =
        awaitTrue { composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

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
        const val TYPED_AUTHOR = "Mia"
        const val TYPED_NOTE = "Start in the corner"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val PUBLISH = "create.action.publish"
        const val GOOGLE = "create.signIn.google"
        const val AUTHOR_FIELD = "create.details.author"
        const val AUTHOR_ERROR = "create.details.author.error"
        const val ADD_NOTE = "create.details.addNote"
        const val NOTE_FIELD = "create.details.note"
        const val NOTE_ERROR = "create.details.note.error"

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
