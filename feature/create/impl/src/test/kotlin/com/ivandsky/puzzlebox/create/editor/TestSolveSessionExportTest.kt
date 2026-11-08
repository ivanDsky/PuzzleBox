package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.coroutines.CoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `start` builds the puzzle on the provider's `default` dispatcher, then opens the sandbox on the
 * session's own: a second start during the export is ignored and a [TestSolveSession.close] or
 * scope end during it drops the result.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestSolveSessionExportTest {

    private val scheduler = TestCoroutineScheduler()
    private val callerDispatcher = MarkedDispatcher(StandardTestDispatcher(scheduler))
    private val defaultDispatcher = MarkedDispatcher(StandardTestDispatcher(scheduler))
    private val scope = CoroutineScope(SupervisorJob() + callerDispatcher)
    private val editor = RecordingEditor { defaultDispatcher.running to callerDispatcher.running }
    private val play = ExportPlayUi(editor.gameId)
    private val stored = mutableListOf<Any>()

    private val session = TestSolveSession(
        draftId = "draft-1",
        plays = GamePlayRegistry(listOf(play)),
        games = GameRegistry(listOf(pluginWith(editor))),
        dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = callerDispatcher
            override val io: CoroutineDispatcher = defaultDispatcher
            override val default: CoroutineDispatcher = defaultDispatcher
        },
        scope = scope,
        store = { stored += it },
    )

    private val draft = EditorDraft(editor, DraftState(1))
    private val unique = Verdict.Unique(Solution(listOf(1)))

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `the puzzle is built on the provider's default, not the caller's dispatcher`() {
        session.start(draft, unique)
        scheduler.advanceUntilIdle()

        assertEquals(listOf(true to false), editor.exports)
    }

    @Test
    fun `the sandbox opens only after the export finishes`() {
        session.start(draft, unique)
        callerDispatcher.drain()
        assertNull(session.sandbox.value)
        assertEquals(0, editor.exports.size)

        defaultDispatcher.drain()
        assertEquals(1, editor.exports.size)
        assertNull(session.sandbox.value)

        callerDispatcher.drain()
        assertNotNull(session.sandbox.value)
        assertEquals(1, play.controllers.size)
    }

    @Test
    fun `a second start during the export is ignored`() {
        session.start(draft, unique)
        session.start(draft, unique)
        scheduler.advanceUntilIdle()

        assertEquals(1, editor.exports.size)
        assertEquals(1, play.controllers.size)
    }

    @Test
    fun `a start after the export finished is ignored while the sandbox is open`() {
        session.start(draft, unique)
        scheduler.advanceUntilIdle()
        session.start(draft, unique)
        scheduler.advanceUntilIdle()

        assertEquals(1, editor.exports.size)
    }

    @Test
    fun `close during the export opens no sandbox`() {
        session.start(draft, unique)
        defaultDispatcher.drain()
        session.close()
        scheduler.advanceUntilIdle()

        assertNull(session.sandbox.value)
        assertTrue(play.controllers.isEmpty())
    }

    @Test
    fun `start works again after a close during the export`() {
        session.start(draft, unique)
        session.close()
        scheduler.advanceUntilIdle()
        session.start(draft, unique)
        scheduler.advanceUntilIdle()

        assertNotNull(session.sandbox.value)
    }

    @Test
    fun `the scope ending during the export opens no sandbox`() {
        session.start(draft, unique)
        defaultDispatcher.drain()
        scope.cancel()
        scheduler.advanceUntilIdle()

        assertNull(session.sandbox.value)
        assertTrue(play.controllers.isEmpty())
    }

    @Test
    fun `the test is keyed by the layout that was exported`() {
        session.start(draft, unique)
        defaultDispatcher.drain()
        session.follow(EditorDraft(editor, DraftState(2)))
        scheduler.advanceUntilIdle()
        play.controllers.single().win()

        assertEquals(draft.layoutKey, (stored.single() as TestSolve).layoutKey)
    }

    private fun pluginWith(editor: RecordingEditor): GamePlugin<*, *, *> {
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

/** Runs queued work on [delegate]'s scheduler; [running] is true while a block of it executes. */
@OptIn(ExperimentalCoroutinesApi::class)
private class MarkedDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    var running = false
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue += Runnable { run(block) }
        delegate.dispatch(context, Runnable { drainOne() })
    }

    /**
     * Runs what this dispatcher has queued so far; the scheduler's own wake-ups then find
     * nothing.
     */
    fun drain() {
        while (queue.isNotEmpty()) drainOne()
    }

    private fun drainOne() {
        queue.removeFirstOrNull()?.run()
    }

    private fun run(block: Runnable) {
        running = true
        try {
            block.run()
        } finally {
            running = false
        }
    }
}

/** An exporting editor that records which dispatcher each [toPuzzle] ran on. */
private class RecordingEditor(private val where: () -> Pair<Boolean, Boolean>) :
    GameEditor<Int>, DraftExport<Int> {
    override val gameId: GameId = GameId("export")
    override val variants: List<VariantId> = listOf(VariantId("export/v1"))
    override val draftFormatVersion: Int = 1

    /** Per export: (on default, on caller). */
    val exports = mutableListOf<Pair<Boolean, Boolean>>()

    override fun newDraft(variant: VariantId): Int = 0

    override fun variantOf(draft: Int): VariantId = variants.single()

    override fun encodeDraft(state: DraftState<Int>): ByteArray = byteArrayOf(state.draft.toByte())

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<Int>> =
        DecodeResult.Ok(DraftState(bytes.single().toInt()))

    override fun analyze(draft: Int, budget: Budget): Verdict = Verdict.Unique(Solution(listOf(draft)))

    override fun layoutOf(draft: Int): Int = draft

    override fun toPuzzle(draft: Int, solution: Solution): Puzzle {
        exports += where()
        return Puzzle(gameId, variants.single(), 1, byteArrayOf(TestGames.MAGIC, draft.toByte()))
    }
}

private class ExportPlayUi(override val gameId: GameId) : GamePlayUi {

    val controllers = mutableListOf<ExportController>()

    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController =
        ExportController(env).also { controllers += it }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        Box(modifier)
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}

private class ExportController(private val env: PlayEnvironment) : GamePlayController {
    private var ref: PuzzleRef? = null

    override val isComplete: Boolean get() = false

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        this.ref = ref
        return Adopt.Adopted
    }

    override fun adoptRestored(bytes: ByteArray): Restored? = null

    override fun replay() = Unit

    fun win() {
        env.onWon(
            CompletionRecord(
                ref!!, time = 1_000, hints = 0, stats = RecordStats(1, ""),
            ),
        )
    }
}
