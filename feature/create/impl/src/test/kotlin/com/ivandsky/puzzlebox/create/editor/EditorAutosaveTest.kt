package com.ivandsky.puzzlebox.create.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.FakeEditorController
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Studio's autosave wired into [EditorViewModel] (spec A5; 16 handoff): an edit is stored
 * once the 300 ms window ends, app stop and leaving the Studio store it at once, and Duplicate
 * and Delete store it first, so the copy and the Create root's Undo both have it; after
 * Delete no late save brings the draft back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorAutosaveTest {

    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val editor = FakeGameEditor()
    private val ui = FakeGameEditorUi()
    private val games = GameRegistry(listOf(withEditor(editor)))
    private val editors = GameEditorRegistry(listOf(ui))
    private val drafts = FakeDraftRepository()

    /** Every row handed to the store, in order. */
    private val writes = mutableListOf<Draft>()
    private val store = object : DraftRepository by drafts {
        override suspend fun upsert(draft: Draft) {
            writes += draft
            drafts.upsert(draft)
        }
    }

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainRule.testDispatcher
        override val io: CoroutineDispatcher = mainRule.testDispatcher
        override val default: CoroutineDispatcher = mainRule.testDispatcher
    }
    private val copyDraft = CreateDraftCopy(drafts, newId = { COPY_ID }, clock = { NOW })

    private suspend fun TestScope.open(saves: CoroutineScope = backgroundScope): EditorViewModel {
        drafts.upsert(storedRow(editor.draft(marks = 1).encode()))
        val vm = EditorViewModel(EditorKey(DRAFT_ID), store, games, editors, GamePlayRegistry(emptyList()), copyDraft, lanes, saves, clock = { NOW }, createShared = signedOutPublishing(store), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(), idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing())
        runCurrent()
        return vm
    }

    private val controller: FakeEditorController get() = ui.controllers.single()

    private val edited = editor.draft(marks = 5)

    @Test
    fun `an edit is stored once the 300 ms window ends, not before`() = runTest(mainRule.testDispatcher) {
        val vm = open()
        controller.edit(edited)
        runCurrent()
        assertTrue("saving while the edit waits", vm.saving.value)

        advanceTimeBy(WINDOW_MS - 1)
        assertTrue("nothing stored inside the window", writes.isEmpty())

        advanceTimeBy(2)
        assertArrayEquals(edited.encode(), drafts.rows.getValue(DRAFT_ID).blob)
        assertFalse("saved once the edit is stored", vm.saving.value)
    }

    @Test
    fun `edits inside one window are stored once, as the newest`() = runTest(mainRule.testDispatcher) {
        open()
        controller.edit(editor.draft(marks = 2))
        advanceTimeBy(100)
        controller.edit(editor.draft(marks = 3))
        advanceTimeBy(100)
        controller.edit(edited)
        advanceTimeBy(WINDOW_MS)

        assertEquals(1, writes.size)
        assertArrayEquals(edited.encode(), writes.single().blob)
    }

    @Test
    fun `a stored edit carries the editor's draft format version`() = runTest(mainRule.testDispatcher) {
        drafts.upsert(storedRow(editor.draft(marks = 1).encode()).copy(draftFormatVersion = 0))
        EditorViewModel(EditorKey(DRAFT_ID), store, games, editors, GamePlayRegistry(emptyList()), copyDraft, lanes, backgroundScope, clock = { NOW }, createShared = signedOutPublishing(store), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(), idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing())
        runCurrent()

        controller.edit(edited)
        advanceTimeBy(WINDOW_MS + 1)

        assertEquals(editor.draftFormatVersion, drafts.rows.getValue(DRAFT_ID).draftFormatVersion)
    }

    @Test
    fun `a flush on app stop stores the pending edit at once`() = runTest(mainRule.testDispatcher) {
        val vm = open()
        controller.edit(edited)
        vm.flush()
        runCurrent()

        assertArrayEquals(edited.encode(), drafts.rows.getValue(DRAFT_ID).blob)
        advanceTimeBy(WINDOW_MS * 2)
        assertEquals("the closed window stores nothing more", 1, writes.size)
    }

    @Test
    fun `leaving the Studio stores the pending edit and leaves the shared saves scope running`() = runTest(mainRule.testDispatcher) {
        val saves = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val vm = open(saves)
        val owner = ViewModelStore()
        ViewModelProvider(owner, factoryOf(vm))[EditorViewModel::class.java]
        controller.edit(edited)

        owner.clear()
        runCurrent()

        assertArrayEquals(edited.encode(), drafts.rows.getValue(DRAFT_ID).blob)
        assertTrue("the next Studio saves through the same scope", saves.isActive)
    }

    @Test
    fun `delete right after an edit stays deleted, and undo restores the edited board`() = runTest(mainRule.testDispatcher) {
        val vm = open()
        val create = createViewModel()
        controller.edit(edited)

        vm.delete { draftId -> create.delete(draftId) }
        runCurrent()
        assertNull("deleted once the edit is stored", drafts.rows[DRAFT_ID])
        controller.edit(editor.draft(marks = 9))

        advanceTimeBy(WINDOW_MS * 2)
        assertNull("no late save brings it back", drafts.rows[DRAFT_ID])
        assertEquals("only the edit before Delete was stored", 1, writes.size)
        assertFalse(vm.saving.value)

        create.undoDelete()
        runCurrent()
        assertArrayEquals("undo restores the board with the edit", edited.encode(), drafts.rows.getValue(DRAFT_ID).blob)
    }

    @Test
    fun `duplicate right after an edit copies the edited board`() = runTest(mainRule.testDispatcher) {
        val vm = open()
        controller.edit(edited)
        var opened: String? = null

        vm.duplicate { draftId -> opened = draftId }
        runCurrent()

        assertEquals(COPY_ID, opened)
        assertArrayEquals(edited.encode(), drafts.rows.getValue(COPY_ID).blob)
        assertArrayEquals(edited.encode(), drafts.rows.getValue(DRAFT_ID).blob)
    }

    private fun createViewModel() = CreateViewModel(
        games,
        editors,
        GamePlayRegistry(emptyList()),
        drafts,
        FakePublishedPuzzleRepository(),
        CreateDraft(games, drafts, newId = { "new" }, clock = { NOW }),
        copyDraft,
        lanes,
        syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
        clock = { NOW },
    )

    private fun factoryOf(vm: EditorViewModel) = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(vm)!!
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val COPY_ID = "copy-1"
        const val WINDOW_MS = 300L

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
