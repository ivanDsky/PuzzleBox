package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.FakeEditorController
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
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * Publish reserves the share code on the stored draft; the Studio's autosave, which writes
 * whole rows, must carry that code on every later save instead of writing the row it held
 * before the publish.
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

    private val controller: FakeEditorController get() = ui.controllers.single()

    @Test
    fun `an edit after a publish keeps the reserved code on the stored draft`() = runTest(mainRule.testDispatcher) {
        val board = editor.draft(marks = 1)
        drafts.upsert(storedRow(board))
        val vm = EditorViewModel(
            EditorKey(DRAFT_ID), drafts, games, GameEditorRegistry(listOf(ui)), GamePlayRegistry(emptyList()),
            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }), lanes, backgroundScope,
            clock = { NOW },
            createShared = CreateSharedPuzzle(
                drafts = drafts,
                shared = shared,
                published = FakePublishedPuzzleRepository(),
                identity = FakeIdentityService(Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null)),
                validate = ValidatePublish(),
                random = Random(0),
                clock = { NOW },
            ),
        )
        runCurrent()

        vm.publish.publish(board, Verdict.Unique(Solution(listOf(1))))
        runCurrent()
        val outcome = vm.publish.state.value.outcome
        val code = (outcome as? PublishOutcome.Published)?.code
        assertNotNull("published, got $outcome", code)

        controller.edit(editor.draft(marks = 2))
        advanceTimeBy(WINDOW_MS + 1)

        val stored = drafts.rows.getValue(DRAFT_ID)
        assertEquals("the edit was stored", 2, editor.decodeDraft(stored.blob).getOrNull()?.draft?.marks)
        assertEquals("the reserved code survives the autosave", code?.value, stored.reservedCode)
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
