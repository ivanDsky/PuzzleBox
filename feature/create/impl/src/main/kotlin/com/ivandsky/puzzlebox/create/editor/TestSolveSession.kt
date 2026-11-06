package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The draft's required test solve (spec A10, 00 §3): [start] opens the board as a sandbox on the
 * game's real play controller, and its win is captured as a [TestSolve] of the tested layout,
 * handed to [store] (the Studio's one writer). The win's record is read for time and hints and
 * dropped: it never reaches a `CompletionRepository`, a streak or a board. The sandbox has no
 * session persistence, so [close] discards its progress.
 *
 * The stored test is never deleted by an edit. It is [passed] while its layout key matches the
 * board's: a board edit silently invalidates it and sends [cleared] (the toast with Undo), and
 * undoing back to the tested layout makes it valid again. A title edit never reaches here, and an
 * aid-only edit (Queens answer crowns) keeps the layout key, so both keep the test.
 *
 * Confined to the main thread, like the Studio's other holders.
 */
class TestSolveSession(
    private val draftId: String,
    private val plays: GamePlayRegistry,
    private val games: GameRegistry,
    private val scope: CoroutineScope,
    private val store: (TestSolve) -> Unit,
) {

    /** The open sandbox: the game's play [ui] over [controller]; [result] once it is solved. */
    data class Sandbox(val ui: GamePlayUi, val controller: GamePlayController, val result: TestSolve? = null)

    /** The stored test, valid or not; null until one passes. */
    private var tested: TestSolve? = null

    /** The layout key of the board as it stands. */
    private var layoutKey: String? = null

    private val mutablePassed = MutableStateFlow<TestSolve?>(null)

    /** The stored test when it was solved on the board's current layout; null otherwise. */
    val passed: StateFlow<TestSolve?> = mutablePassed.asStateFlow()

    private val mutableSandbox = MutableStateFlow<Sandbox?>(null)

    /** The sandbox while the author is test solving; null in the editor. */
    val sandbox: StateFlow<Sandbox?> = mutableSandbox.asStateFlow()

    private val clears = Channel<Unit>(Channel.BUFFERED)

    /** One event per board edit that invalidates a passed test (`create.toast.testCleared`). */
    val cleared: Flow<Unit> = clears.receiveAsFlow()

    /** The sandbox's own scope, a child of [scope]; cancelled when the sandbox closes. */
    private var sandboxScope: CoroutineScope? = null

    /** The loaded draft and its stored [test]; no toast. */
    fun open(test: TestSolve?, draft: EditorDraft<*>) {
        tested = test
        layoutKey = draft.layoutKey
        mutablePassed.value = validFor(layoutKey)
    }

    /** Every board change (edit, undo, redo, resize, clear): a passed test that no longer fits is cleared, with the toast. */
    fun follow(draft: EditorDraft<*>) {
        val wasPassed = mutablePassed.value != null
        layoutKey = draft.layoutKey
        mutablePassed.value = validFor(layoutKey)
        if (wasPassed && mutablePassed.value == null) clears.trySend(Unit)
    }

    /**
     * Opens [draft] as a sandbox when the [verdict] is Unique and the game exports puzzles: the
     * plain puzzle from [EditorDraft.toPuzzle] (author aids never reach it) under the draft's
     * custom key. Ignored while a sandbox is open or when the game can't play the puzzle.
     */
    fun start(draft: EditorDraft<*>, verdict: Verdict) {
        if (mutableSandbox.value != null || verdict !is Verdict.Unique) return
        val puzzle = draft.toPuzzle(verdict.solution) ?: return
        val ui = plays[draft.gameId] ?: return
        val plugin = games[draft.gameId] ?: return
        val tested = draft.layoutKey
        val lifetime = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        val controller = ui.createController(
            PlayEnvironment(lifetime, onSessionChanged = {}, onWon = { record -> finish(tested, record.time, record.hints) }),
        )
        if (controller.adoptToday(puzzle, plugin.refOf(PuzzleKey.Custom("draft-$draftId"))) is Adopt.Rejected) {
            lifetime.cancel()
            return
        }
        sandboxScope = lifetime
        mutableSandbox.value = Sandbox(ui, controller)
    }

    /** Back to the editor: the sandbox and any unsolved progress are dropped. */
    fun close() {
        sandboxScope?.cancel()
        sandboxScope = null
        mutableSandbox.value = null
    }

    /** The sandbox's win: the test of [layout] is stored; the record itself goes nowhere. */
    private fun finish(layout: String, timeMs: Long, hints: Int) {
        val test = TestSolve(layout, timeMs, hints)
        tested = test
        mutablePassed.value = validFor(layoutKey)
        mutableSandbox.value = mutableSandbox.value?.copy(result = test)
        store(test)
    }

    private fun validFor(key: String?): TestSolve? = tested?.takeIf { it.layoutKey == key }
}
