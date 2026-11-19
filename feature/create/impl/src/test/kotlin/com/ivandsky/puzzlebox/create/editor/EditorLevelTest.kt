package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.FakeScoredGrader
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The author's level kept on the draft (96), through the Studio's view model and a scored grader: a pick is stored with
 * the draft and survives leaving the Studio; a board change that moves the proposal takes an unchanged choice along and
 * keeps an explicit one, which then shows next to "Proposed: X".
 */
class EditorLevelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    /** Two marks or fewer grade Easy, more grade Hard. */
    private val game = FakeScoredGrader().apply { levelOf = { if (it.marks <= 2) Level.Easy else Level.Hard } }
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainRule.testDispatcher
        override val io: CoroutineDispatcher = mainRule.testDispatcher
        override val default: CoroutineDispatcher = mainRule.testDispatcher
    }

    private suspend fun TestScope.open(): EditorViewModel {
        if (drafts.rows[DRAFT_ID] == null) drafts.upsert(row(game.draft(2).encode()))
        val vm = EditorViewModel(
            EditorKey(DRAFT_ID), drafts, GameRegistry(listOf(FinishViewModelTest.withEditor(game))), GameEditorRegistry(listOf(ui)), GamePlayRegistry(emptyList()),
            CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }), lanes, backgroundScope, clock = { NOW },
            createShared = signedOutPublishing(drafts), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(),
            idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing(), preferences = FakeUserPreferencesRepository(),
        )
        runCurrent()
        settled()
        return vm
    }

    private fun TestScope.settled() {
        advanceTimeBy(CHECK_MS)
        runCurrent()
    }

    private fun EditorViewModel.verdict(): Verdict = (state.value as EditorUiState.Ready).verdict

    /** What the control shows for the open board, the way the Studio hands it to the details. */
    private fun EditorViewModel.choice(): LevelChoice? = LevelChoice.of(verdict(), publish.state.value.details.level)

    @Test
    fun `the Studio proposes the scored level and the control follows it`() = runTest(mainRule.testDispatcher) {
        val vm = open()

        assertEquals(LevelChoice(proposed = Level.Easy, chosen = Level.Easy), vm.choice())
        assertTrue(vm.publish.state.value.details.levelOffered)
    }

    @Test
    fun `a pick is stored with the draft and survives leaving the Studio`() = runTest(mainRule.testDispatcher) {
        val first = open()

        first.publish.pickLevel(Level.Expert, first.verdict())
        advanceTimeBy(WINDOW_MS)
        runCurrent()
        assertEquals(Level.Expert, drafts.rows.getValue(DRAFT_ID).difficultyChoice)

        val again = open()
        assertEquals(Level.Expert, again.publish.state.value.details.level)
        assertEquals(LevelChoice(proposed = Level.Easy, chosen = Level.Expert), again.choice())
    }

    @Test
    fun `a board change that moves the proposal takes an unchanged choice along`() = runTest(mainRule.testDispatcher) {
        val vm = open()
        val controller = ui.controllers.single()

        controller.edit(game.draft(marks = 5))
        runCurrent()
        settled()

        assertEquals(LevelChoice(proposed = Level.Hard, chosen = Level.Hard), vm.choice())
        assertNull(drafts.rows.getValue(DRAFT_ID).difficultyChoice)
    }

    @Test
    fun `a board change keeps an explicit pick and shows the new proposal beside it`() = runTest(mainRule.testDispatcher) {
        val vm = open()
        vm.publish.pickLevel(Level.Medium, vm.verdict())
        val controller = ui.controllers.single()

        controller.edit(game.draft(marks = 5))
        runCurrent()
        settled()
        advanceTimeBy(WINDOW_MS)
        runCurrent()

        val choice = vm.choice()!!
        assertEquals(Level.Medium, choice.chosen)
        assertEquals(Level.Hard, choice.proposed)
        assertTrue(choice.changed)
        assertEquals("the board's save keeps the pick", Level.Medium, drafts.rows.getValue(DRAFT_ID).difficultyChoice)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val WINDOW_MS = 300L
        const val CHECK_MS = 1_700L

        fun row(blob: ByteArray) = FinishViewModelTest.storedRow(blob)
    }
}
