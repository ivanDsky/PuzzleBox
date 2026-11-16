package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.FinishBudget
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Studio's Finish run (E1): each [press] completes the board on [DispatcherProvider.default] under
 * the budget [budgetMs] gives its target (90: [FinishBudget], longer for Hard and Expert), which also ends
 * when the run is cancelled, so the main thread never waits, and hands the completion to the board as one
 * step. A press while it runs cancels it, as does [boardChanged] by any other board (an edit, undo, redo,
 * resize or clear) and [scope] ending; a cancel says nothing. A completer that gives up, or a spent budget
 * with no board found, calls [gaveUp] with the pieces it could not finish and leaves the board as it is.
 *
 * A completion starts a repeat chain: the author's own pieces are kept, so the next press completes those
 * again (not the last completion) while the board stays the completion this run landed. [boardChanged]
 * ends the chain. [random] is the run's one source of randomness.
 */
internal class FinishRun(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val random: Random,
    private val gaveUp: (pieces: EditorDraft<*>) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
    private val budgetMs: (Difficulty.Level?) -> Long = FinishBudget::millisFor,
) {
    private val running = MutableStateFlow(false)

    /** A completion is being found; the tool shows busy and a tap cancels it. */
    val busy: StateFlow<Boolean> = running.asStateFlow()

    private val chained = MutableStateFlow(false)

    /** The board is a completion the author has not edited, so pressing again gives another. */
    val repeating: StateFlow<Boolean> = chained.asStateFlow()

    private var job: Job? = null

    /** Which press a finishing job belongs to, so a cancelled one never clears a newer one's state. */
    private var generation = 0

    /** The author's own pieces while a chain lasts; null before the first completion and after an edit. */
    private var pieces: EditorDraft<*>? = null

    /** The completion being handed to the board: its own change is not an edit. */
    private var landing: EditorDraft<*>? = null

    /** Starts a completion of [board] aiming for [target] (null: any level) and hands it to [land]; while one runs, cancels it instead. */
    fun press(board: EditorDraft<*>, land: (EditorDraft<*>) -> Unit, target: Difficulty.Level? = null) {
        if (running.value) return cancel()
        val source = pieces ?: board
        val mine = ++generation
        running.value = true
        job = scope.launch {
            try {
                val next = withContext(dispatchers.default) {
                    board.finish(random, Budget.within(budgetMs(target), nanoTime) { !isActive }, source, target)
                }
                if (generation != mine) return@launch
                if (next == null) {
                    gaveUp(source)
                } else {
                    pieces = source
                    chained.value = true
                    landing = next
                    land(next)
                    landing = null
                }
            } finally {
                if (generation == mine) running.value = false
            }
        }
    }

    /** The board became [draft]: a different one ends the run and the chain; the completion just landed does not. */
    fun boardChanged(draft: EditorDraft<*>) {
        if (draft === landing) return
        cancel()
        pieces = null
        chained.value = false
    }

    /** Stops a running completion without a word. */
    fun cancel() {
        generation++
        job?.cancel()
        running.value = false
    }
}
