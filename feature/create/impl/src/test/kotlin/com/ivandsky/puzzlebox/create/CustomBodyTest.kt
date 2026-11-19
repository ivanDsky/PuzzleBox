package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.ImportResult
import com.ivandsky.puzzlebox.create.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The Custom list body over the real activity-scoped [CreateViewModel]: a New puzzle pick stores a draft and reports its
 * id; a draft row keeps its actions (open, duplicate, delete with Undo); the sections sit in the order drafts,
 * published; text another app shared imports while the body shows; the published rows get their solve counts; and with nothing in any section the Create empty state shows instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class CustomBodyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = NotatedEditor()
    private val games = GameRegistry(listOf(pluginOf(editor)))
    private val drafts = FakeDraftRepository()
    private val published = FakePublishedPuzzleRepository()
    private val boards = FakeCustomBoardRepository()
    private val store = FakeSharedTextStore()
    private val openedDrafts = mutableListOf<String>()
    private val openedPublished = mutableListOf<String>()
    private lateinit var vm: CreateViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a New puzzle pick stores a draft and reports its id`() {
        store(draftRow("plain", code = null))
        show()
        awaitTag(NEW)

        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag(SHEET_GAME).performClick()

        awaitTrue { openedDrafts.isNotEmpty() }
        assertEquals(listOf("draft-1"), openedDrafts)
        assertEquals(setOf("plain", "draft-1"), drafts.rows.keys)
        assertFalse(composeRule.exists(NEW_SHEET))
    }

    @Test
    fun `the sections sit in order - drafts, published`() {
        store(draftRow("plain", code = null))
        storePublished(CODE, "shared")
        show()
        awaitTag(PUBLISHED_ROW)

        val tops = listOf(NEW, CONTINUE, DRAFT_ROW, PUBLISHED, PUBLISHED_ROW).map { tag ->
            composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
        }
        assertEquals("each section starts under the one before it", tops.sorted(), tops)
        assertEquals(tops.size, tops.distinct().size)
    }

    @Test
    fun `a draft row opens its draft and a published row its page`() {
        store(draftRow("plain", code = null))
        storePublished(CODE, "shared")
        show()
        awaitTag(PUBLISHED_ROW)

        composeRule.onNodeWithTag(DRAFT_ROW).performClick()
        composeRule.onNodeWithTag(PUBLISHED_ROW).performClick()

        assertEquals(listOf("plain"), openedDrafts)
        assertEquals(listOf(CODE.value), openedPublished)
    }

    @Test
    fun `Duplicate from a draft row's actions adds the copy under Continue`() {
        store(draftRow("plain", code = null))
        show()
        awaitCount(DRAFT_ROW, 1)

        composeRule.onNodeWithTag(DRAFT_MORE).performClick()
        composeRule.onNodeWithTag(ACTION_DUPLICATE).performClick()

        awaitCount(DRAFT_ROW, 2)
        assertEquals(setOf("plain", "copy"), drafts.rows.keys)
        assertEquals(emptyList<String>(), openedDrafts)
    }

    @Test
    fun `deleting the last draft offers Undo, and Undo brings it back`() {
        val row = draftRow("plain", code = null)
        store(row)
        show()
        awaitCount(DRAFT_ROW, 1)

        composeRule.onNodeWithTag(DRAFT_MORE).performClick()
        composeRule.onNodeWithTag(ACTION_DELETE).performClick()

        awaitTrue { composeRule.onAllNodes(hasText("Deleted “Title plain”")).fetchSemanticsNodes().size == 1 }
        assertEquals(emptySet<String>(), drafts.rows.keys)
        awaitCount(DRAFT_ROW, 0)

        composeRule.onNodeWithText("Undo").performClick()

        awaitCount(DRAFT_ROW, 1)
        assertEquals(mapOf("plain" to row), drafts.rows.toMap())
    }

    @Test
    fun `text another app shared imports one draft and reports its id`() {
        store.put(NOTATION)
        show()

        awaitTrue { openedDrafts.isNotEmpty() }

        assertEquals(listOf("draft-1"), openedDrafts)
        assertTrue(drafts.rows.getValue("draft-1").imported)
        assertNull(store.pending.value)
    }

    @Test
    fun `a malformed shared text stores nothing and leaves the import sheet open with the error`() {
        store.put("no header here")
        show()

        awaitTag(IMPORT_SHEET)
        awaitTrue { vm.importText.ui.value.error != null }

        assertTrue(vm.importText.ui.value.error is ImportResult.Malformed)
        assertEquals(emptySet<String>(), drafts.rows.keys)
        assertEquals(emptyList<String>(), openedDrafts)
    }

    @Test
    fun `showing the body fetches a published row's solves`() {
        storePublished(CODE, "shared")
        runBlocking {
            repeat(3) { n ->
                boards.submit(CODE, LeaderboardEntry("friend-$n", "Friend $n", "custom/${editor.gameId.value}/${CODE.value}", 60_000L + n, 0, NOW.toEpochMilli()))
            }
        }
        show()

        awaitCount(ROW_SOLVES, 1)
        assertEquals(3, vm.uiState.value.published.single().solves)
    }

    @Test
    fun `nothing made and no friends puzzles shows the empty state, whose game row starts a draft`() {
        show()
        awaitTag(EMPTY)
        assertFalse(composeRule.exists(NEW))

        composeRule.onNodeWithTag(EMPTY_GAME).performClick()

        awaitTrue { openedDrafts.isNotEmpty() }
        assertEquals(listOf("draft-1"), openedDrafts)
    }

    private fun show() {
        var ids = 0
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            GameEditorRegistry(listOf(FakeGameEditorUi())),
                            GamePlayRegistry(emptyList()),
                            drafts,
                            published,
                            CreateDraft(games, drafts, newId = { "draft-${++ids}" }, clock = { NOW }),
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = store,
                            syncSolves = SyncSolvesCounts(published, boards, clock = { NOW }),
                            clock = { NOW },
                            cloudSync = FakeCloudSync(),
                        ).also { vm = it }
                    }
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                CustomBody(
                    listState = rememberLazyListState(),
                    onOpenDraft = { openedDrafts += it },
                    onOpenPublished = { openedPublished += it },
                )
            }
        }
    }

    private fun storePublished(code: ShareCode, draftId: String) {
        store(draftRow(draftId, code = code))
        runBlocking {
            published.upsert(
                PublishedPuzzle(
                    code = code,
                    gameId = editor.gameId,
                    variant = editor.variants.first(),
                    title = "Title $draftId",
                    authorTimeMs = 102_000L,
                    authorHints = 0,
                    createdAt = NOW.minusSeconds(1_800),
                    unpublished = false,
                    solvesCount = null,
                    solvesCountAt = null,
                ),
            )
        }
    }

    private fun store(row: Draft) = runBlocking { drafts.upsert(row) }

    private fun draftRow(id: String, code: ShareCode?) = Draft(
        id = id,
        gameId = editor.gameId,
        variant = editor.variants.first(),
        draftFormatVersion = editor.draftFormatVersion,
        blob = byteArrayOf(1, 2),
        title = "Title $id",
        updatedAt = NOW,
        testSolve = null,
        reservedCode = code?.value,
        imported = false,
        authorName = "Ivan",
        note = "",
    )

    private fun ComposeTestRule.exists(tag: String): Boolean =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun awaitTag(tag: String) = awaitTrue { composeRule.exists(tag) }

    private fun awaitCount(tag: String, count: Int) =
        awaitTrue { composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size == count }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    /** The stand-in game with a notation: a header line, then the board's mark count. */
    private class NotatedEditor(
        private val base: FakeGameEditor = FakeGameEditor(),
    ) : GameEditor<TestDraft> by base, EditorNotation<TestDraft> {
        override val notationVersion: Int = 1

        override fun parse(text: String): NotationResult<TestDraft> {
            val marks = text.lines().firstOrNull { it.isNotBlank() }?.trim()?.toIntOrNull()
                ?: return NotationResult.Err(2, 1, "expected a mark count")
            return NotationResult.Ok(TestDraft(marks))
        }

        override fun print(draft: TestDraft): String = HEADER + "${draft.marks}\n"

        override fun columns(draft: TestDraft): Int = 1
    }

    private fun pluginOf(editor: GameEditor<TestDraft>): GamePlugin<*, *, *> {
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

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-16T10:00:00Z")
        val CODE: ShareCode = checkNotNull(ShareCode.parse("K7Q2MX"))
        val HEADER = NotationHeader("fake", 1).line + "\n"
        val NOTATION = HEADER + "3\n"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val NEW = "custom.new"
        const val CONTINUE = "custom.continue"
        const val PUBLISHED = "custom.published"
        const val NEW_SHEET = "create.new.sheet"
        const val SHEET_GAME = "create.new.game.fake"
        const val IMPORT_SHEET = "create.import.sheet"
        const val EMPTY = "create.empty"
        const val EMPTY_GAME = "create.empty.game.fake"
        const val DRAFT_ROW = "create.draft.row"
        const val DRAFT_MORE = "create.draft.more"
        const val PUBLISHED_ROW = "create.published.row"
        const val ROW_SOLVES = "create.published.row.solves"
        const val ACTION_DUPLICATE = "create.actions.duplicate"
        const val ACTION_DELETE = "create.actions.delete"
    }
}
