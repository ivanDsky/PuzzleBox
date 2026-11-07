package com.ivandsky.puzzlebox.create.editor

import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.FakeEditorController
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.formatDuration
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
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.ShareCode
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Once published, the Studio's action area is the ticket: the code, the title and the author's
 * time, with Share (a chooser carrying the link) and Copy code (the clipboard). The draft is
 * read-only — a board tap, undo, resize and clear change nothing — Duplicate opens an editable
 * copy, and Back lands on Create. Under reduced motion the ticket lands without the stamp spring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class PublishedStudioTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = SharingEditor()
    private val ui = TappableEditorUi { marks -> editor.draft(marks) }
    private val drafts = FakeDraftRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid }
    private val published = FakePublishedPuzzleRepository()
    private val identity = FakeIdentityService(initial = AUTHOR)
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))
    private lateinit var vm: EditorViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the ticket shows the code, the title and the author's time`() {
        val code = openPublished()

        composeRule.onNodeWithTag(CODE).assertTextEquals(code.display)
        composeRule.onNodeWithTag(TICKET_TITLE).assertTextContains(TITLE, substring = true)
        composeRule.onNodeWithTag(AUTHOR_TIME).assertTextContains(formatDuration(TEST_TIME_MS), substring = true)
        awaitGone(PUBLISH)
    }

    @Test
    fun `Share opens a chooser whose ACTION_SEND text holds the link and the code`() {
        val code = openPublished()

        composeRule.onNodeWithTag(SHARE).performClick()

        val chooser = checkNotNull(shadowOf(composeRule.activity).nextStartedActivity) { "nothing was started" }
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = checkNotNull(chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        val text = checkNotNull(send.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue("the link travels", code.link in text)
        assertTrue("the code travels", code.display in text)
    }

    @Test
    fun `Copy code puts the code on the clipboard, swaps to a check and confirms`() {
        val code = openPublished()

        composeRule.onNodeWithTag(COPY).performClick()

        val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
        awaitTrue { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == code.display }
        awaitTag(COPIED)
        awaitTag(COPIED_TOAST)
    }

    @Test
    fun `the published draft is read-only - a board tap, undo, resize and clear change nothing`() {
        openPublished()
        val controller = ui.controllers.single()
        val board = controller.draft.value
        val blob = drafts.rows.getValue(DRAFT_ID).blob

        awaitTag(READ_ONLY)
        awaitGone(TOOLS)
        composeRule.onNodeWithTag(READ_ONLY_BOARD).performClick()
        vm.undo()
        vm.clear()
        vm.resize(editor.variants.first())
        idle(SETTLE_MS)

        assertEquals("the board is as published", board, controller.draft.value)
        assertArrayEquals("nothing was stored", blob, drafts.rows.getValue(DRAFT_ID).blob)
    }

    @Test
    fun `a published draft takes no rename, no details and no second publish`() {
        val code = openPublished()
        val stored = drafts.rows.getValue(DRAFT_ID)

        composeRule.runOnIdle {
            vm.meta.rename("Renamed")
            vm.publish.editAuthorName("Someone else")
            vm.publish.editNote("A new note")
            vm.publish.publish(ui.controllers.single().draft.value, Verdict.Unique(Solution(listOf(1))))
        }
        idle(SETTLE_MS)

        assertEquals("the title is as published", TITLE, vm.meta.title.value)
        assertEquals("the details are as published", "Ivan", vm.publish.state.value.details.authorName)
        assertEquals("nothing was stored", stored, drafts.rows.getValue(DRAFT_ID))
        assertEquals("still the one publish", PublishOutcome.Published(code), vm.publish.state.value.outcome)
        assertEquals("one doc", setOf(code), shared.docs.keys)
    }

    @Test
    fun `the published Studio's menu offers neither Rename nor Delete, and its title opens nothing`() {
        openPublished()

        composeRule.onNodeWithTag(TITLE_BLOCK).assertHasNoClickAction()
        composeRule.onNodeWithTag(MORE).performClick()

        awaitTag(MENU_DUPLICATE)
        awaitGone(MENU_RENAME)
        awaitGone(MENU_DELETE)
    }

    @Test
    fun `Duplicate opens an editable copy in the Studio's place`() {
        openPublished()

        composeRule.onNodeWithTag(DUPLICATE).performClick()

        awaitTrue { navigator.state.currentStack.last() == EditorKey(COPY_ID) }
        awaitTrue { ui.controllers.size == 2 }
        awaitTag(BOARD)
        awaitGone(READ_ONLY)
        composeRule.onNodeWithTag(BOARD).performClick()
        awaitTrue { drafts.rows.getValue(COPY_ID).blob.last().toInt() == MARKS + 1 }
        assertEquals("the source keeps its board", MARKS, drafts.rows.getValue(DRAFT_ID).blob.last().toInt())
    }

    @Test
    fun `Back from the published Studio lands on Create`() {
        openPublished()

        composeRule.onNodeWithTag(BACK).performClick()

        awaitTrue { navigator.state.currentStack.last() == CreateKey }
    }

    @Test
    fun `under reduced motion the ticket lands at its resting size, without the spring`() {
        Settings.Global.putFloat(composeRule.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        val (first, settled) = landingSizes()

        assertEquals(settled, first)
    }

    @Test
    fun `with motion on the ticket lands from the stamp's larger scale`() {
        val (first, settled) = landingSizes()

        assertTrue("it starts larger than at rest", first.width > settled.width)
        assertNotEquals(settled, first)
    }

    /** A just-published code's size on the first frame it shows, then once everything has settled. */
    private fun landingSizes(): Pair<Size, Size> {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuzzleBoxTheme { PublishedTicket(SAMPLE.copy(fresh = true), onCopied = {}) } }
        var frames = 0
        while (composeRule.onAllNodes(hasTestTag(CODE)).fetchSemanticsNodes().isEmpty()) {
            check(frames++ < MAX_FRAMES) { "the code never showed" }
            composeRule.mainClock.advanceTimeByFrame()
        }
        val first = codeSize()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        return first to codeSize()
    }

    private fun codeSize(): Size = composeRule.onNodeWithTag(CODE).fetchSemanticsNode().boundsInRoot.size

    /** Opens the stored, test-solved draft, publishes it and waits for the ticket; returns its code. */
    private fun openPublished(): ShareCode {
        open()
        val enabled = hasTestTag(PUBLISH) and isEnabled()
        awaitTrue { composeRule.onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(enabled).performClick()
        awaitTag(TICKET)
        awaitTag(CODE)
        val outcome = vm.publish.state.value.outcome
        return checkNotNull(outcome as? PublishOutcome.Published) { "expected Published, was $outcome" }.code
    }

    private fun open() {
        runBlocking { drafts.upsert(storedRow(editor.draft(MARKS))) }
        val games = GameRegistry(listOf(pluginOf(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        val copyDraft = CreateDraftCopy(drafts, newId = { COPY_ID }, clock = { NOW })
        val createShared = CreateSharedPuzzle(
            drafts = drafts,
            shared = shared,
            published = published,
            identity = identity,
            validate = ValidatePublish(),
            random = Random(0),
            clock = { NOW },
        )
        startKoin {
            // Every Studio test runs with friend sharing on (ADR-0022).
            modules(module { single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.FriendSharing) } })
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
        // Create's root is another entry's screen; landing there is the navigator's top.
        val entries = entryProvider<NavKey>(fallback = { unknown -> NavEntry(unknown) {} }) {
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

    // Unmerged: the Copy button's check icon sits inside the button's merged node.
    private fun awaitTag(tag: String) = awaitTrue { composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitGone(tag: String) = awaitTrue { composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

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

    /** The stand-in editor UI whose board, like a real one, edits on a tap: one more mark. */
    private class TappableEditorUi(
        private val base: FakeGameEditorUi = FakeGameEditorUi(),
        private val next: (marks: Int) -> EditorDraft<*>,
    ) : GameEditorUi by base {

        constructor(next: (marks: Int) -> EditorDraft<*>) : this(FakeGameEditorUi(), next)

        val controllers: List<FakeEditorController> get() = base.controllers

        @Composable
        override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
            val fake = controller as FakeEditorController
            val marks = fake.draft.value.encode().last().toInt()
            Text("$marks", modifier.testTag(BOARD).clickable { fake.edit(next(marks + 1)) })
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
        const val COPY_ID = "copy-1"
        const val TITLE = "Castle"
        const val MARKS = 1
        const val TEST_TIME_MS = 102_000L
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val SETTLE_MS = 2_000L
        const val MAX_FRAMES = 120
        const val BOARD = "fake.editor.board"
        const val BACK = "create.editor.back"
        const val TOOLS = "create.tool.strip"
        const val PUBLISH = "create.action.publish"
        const val TICKET = "create.published.ticket"
        const val CODE = "create.published.code"
        const val TICKET_TITLE = "create.published.title"
        const val AUTHOR_TIME = "create.published.authorTime"
        const val SHARE = "create.published.share"
        const val COPY = "create.published.copy"
        const val COPIED = "create.published.copied"
        const val COPIED_TOAST = "create.toast.codeCopied"
        const val READ_ONLY = "create.published.readOnly"
        const val READ_ONLY_BOARD = "create.studio.board.readOnly"
        const val DUPLICATE = "create.published.duplicate"
        const val TITLE_BLOCK = "create.studio.title"
        const val MORE = "create.studio.more"
        const val MENU_RENAME = "create.studio.rename"
        const val MENU_DUPLICATE = "create.studio.duplicate"
        const val MENU_DELETE = "create.studio.delete"

        val SAMPLE = ShareTicket(
            code = checkNotNull(ShareCode.parse("K7Q2MX")),
            title = TITLE,
            author = "Ivan",
            authorTimeMs = TEST_TIME_MS,
            game = "Queens",
            accent = AccentSlot.Violet,
        )

        fun storedRow(board: EditorDraft<TestDraft>) = Draft(
            id = DRAFT_ID,
            gameId = board.gameId,
            variant = board.variant,
            draftFormatVersion = board.editor.draftFormatVersion,
            blob = board.encode(),
            title = TITLE,
            updatedAt = NOW,
            testSolve = TestSolve(board.layoutKey, timeMs = TEST_TIME_MS, hints = 0),
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
