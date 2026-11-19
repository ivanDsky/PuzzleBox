package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.FakeDraftCompleter
import com.ivandsky.puzzlebox.create.FakeEditorController
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.FakeScoredGrader
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
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
 * Finish for me wired into [EditorViewModel] (E1): the
 * tool is disabled until Ready and a settled verdict; a completion goes through the normal change
 * path (the controller adopts it, the verdict re-checks, autosave stores it) as one undoable step.
 */
class FinishViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val editor = FakeDraftCompleter()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainRule.testDispatcher
        override val io: CoroutineDispatcher = mainRule.testDispatcher
        override val default: CoroutineDispatcher = mainRule.testDispatcher
    }

    private val controller: FakeEditorController get() = ui.controllers.single()

    private suspend fun TestScope.open(
        marks: Int = 2,
        game: FakeGameEditor = editor,
        choice: FinishLevelChoice = FinishLevelChoice(),
    ): EditorViewModel {
        drafts.upsert(storedRow(game.draft(marks).encode()))
        val vm = EditorViewModel(
            EditorKey(DRAFT_ID), drafts, GameRegistry(listOf(withEditor(game))), GameEditorRegistry(listOf(ui)), GamePlayRegistry(emptyList()),
            CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }), lanes, backgroundScope, clock = { NOW },
            createShared = signedOutPublishing(drafts), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(),
            idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing(), preferences = FakeUserPreferencesRepository(),
            finishRandom = Random(SEED), finishLevel = choice,
        )
        runCurrent()
        return vm
    }

    /** Lets the debounced check of the open board settle. */
    private fun TestScope.settled() {
        advanceTimeBy(CHECK_MS)
        runCurrent()
    }

    private fun EditorViewModel.canFinish(): Boolean =
        finish.enabled(controller.draft.value, (state.value as EditorUiState.Ready).verdict)

    private fun added(count: Int): List<Int> = Random(SEED).let { random -> List(count) { random.nextInt(1, 10) } }

    @Test
    fun `it starts disabled on a blank board and while the first check runs`() = runTest(mainRule.testDispatcher) {
        val vm = open(marks = 0)
        assertFalse("blank: needs more, and Checking", vm.canFinish())
        settled()
        assertFalse("blank: the completer needs more", vm.canFinish())
    }

    @Test
    fun `it enables once Ready and the verdict has settled, and drops back to off while an edit is checked`() = runTest(mainRule.testDispatcher) {
        val vm = open(marks = 2)
        assertFalse("Checking keeps it off", vm.canFinish())

        settled()
        assertTrue(vm.canFinish())

        controller.edit(editor.draft(marks = 3))
        runCurrent()
        assertFalse("the edit's recheck is Checking", vm.canFinish())
        settled()
        assertTrue(vm.canFinish())
    }

    @Test
    fun `it stays off when the verdict is None`() = runTest(mainRule.testDispatcher) {
        editor.analysis = { _, _ -> Verdict.None(setOf(0)) }
        val vm = open(marks = 2)
        settled()

        assertFalse(vm.canFinish())
    }

    @Test
    fun `a game without a completer has no Finish even with the flag on`() = runTest(mainRule.testDispatcher) {
        val plain = FakeGameEditor()
        val vm = open(marks = 2, game = plain)
        settled()

        assertFalse(vm.canFinish())
        vm.finish.press()
        runCurrent()
        assertTrue(controller.adopted.isEmpty())
    }

    @Test
    fun `a press lands one undoable completion through the change path`() = runTest(mainRule.testDispatcher) {
        val vm = open(marks = 2)
        settled()
        editor.analyzed.clear()

        vm.finish.press()
        assertTrue(vm.finish.busy.value)
        runCurrent()

        val completion = TestDraft(2 + added(1).single())
        assertEquals("the board adopts it", completion, controller.adopted.single().state.draft)
        assertFalse(vm.finish.busy.value)
        assertTrue("one undo step", vm.canUndo.value)
        settled()
        assertEquals("the verdict re-checks it", listOf(completion), editor.analyzed)
        advanceTimeBy(WINDOW_MS)
        assertArrayEquals("autosave stores it", controller.adopted.single().encode(), drafts.rows.getValue(DRAFT_ID).blob)

        vm.undo()
        assertEquals("undo returns the author's pieces", TestDraft(2), controller.adopted.last().state.draft)
        assertFalse("and that was the only step", vm.canUndo.value)
    }

    @Test
    fun `pressing again completes the author's pieces, not the last completion, and undo goes back one`() = runTest(mainRule.testDispatcher) {
        val vm = open(marks = 2)
        settled()
        vm.finish.press()
        runCurrent()
        settled()
        assertTrue("the tool stays enabled over its own completion", vm.canFinish())

        vm.finish.press()
        runCurrent()

        val (first, second) = added(2)
        assertEquals(listOf(TestDraft(2), TestDraft(2)), editor.completed)
        assertEquals(TestDraft(2 + second), controller.adopted.last().state.draft)
        vm.undo()
        assertEquals("undo returns the previous completion", TestDraft(2 + first), controller.adopted.last().state.draft)
        vm.undo()
        assertEquals(TestDraft(2), controller.adopted.last().state.draft)
    }

    @Test
    fun `an edit, an undo, a resize or a clear ends the run, so the next press starts from the board as it stands`() = runTest(mainRule.testDispatcher) {
        val vm = open(marks = 2)
        settled()
        vm.finish.press()
        runCurrent()
        assertTrue(vm.finish.repeating.value)

        controller.edit(editor.draft(marks = 4))
        runCurrent()
        assertFalse(vm.finish.repeating.value)
        settled()
        vm.finish.press()
        runCurrent()
        assertEquals(TestDraft(4), editor.completed.last())

        vm.undo()
        assertFalse("undo ends the run", vm.finish.repeating.value)
        settled()
        vm.finish.press()
        runCurrent()
        assertTrue(vm.finish.repeating.value)
        vm.clear()
        assertFalse("clear ends the run", vm.finish.repeating.value)
    }

    @Test
    fun `a completer that gives up on a board with its answers changes nothing and asks for changes`() = runTest(mainRule.testDispatcher) {
        editor.completionOf = { _, _, _ -> Completion.GaveUp }
        val vm = open(marks = 2)
        settled()

        vm.finish.press()
        runCurrent()

        assertTrue(controller.adopted.isEmpty())
        assertFalse(vm.canUndo.value)
        assertEquals(FinishNotice.NoFix, vm.finish.notices.pending.value)
    }

    @Test
    fun `a completer that gives up on a board with room left asks for more pieces`() = runTest(mainRule.testDispatcher) {
        editor.analysis = { _, _ -> Verdict.Incomplete(IncompleteReason("fake.unpainted", 3)) }
        editor.completionOf = { _, _, _ -> Completion.GaveUp }
        val vm = open(marks = 2)
        settled()

        vm.finish.press()
        runCurrent()

        assertEquals(FinishNotice.NeedsMore, vm.finish.notices.pending.value)
    }

    @Test
    fun `a repeat that gives up is judged by the author's pieces, not by the completion on the board`() = runTest(mainRule.testDispatcher) {
        editor.analysis = { draft, _ ->
            if (draft.marks == 2) Verdict.Incomplete(IncompleteReason("fake.unpainted", 3)) else FakeGameEditor.defaultVerdict(draft)
        }
        val vm = open(marks = 2)
        settled()
        vm.finish.press()
        runCurrent()
        settled()
        editor.completionOf = { _, _, _ -> Completion.GaveUp }

        vm.finish.press()
        runCurrent()

        assertEquals("the pieces were Incomplete; the Unique board is Finish's own", FinishNotice.NeedsMore, vm.finish.notices.pending.value)
    }

    @Test
    fun `a press with a level hands it to the completer and keeps it as the choice`() = runTest(mainRule.testDispatcher) {
        val vm = open(marks = 2)
        settled()
        assertEquals("Medium until the author chooses", Difficulty.Level.Medium, vm.finish.level.value)

        vm.finish.press(Difficulty.Level.Hard)
        runCurrent()
        vm.finish.press()
        runCurrent()

        assertEquals(listOf<Difficulty.Level?>(Difficulty.Level.Hard, Difficulty.Level.Hard), editor.targets)
        assertEquals(Difficulty.Level.Hard, vm.finish.level.value)
    }

    @Test
    fun `the next Studio of the session opens on the last level chosen`() = runTest(mainRule.testDispatcher) {
        val session = FinishLevelChoice()
        val first = open(marks = 2, choice = session)
        settled()
        first.finish.press(Difficulty.Level.Expert)
        runCurrent()

        val second = open(marks = 2, choice = session)

        assertEquals(Difficulty.Level.Expert, second.finish.level.value)
    }

    @Test
    fun `a press the gate turns away does not change the choice`() = runTest(mainRule.testDispatcher) {
        val vm = open(marks = 0)
        settled()

        vm.finish.press(Difficulty.Level.Expert)
        runCurrent()

        assertEquals(Difficulty.Level.Medium, vm.finish.level.value)
        assertTrue(editor.targets.isEmpty())
    }

    @Test
    fun `a board finished at another level says which, once its check settles`() = runTest(mainRule.testDispatcher) {
        val scored = FakeScoredGrader().apply { levelOf = { Difficulty.Level.Medium } }
        val vm = open(marks = 2, game = scored)
        settled()

        vm.finish.press(Difficulty.Level.Hard)
        runCurrent()
        assertNull("nothing is said while the completion is still being checked", vm.finish.notices.pending.value)
        settled()

        assertEquals(FinishNotice.Missed(got = Difficulty.Level.Medium, wanted = Difficulty.Level.Hard), vm.finish.notices.pending.value)
    }

    @Test
    fun `a board finished at the level asked for says nothing`() = runTest(mainRule.testDispatcher) {
        val scored = FakeScoredGrader().apply { levelOf = { Difficulty.Level.Hard } }
        val vm = open(marks = 2, game = scored)
        settled()

        vm.finish.press(Difficulty.Level.Hard)
        runCurrent()
        settled()

        assertEquals(1, controller.adopted.size)
        assertNull(vm.finish.notices.pending.value)
    }

    @Test
    fun `a finish that changed the author's pieces says how many as it lands`() = runTest(mainRule.testDispatcher) {
        editor.changedOf = { 2 }
        val vm = open(marks = 2)
        settled()

        vm.finish.press()
        runCurrent()

        assertEquals(1, controller.adopted.size)
        assertEquals(FinishNotice.Recoloured(2), vm.finish.notices.pending.value)
    }

    @Test
    fun `an edit before the completion's check settles drops the comparison`() = runTest(mainRule.testDispatcher) {
        val scored = FakeScoredGrader().apply { levelOf = { Difficulty.Level.Medium } }
        val vm = open(marks = 2, game = scored)
        settled()
        vm.finish.press(Difficulty.Level.Hard)
        runCurrent()

        controller.edit(scored.draft(marks = 1))
        runCurrent()
        settled()

        assertNull("the author's own board was never asked for a level", vm.finish.notices.pending.value)
    }

    @Test
    fun `the Studio's verdict carries the scored level`() = runTest(mainRule.testDispatcher) {
        val scored = FakeScoredGrader().apply { levelOf = { Difficulty.Level.Expert } }
        val vm = open(marks = 2, game = scored)
        settled()

        val verdict = (vm.state.value as EditorUiState.Ready).verdict as Verdict.Unique
        assertEquals(Difficulty(Difficulty.Level.Expert, score = 2, hardestRung = null), verdict.difficulty)
    }

    internal companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val WINDOW_MS = 300L
        const val CHECK_MS = 1_700L
        const val SEED = 11

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
                playAgainSession = base.playAgainSession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}
