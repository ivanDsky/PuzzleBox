package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.api.createNavKeys
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import com.ivandsky.puzzlebox.create.editor.noGoogleAccount
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.create.navigation.publishedPuzzleEntry
import com.ivandsky.puzzlebox.create.published.PublishedPuzzleViewModel
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.CreateDraft
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
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.navigation.navKeySavedStateConfiguration
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
import org.junit.Assert.assertNull
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
 * Create's Published tab beside Drafts: published drafts leave Drafts and list under Published,
 * newest first; a row opens the published page (the read-only board, the ticket's code and the
 * author's time), whose Duplicate opens an editable, unreserved copy in the Studio; the empty
 * tab has its own state; the page's key survives saved state; and a published draft reopened
 * over the stored rows alone, as after process death, is the read-only Studio with its ticket.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class CreatePublishedTabTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi(editor.gameId)
    private val games = GameRegistry(listOf(pluginOf(editor)))
    private val editors = GameEditorRegistry(listOf(ui))
    private val drafts = FakeDraftRepository()
    private val published = FakePublishedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid }
    private val identity = FakeIdentityService(initial = AUTHOR)
    private val copyDraft = CreateDraftCopy(drafts, newId = { COPY_ID }, clock = { NOW })
    private lateinit var create: CreateViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `published rows list newest first under Published, and neither shows under Drafts`() {
        storePublished(OLDER, "older", minutesAgo = 30)
        storePublished(NEWER, "newer", minutesAgo = 5)
        store(draftRow("plain", marks = 1, code = null))
        show(CreateKey)

        awaitCount(DRAFT_ROW, 1)
        composeRule.onNodeWithTag(TAB_PUBLISHED).performClick()

        awaitCount(PUBLISHED_ROW, 2)
        assertEquals(listOf(NEWER, OLDER), create.uiState.value.published.map { it.code })
        assertEquals(listOf("plain"), create.uiState.value.drafts.map { it.id })
    }

    @Test
    fun `a row opens the published page with its board, code and author time`() {
        storePublished(NEWER, "newer", minutesAgo = 5)
        val navigator = show(CreateKey)

        composeRule.onNodeWithTag(TAB_PUBLISHED).performClick()
        awaitCount(PUBLISHED_ROW, 1)
        composeRule.onNodeWithTag(PUBLISHED_ROW).performClick()

        awaitTrue { navigator.top == PublishedPuzzleKey(NEWER.value) }
        awaitCount(READ_ONLY_BOARD, 1)
        awaitStamped(CODE)
        composeRule.onNodeWithTag(CODE).assertTextEquals(NEWER.display)
        awaitStamped(AUTHOR_TIME)
        composeRule.onNodeWithTag(AUTHOR_TIME).assertTextContains(formatDuration(TEST_TIME_MS), substring = true)
    }

    @Test
    fun `Duplicate makes an editable draft with the same board and no reserved code`() {
        storePublished(NEWER, "newer", minutesAgo = 5)
        val navigator = show(PublishedPuzzleKey(NEWER.value))

        awaitCount(PAGE_DUPLICATE, 1)
        composeRule.onNodeWithTag(PAGE_DUPLICATE).performClick()

        awaitTrue { navigator.top == EditorKey(COPY_ID) }
        val copy = checkNotNull(drafts.rows[COPY_ID])
        assertArrayEquals("the same board", drafts.rows.getValue("newer").blob, copy.blob)
        assertNull("unreserved, so it publishes under a new code", copy.reservedCode)
        awaitCount(BOARD, 1)
        awaitCount(STUDIO_READ_ONLY, 0)
    }

    @Test
    fun `with nothing published the Published tab shows its empty state`() {
        store(draftRow("plain", marks = 1, code = null))
        show(CreateKey)

        awaitCount(DRAFT_ROW, 1)
        composeRule.onNodeWithTag(TAB_PUBLISHED).performClick()

        awaitCount(PUBLISHED_EMPTY, 1)
        awaitCount(PUBLISHED_ROW, 0)
    }

    @Test
    fun `PublishedPuzzleKey survives a saved-state round trip`() {
        val state = NavigationState(CreateKey, listOf(listOf(CreateKey, PublishedPuzzleKey(NEWER.value))))
        val config = navKeySavedStateConfiguration { createNavKeys() }

        val restored = decodeFromSavedState(NavigationState.serializer(), encodeToSavedState(NavigationState.serializer(), state, config), config)

        assertEquals(state, restored)
    }

    @Test
    fun `a published draft reopened from storage alone is the read-only Studio with its ticket`() {
        store(draftRow("castle", marks = 1, code = null))
        val code = publishHeadless("castle")
        // Process death: only the stored rows and the saved back stack survive.
        val config = navKeySavedStateConfiguration { createNavKeys() }
        val saved = NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey("castle"))))
        val restored = decodeFromSavedState(NavigationState.serializer(), encodeToSavedState(NavigationState.serializer(), saved, config), config)

        showRestored(restored)

        awaitCount(STUDIO_READ_ONLY, 1)
        awaitCount(TICKET, 1)
        awaitStamped(CODE)
        composeRule.onNodeWithTag(CODE).assertTextEquals(code.display)
    }

    /** Publishes [draftId] through a Studio view model no screen ever showed, then drops it. */
    private fun publishHeadless(draftId: String): ShareCode {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val vm = editorViewModel(EditorKey(draftId), scope)
        awaitTrue { vm.publish.state.value.signedIn && drafts.rows.getValue(draftId).testSolve != null }
        awaitTrue { ui.controllers.isNotEmpty() }
        vm.publish.publish(ui.controllers.last().draft.value, Verdict.Unique(Solution(listOf(1))))
        awaitTrue { vm.publish.state.value.outcome is PublishOutcome.Published }
        ui.controllers.clear()
        return (vm.publish.state.value.outcome as PublishOutcome.Published).code
    }

    private fun editorViewModel(key: EditorKey, saves: CoroutineScope) = EditorViewModel(
        key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(),
        saves = saves,
        clock = { NOW },
        createShared = CreateSharedPuzzle(
            drafts = drafts,
            shared = shared,
            published = published,
            identity = identity,
            validate = ValidatePublish(),
            random = Random(0),
            clock = { NOW },
        ),
        publishedPuzzles = published,
        identity = identity,
        idTokens = noGoogleAccount,
    )

    private fun show(top: NavKey): Navigator =
        showRestored(NavigationState(CreateKey, listOf(listOfNotNull(CreateKey, top.takeIf { it != CreateKey }))))

    private fun showRestored(state: NavigationState): Navigator {
        val navigator = Navigator(state)
        val saves = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games, editors, GamePlayRegistry(emptyList()), drafts, published,
                            CreateDraft(games, drafts, newId = { "new" }, clock = { NOW }), copyDraft, DefaultDispatcherProvider(),
                            clock = { NOW },
                        ).also { create = it }
                    }
                    viewModel { (key: PublishedPuzzleKey) ->
                        PublishedPuzzleViewModel(key, published, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider())
                    }
                    viewModel { (key: EditorKey) -> editorViewModel(key, saves) }
                },
            )
        }
        val entries = entryProvider<NavKey> {
            createEntry(navigator)
            publishedPuzzleEntry(navigator)
            editorEntry(navigator, PlayLayout.SinglePane)
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                val top = navigator.top
                key(top) { entries(top).Content() }
            }
        }
        return navigator
    }

    private val Navigator.top: NavKey get() = state.currentStack.last()

    private fun storePublished(code: ShareCode, draftId: String, minutesAgo: Long) {
        store(draftRow(draftId, marks = 2, code = code))
        runBlocking {
            published.upsert(
                PublishedPuzzle(
                    code = code,
                    gameId = editor.gameId,
                    variant = editor.variants.first(),
                    title = "Title $draftId",
                    authorTimeMs = TEST_TIME_MS,
                    authorHints = 0,
                    createdAt = NOW.minusSeconds(minutesAgo * 60),
                    unpublished = false,
                    solvesCount = null,
                    solvesCountAt = null,
                ),
            )
        }
    }

    private fun store(row: Draft) = runBlocking { drafts.upsert(row) }

    private fun draftRow(id: String, marks: Int, code: ShareCode?): Draft {
        val board = editor.draft(marks)
        return Draft(
            id = id,
            gameId = board.gameId,
            variant = board.variant,
            draftFormatVersion = board.editor.draftFormatVersion,
            blob = board.encode(),
            title = "Title $id",
            updatedAt = NOW,
            testSolve = TestSolve(board.layoutKey, timeMs = TEST_TIME_MS, hints = 0),
            reservedCode = code?.value,
            imported = false,
            authorName = "Ivan",
            note = "",
        )
    }

    /** A ticket line once stamped: it sits on a semantics-cleared placeholder of itself, so read the merged tree. */
    private fun awaitStamped(tag: String) = awaitTrue { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size == 1 }

    private fun awaitCount(tag: String, count: Int) =
        awaitTrue { composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == count }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
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
        val NOW: Instant = Instant.parse("2026-11-07T10:00:00Z")
        val AUTHOR = Identity(uid = "author", displayName = "Ivan", photoUrl = null)
        val OLDER: ShareCode = checkNotNull(ShareCode.parse("K7Q2MX"))
        val NEWER: ShareCode = checkNotNull(ShareCode.parse("B4N8TW"))
        const val COPY_ID = "copy-1"
        const val TEST_TIME_MS = 102_000L
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val BOARD = "fake.editor.board"
        const val DRAFT_ROW = "create.draft.row"
        const val TAB_PUBLISHED = "create.tab.published"
        const val PUBLISHED_ROW = "create.published.row"
        const val PUBLISHED_EMPTY = "create.published.empty"
        const val READ_ONLY_BOARD = "create.studio.board.readOnly"
        const val STUDIO_READ_ONLY = "create.published.readOnly"
        const val TICKET = "create.published.ticket"
        const val CODE = "create.published.code"
        const val AUTHOR_TIME = "create.published.authorTime"
        const val PAGE_DUPLICATE = "create.publishedPage.duplicate"

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
