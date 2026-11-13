package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
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
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * A taken share code is replaced during a publish. An autosave of the Studio's row that lands
 * after the replacement and before the publish returns must carry the new code, so the draft
 * ends up holding the code its doc was published under (a retry after a lost answer finds it).
 */
class PublishRegeneratedCodeTest {

    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val editor = SharingEditor()
    private val ui = FakeGameEditorUi()
    private val games = GameRegistry(listOf(pluginOf(editor)))
    private val drafts = FakeDraftRepository()
    private val backend = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainRule.testDispatcher
        override val io: CoroutineDispatcher = mainRule.testDispatcher
        override val default: CoroutineDispatcher = mainRule.testDispatcher
    }

    /** First try: taken. Second try: an autosave of the Studio's row fires before the answer. */
    private val takenThenAutosaved = object : SharedPuzzleRepository by backend {
        private var tries = 0

        override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
            if (++tries == 1) return PublishResult.CodeTaken
            ui.controllers.single().edit(board)
            delay(WINDOW_MS + 1)
            return backend.publish(doc)
        }
    }

    private val board = editor.draft(marks = 1)

    @Test
    fun `an autosave landing after a code regeneration keeps the new code on the draft`() = runTest(mainRule.testDispatcher) {
        val vm = openStudio()

        vm.publish.publish(board, Verdict.Unique(Solution(listOf(1))))
        runCurrent()
        mainRule.testDispatcher.scheduler.advanceUntilIdle()

        val code = (vm.publish.state.value.outcome as PublishOutcome.Published).code
        assertEquals("one doc, under the regenerated code", setOf(code), backend.docs.keys)
        assertEquals("the draft holds the code it was published under", code.value, drafts.rows.getValue(DRAFT_ID).reservedCode)
    }

    @Test
    fun `the regenerated code is stored before the next try goes out`() = runTest(mainRule.testDispatcher) {
        val seen = mutableListOf<String?>()
        val recording = object : SharedPuzzleRepository by backend {
            override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
                seen += drafts.rows.getValue(DRAFT_ID).reservedCode
                return if (seen.size == 1) PublishResult.CodeTaken else backend.publish(doc)
            }
        }
        val vm = openStudio(recording)

        vm.publish.publish(board, Verdict.Unique(Solution(listOf(1))))
        mainRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("a try per code", 2, seen.size)
        assertNotEquals("the second try runs under a new code", seen[0], seen[1])
        assertEquals("both tries find their code on the draft", 2, seen.filterNotNull().size)
    }

    private suspend fun TestScope.openStudio(repository: SharedPuzzleRepository = takenThenAutosaved): EditorViewModel {
        val author = FakeIdentityService(Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null))
        drafts.upsert(storedRow(board))
        val vm = EditorViewModel(
            EditorKey(DRAFT_ID), drafts, games, GameEditorRegistry(listOf(ui)), GamePlayRegistry(emptyList()),
            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }), lanes, backgroundScope,
            clock = { NOW },
            createShared = CreateSharedPuzzle(
                drafts = drafts,
                shared = repository,
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
                playAgainSession = base.playAgainSession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}
