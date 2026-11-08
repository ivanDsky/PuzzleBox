package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
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
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.DAILY_PUBLISH_CAP
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
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
 * A publish that doesn't land says why under Publish: offline, or refused, the banner names it
 * and Publish turns into Retry, which goes out under the code already reserved; nothing is
 * written or queued meanwhile. At the day's cap the cap block joins the readiness line, Publish
 * stays off and test solving stays on. While a publish runs, Publish is a spinner that takes no taps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class PublishSessionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid }
    private val published = FakePublishedPuzzleRepository()
    private val gated = GatedSharedPuzzleRepository(shared)
    private val identity = FakeIdentityService(initial = AUTHOR)
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))
    private lateinit var vm: EditorViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `offline, publish writes nothing, shows the offline banner and Retry, and queues nothing`() {
        shared.online = false
        open()

        tapPublish()

        awaitTag(OFFLINE)
        val state = vm.publish.state.value
        assertEquals(PublishOutcome.Offline, state.outcome)
        assertTrue("Publish now retries", state.retry)
        assertTrue("no doc while offline", shared.docs.isEmpty())
        assertTrue("not listed as published", published.rows.isEmpty())
        val reserved = checkNotNull(drafts.rows.getValue(DRAFT_ID).reservedCode)

        shared.online = true
        idle(QUEUE_WAIT_MS)
        assertTrue("back online, nothing goes out on its own", shared.docs.isEmpty())

        tapPublish()

        awaitTag(PUBLISHED_CODE)
        awaitGone(OFFLINE)
        val code = ShareCode.parse(reserved)
        assertEquals(PublishOutcome.Published(checkNotNull(code)), vm.publish.state.value.outcome)
        assertEquals("Retry used the reserved code", setOf(code), shared.docs.keys)
        assertFalse(vm.publish.state.value.retry)
    }

    @Test
    fun `an answer lost after the commit is retried under the same code, leaving one doc`() {
        shared.commitThenOffline = true
        open()

        tapPublish()

        awaitTag(OFFLINE)
        val committed = shared.docs.keys.single()
        assertEquals(committed.value, drafts.rows.getValue(DRAFT_ID).reservedCode)

        tapPublish()

        awaitTag(PUBLISHED_CODE)
        assertEquals(PublishOutcome.Published(committed), vm.publish.state.value.outcome)
        assertEquals("exactly one doc", setOf(committed), shared.docs.keys)
    }

    @Test
    fun `an autosave while createShared is in flight keeps the reserved code on the stored row`() {
        gated.gate = CompletableDeferred()
        open()

        tapPublish()
        awaitTrue { gated.entered }
        val reserved = checkNotNull(drafts.rows.getValue(DRAFT_ID).reservedCode)
        assertTrue("nothing committed while held", shared.docs.isEmpty())

        ui.controllers.single().edit(editor.draft(marks = 2))
        idle(QUEUE_WAIT_MS)

        val row = drafts.rows.getValue(DRAFT_ID)
        assertEquals("the edit was stored", 2, row.blob.last().toInt())
        assertEquals("the autosave kept the reserved code", reserved, row.reservedCode)

        gated.gate?.complete(Unit)
        awaitTag(PUBLISHED_CODE)
        assertEquals(setOf(ShareCode.parse(reserved)), shared.docs.keys)
        assertEquals(reserved, drafts.rows.getValue(DRAFT_ID).reservedCode)
    }

    @Test
    fun `a failed createShared then a retry reuses the same code, with no second reservation`() {
        shared.online = false
        open()

        tapPublish()
        awaitTag(OFFLINE)
        val first = checkNotNull(drafts.rows.getValue(DRAFT_ID).reservedCode)
        ui.controllers.single().edit(editor.draft(marks = 1))
        idle(QUEUE_WAIT_MS)
        assertEquals("kept across the failure and an autosave", first, drafts.rows.getValue(DRAFT_ID).reservedCode)

        shared.online = true
        tapPublish()

        awaitTag(PUBLISHED_CODE)
        assertEquals(setOf(ShareCode.parse(first)), shared.docs.keys)
        assertEquals(first, drafts.rows.getValue(DRAFT_ID).reservedCode)
    }

    @Test
    fun `at the day's cap the cap block and banner show, Publish stays off and test solving stays on`() {
        shared.publishesToday = DAILY_PUBLISH_CAP
        open()

        tapPublish()

        awaitTag(CAP)
        awaitTag(CAP_READINESS)
        assertEquals(PublishOutcome.DailyCapReached, vm.publish.state.value.outcome)
        assertTrue(PublishBlock(PublishBlock.DAILY_CAP) in vm.publish.state.value.blocks)
        assertFalse("the cap isn't retried", vm.publish.state.value.retry)
        awaitTrue { composeRule.onAllNodes(hasTestTag(PUBLISH) and isNotEnabled()).fetchSemanticsNodes().isNotEmpty() }
        awaitTrue { composeRule.onAllNodes(hasTestTag(TEST_SOLVE) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("nothing written at the cap", shared.docs.isEmpty())
    }

    @Test
    fun `a refused publish shows the refused banner and Retry`() {
        shared.signedInUid = "someone-else"
        open()

        tapPublish()

        awaitTag(REJECTED)
        assertTrue(vm.publish.state.value.outcome is PublishOutcome.Rejected)
        assertTrue(vm.publish.state.value.retry)
        assertTrue(shared.docs.isEmpty())
    }

    @Test
    fun `while a publish runs the call-to-action is a busy spinner that takes no taps`() {
        var taps = 0
        composeRule.setContent {
            PuzzleBoxTheme { PublishCta(PublishSession.State(busy = true), ready = true, onPublish = { taps++ }) }
        }

        val cta = composeRule.onNodeWithTag(PUBLISH)
        cta.assert(isNotEnabled())
        cta.assert(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        cta.performClick()
        assertEquals(0, taps)
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
            shared = gated,
            published = published,
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
                            publishedPuzzles = published,
                            identity = identity,
                            idTokens = noGoogleAccount,
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

    /** Runs the main looper and the frame clock for [millis], giving any background work its chance. */
    private fun idle(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.waitForIdle()
    }

    /** The fake store, holding [publish] at [gate] when one is set: the call is in flight until the gate opens. */
    private class GatedSharedPuzzleRepository(private val delegate: SharedPuzzleRepository) : SharedPuzzleRepository by delegate {
        @Volatile var gate: CompletableDeferred<Unit>? = null
        @Volatile var entered = false

        override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
            entered = true
            gate?.await()
            return delegate.publish(doc)
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
        const val DRAFT_ID = "draft-1"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val QUEUE_WAIT_MS = 2_000L
        const val PUBLISH = "create.action.publish"
        const val TEST_SOLVE = "create.action.testSolve"
        const val PUBLISHED_CODE = "create.published.code"
        const val OFFLINE = "create.publish.banner.offline"
        const val CAP = "create.publish.banner.cap"
        const val REJECTED = "create.publish.banner.rejected"
        const val CAP_READINESS = "create.readiness.${PublishBlock.DAILY_CAP}"

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
            authorName = "Ivan",
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
