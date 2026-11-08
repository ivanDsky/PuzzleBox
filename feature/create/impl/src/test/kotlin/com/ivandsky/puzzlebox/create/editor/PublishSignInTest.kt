package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
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
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.PublishOutcome
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
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Signed out, Publish opens the sign-in sheet instead of publishing; its Google button signs in
 * and the publish continues on its own, once. Cancel, and every way Google or the account
 * exchange can fail, close the sheet having written no document and reserved no code; a failure
 * shows its own reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class PublishSignInTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid }
    private val identity = FakeIdentityService(initial = null, signInResult = AuthResult.Ok(AUTHOR))
    private val idTokens = FakeGoogleIdTokenProvider(GoogleIdToken.Available(TOKEN))
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))
    private lateinit var vm: EditorViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `signed out, publish opens the sheet and a Google account publishes once`() {
        open()

        tapPublish()
        awaitTag(SHEET)
        assertEquals("the sheet asks first; Google isn't called yet", 0, idTokens.calls)
        assertTrue("nothing published before sign-in", shared.docs.isEmpty())

        composeRule.onNodeWithTag(GOOGLE).performClick()

        awaitTrue { shared.docs.size == 1 }
        awaitGone(SHEET)
        awaitTag(PUBLISHED_CODE)
        val code = (vm.publish.state.value.outcome as? PublishOutcome.Published)?.code
        assertEquals("one doc, under the published code", setOf(code), shared.docs.keys)
        assertEquals("one Google sign-in", 1, idTokens.calls)
        assertEquals(listOf(TOKEN), identity.signInCalls)
        assertEquals(code?.value, drafts.rows.getValue(DRAFT_ID).reservedCode)
        assertTrue(vm.publish.state.value.signedIn)
    }

    @Test
    fun `cancel closes the sheet and writes nothing`() {
        open()
        tapPublish()
        awaitTag(SHEET)

        composeRule.onNodeWithTag(CANCEL).performClick()

        awaitGone(SHEET)
        assertNothingWritten()
        assertEquals("Google never asked", 0, idTokens.calls)
        assertNull(vm.publish.state.value.signInFailure)
    }

    @Test
    fun `cancel while signing in drops the sign-in and publishes nothing`() {
        idTokens.gate = CompletableDeferred()
        open()
        signInFromSheet()
        awaitTrue { vm.publish.state.value.signIn?.signingIn == true }

        composeRule.onNodeWithTag(CANCEL).performClick()
        awaitGone(SHEET)
        idTokens.gate?.complete(Unit)
        awaitTrue { identity.signInCalls.isNotEmpty() }
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)

        assertNothingWritten()
        assertNull("no failure for a dropped sign-in", vm.publish.state.value.signInFailure)
        assertTrue("not publishing", !vm.publish.state.value.busy)
    }

    @Test
    fun `a backend SignedOut for a signed-in author reopens the sheet once`() {
        identity.currentIdentity = AUTHOR
        shared.signedInUid = null
        open()

        tapPublish()

        awaitTag(SHEET)
        assertTrue("signed in, so Publish asked the backend", vm.publish.state.value.signedIn)
        assertEquals("the sheet only holds the request", 0, idTokens.calls)

        composeRule.onNodeWithTag(GOOGLE).performClick()

        awaitTrue { vm.publish.state.value.outcome == PublishOutcome.SignedOut }
        awaitGone(SHEET)
        assertEquals("one Google sign-in, no second sheet", 1, idTokens.calls)
        assertTrue(shared.docs.isEmpty())
    }

    @Test
    fun `the publish state is signed in only once an account is signed in`() {
        open()
        assertTrue("signed out before sign-in", !vm.publish.state.value.signedIn)

        signInFromSheet()

        awaitTrue { vm.publish.state.value.signedIn }
        awaitTag(PUBLISHED_CODE)
    }

    @Test
    fun `a cancelled Google sheet closes the sheet and writes no doc and no reserved code`() {
        idTokens.next = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
        open()

        signInFromSheet()

        awaitGone(SHEET)
        awaitTag("$FAILURE.cancelled")
        assertNothingWritten()
        assertEquals(
            PublishSession.SignInFailure.Token(GoogleIdToken.Unavailable.Reason.CANCELLED),
            vm.publish.state.value.signInFailure,
        )
    }

    @Test
    fun `a network failure shows its reason`() {
        idTokens.next = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.NETWORK)
        open()

        signInFromSheet()

        awaitTag("$FAILURE.network")
        assertEquals(
            PublishSession.SignInFailure.Token(GoogleIdToken.Unavailable.Reason.NETWORK),
            vm.publish.state.value.signInFailure,
        )
        assertNothingWritten()
    }

    @Test
    fun `every unavailable reason writes nothing and shows its own reason`() {
        open()

        for (reason in GoogleIdToken.Unavailable.Reason.entries) {
            idTokens.next = GoogleIdToken.Unavailable(reason)
            signInFromSheet()

            awaitTag("$FAILURE.${reason.name.lowercase()}")
            awaitGone(SHEET)
            assertNothingWritten()
        }
        assertTrue("no account exchange without a token", identity.signInCalls.isEmpty())
    }

    @Test
    fun `a refused account exchange writes nothing and shows its reason`() {
        identity.nextSignInResult = AuthResult.Err(AuthError.Rejected("expired"))
        open()

        signInFromSheet()

        awaitTag("$FAILURE.account.rejected")
        awaitGone(SHEET)
        assertEquals(listOf(TOKEN), identity.signInCalls)
        assertNothingWritten()
    }

    private fun assertNothingWritten() {
        assertTrue("no shared doc", shared.docs.isEmpty())
        assertNull("no reserved code", drafts.rows.getValue(DRAFT_ID).reservedCode)
        assertNull("no publish outcome", vm.publish.state.value.outcome)
    }

    private fun signInFromSheet() {
        tapPublish()
        awaitTag(SHEET)
        composeRule.onNodeWithTag(GOOGLE).performClick()
    }

    private fun tapPublish() {
        val enabled = hasTestTag(PUBLISH) and isEnabled()
        awaitTrue { composeRule.onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(enabled).performClick()
    }

    private fun open() {
        val board = editor.draft(marks = 1)
        runBlocking { drafts.upsert(storedRow(board)) }
        val games = GameRegistry(listOf(pluginOf(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
        val createShared = CreateSharedPuzzle(
            drafts = drafts,
            shared = shared,
            published = FakePublishedPuzzleRepository(),
            identity = identity,
            validate = ValidatePublish(),
            random = Random(0),
            clock = { NOW },
        )
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        EditorViewModel(
                            key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(),
                            saves = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                            clock = { NOW },
                            createShared = createShared,
                            publishedPuzzles = FakePublishedPuzzleRepository(),
                            identity = identity,
                            idTokens = idTokens,
                            updatePublishState = signedOutUnpublishing(),
                        ).also { vm = it }
                    }
                },
            )
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

    private fun awaitTag(tag: String) = awaitTrue { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitGone(tag: String) = awaitTrue { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    /** Google's half of sign-in, scripted: every call answers [next]. */
    private class FakeGoogleIdTokenProvider(var next: GoogleIdToken) : GoogleIdTokenProvider {
        /** When set, a call waits for it before answering. */
        var gate: CompletableDeferred<Unit>? = null

        var calls = 0
            private set

        override suspend fun idToken(activityContext: Context): GoogleIdToken {
            calls++
            gate?.await()
            return next
        }
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
        const val PUBLISH = "create.action.publish"
        const val PUBLISHED_CODE = "create.published.code"
        const val SHEET = "create.signIn.sheet"
        const val GOOGLE = "create.signIn.google"
        const val CANCEL = "create.signIn.cancel"
        const val FAILURE = "create.signIn.failure"

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
