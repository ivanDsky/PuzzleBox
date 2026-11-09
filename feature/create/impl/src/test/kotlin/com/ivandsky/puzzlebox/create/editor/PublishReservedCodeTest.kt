package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
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
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Publish reserves the share code on the stored draft; the Studio's autosave, which writes
 * whole rows, must carry that code on a save made while the publish is still unsettled (its
 * answer lost), so the retry reuses it instead of committing a second doc.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PublishReservedCodeTest {

    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi()
    private val games = GameRegistry(listOf(pluginOf(editor)))
    private val drafts = FakeDraftRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainRule.testDispatcher
        override val io: CoroutineDispatcher = mainRule.testDispatcher
        override val default: CoroutineDispatcher = mainRule.testDispatcher
    }

    private var now: Instant = NOW

    @Test
    fun `a save that changes no published content keeps the reserved code, so the retry leaves one doc`() = runTest(mainRule.testDispatcher) {
        val board = editor.draft(marks = 1)
        val vm = openStudio(board)
        val verdict = Verdict.Unique(Solution(listOf(1)))
        val committed = publishLosingTheAnswer(vm, board, verdict)

        // The same board handed back: the whole row is saved again, but a friend sees nothing new.
        val stored = drafts.rows.getValue(DRAFT_ID).updatedAt
        now = now.plusSeconds(60)
        ui.controllers.single().edit(board)
        advanceTimeBy(WINDOW_MS + 1)
        assertNotEquals("the row was rewritten", stored, drafts.rows.getValue(DRAFT_ID).updatedAt)
        assertEquals("the reserved code survives the autosave", committed.value, drafts.rows.getValue(DRAFT_ID).reservedCode)

        vm.publish.publish(board, verdict)
        runCurrent()
        assertEquals(PublishOutcome.Published(committed), vm.publish.state.value.outcome)
        assertEquals("exactly one doc", setOf(committed), shared.docs.keys)
    }

    @Test
    fun `a rename between a lost answer and the retry keeps the reserved code, and the retry publishes the new title under a new code`() = runTest(mainRule.testDispatcher) {
        val board = editor.draft(marks = 1)
        val vm = openStudio(board)
        val verdict = Verdict.Unique(Solution(listOf(1)))
        val committed = publishLosingTheAnswer(vm, board, verdict)

        // Not published as far as the Studio knows, so a rename still saves the whole row.
        vm.meta.rename("Renamed")
        advanceTimeBy(WINDOW_MS + 1)
        assertEquals("the reserved code survives the autosave", committed.value, drafts.rows.getValue(DRAFT_ID).reservedCode)

        vm.publish.publish(board, verdict)
        runCurrent()
        val retried = (vm.publish.state.value.outcome as PublishOutcome.Published).code
        assertNotEquals("the old code holds other content, so the retry takes a new one", committed, retried)
        // The republish retires the old doc, so no friend is left on it.
        assertEquals(setOf(committed, retried), shared.docs.keys)
        assertTrue("the old doc is unpublished", shared.docs.getValue(committed).unpublished)
        assertEquals("Castle", shared.docs.getValue(committed).title)
        assertEquals("Renamed", shared.docs.getValue(retried).title)
    }

    private suspend fun TestScope.openStudio(board: EditorDraft<TestDraft>): EditorViewModel {
        val author = FakeIdentityService(Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null))
        drafts.upsert(storedRow(board))
        val vm = EditorViewModel(
            EditorKey(DRAFT_ID), drafts, games, GameEditorRegistry(listOf(ui)), GamePlayRegistry(emptyList()),
            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }), lanes, backgroundScope,
            clock = { now },
            createShared = CreateSharedPuzzle(
                drafts = drafts,
                shared = shared,
                published = FakePublishedPuzzleRepository(),
                identity = author,
                validate = ValidatePublish(),
                random = Random(0),
                clock = { NOW },
            ),
            publishedPuzzles = FakePublishedPuzzleRepository(),
            identity = author,
            idTokens = noGoogleAccount,
            updatePublishState = signedOutUnpublishing(),
            preferences = FakeUserPreferencesRepository(),
        )
        runCurrent()
        return vm
    }

    /** Publishes once; the doc commits but the answer is lost. Returns the code the doc holds. */
    private fun TestScope.publishLosingTheAnswer(vm: EditorViewModel, board: EditorDraft<TestDraft>, verdict: Verdict): ShareCode {
        shared.commitThenOffline = true
        vm.publish.publish(board, verdict)
        runCurrent()
        assertEquals(PublishOutcome.Offline, vm.publish.state.value.outcome)
        return shared.docs.keys.single()
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
        const val DRAFT_ID = "draft-1"
        const val AUTHOR = "author"
        const val WINDOW_MS = 300L

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
