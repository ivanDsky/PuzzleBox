package com.ivandsky.puzzlebox.gameui.hint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * The one press flow for every stepwise game (spec A4, A9, A10, A17, A19): search off the main
 * thread, ask the [HintLedger] what the press costs, hold the current [spotlight]. A press only
 * ever shows; it never edits the board. Never interprets a step [S] or a key [K] (ADR-0001); it
 * only hands steps to the [game] adapter.
 *
 * Confined to [scope]'s dispatcher (the controller's main thread), like [HintSearch] and
 * [HintLedger]. [spotlight] and the play id change only inside [press], [inputMade] and [reset].
 */
class StepHints<S : Any, K : Any>(
    mode: HintMode.Stepwise,
    scope: CoroutineScope,
    private val game: Game<S, K>,
    private val used: () -> Int,
    private val onCount: (used: Int) -> Unit,
) {
    /** What a game supplies; steps stay opaque to [StepHints]. */
    interface Game<S : Any, K : Any> {
        /** Main thread: snapshot the board. The returned block is pure and runs on [HintMode.Stepwise.compute]. */
        fun capture(): () -> S

        /** Null = nothing to show (solved / unavailable): no Spotlight, nothing counted. */
        fun keyOf(step: S): K?

        fun spotlight(step: S, playId: Int): Spotlight?

        /** Called by [StepHints.inputMade], after the search step and the Spotlight clear. */
        fun inputMade() {}

        /** Called by [StepHints.reset], after the ledger and search step and the Spotlight clear. */
        fun reset() {}
    }

    private val search = HintSearch(scope, mode.compute)
    private val ledger = HintLedger<K>()
    private var playId = 0

    /** Compose state: the board overlay reads it and recomposes on change. */
    var spotlight: Spotlight? by mutableStateOf(null)
        private set

    fun press() {
        val block = game.capture()
        search.request(compute = { block.runCatchingStep() }, deliver = ::deliver)
    }

    /** Any player input: drop a search in flight, clear the Spotlight; counted keys stay (A9, A10). */
    fun inputMade() {
        search.invalidate()
        spotlight = null
        game.inputMade()
    }

    /** A replay or a fresh session: like [inputMade], and counted keys are forgotten too. */
    fun reset() {
        ledger.reset()
        search.invalidate()
        spotlight = null
        game.reset()
    }

    private fun deliver(step: S?) {
        val key = step?.let(game::keyOf)
        if (step == null || key == null) {
            spotlight = null
            return
        }
        val next = ledger.press(key, used())
        spotlight = game.spotlight(step, ++playId)
        if (next != used()) onCount(next)
    }

    /** A failing search is "no hint": HintSearch would rethrow into the scope, a press must not crash it. */
    private fun (() -> S).runCatchingStep(): S? =
        try {
            invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
}
