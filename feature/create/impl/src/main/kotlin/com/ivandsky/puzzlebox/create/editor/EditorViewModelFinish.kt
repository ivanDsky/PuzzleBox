package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Studio view model's Finish for me (E1), and the only reader of `FinishForMe` (A2): [offered] says
 * whether the tool is on the strip at all, [enabled] whether a press would start a completion, and [press]
 * runs one through [FinishRun] unless the draft is [locked] (published), nothing is open or the rule says no;
 * while one runs a press cancels it. [boardChanged] is told of every board the Studio adopts, and [land]
 * receives a completion as one step. A game without a completer ([EditorDraft.canFinish]) never offers it.
 */
internal class EditorViewModelFinish(
    flags: ReleaseFlags?,
    scope: CoroutineScope,
    dispatchers: DispatcherProvider,
    random: Random,
    private val board: () -> EditorDraft<*>?,
    private val verdict: () -> Verdict,
    private val locked: () -> Boolean,
    private val land: (EditorDraft<*>) -> Unit,
) {
    val gaveUp = FinishGaveUpToastState()
    private val run = FinishRun(scope, dispatchers, random, gaveUp)

    val busy: StateFlow<Boolean> = run.busy
    val repeating: StateFlow<Boolean> = run.repeating

    /** The tool is on the strip: FinishForMe is on, live. */
    val offered: StateFlow<Boolean> = when (flags) {
        null -> MutableStateFlow(false)
        else -> flags.enabled
            .map { ReleaseFlag.FinishForMe in it }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, initialValue = flags.isEnabled(ReleaseFlag.FinishForMe))
    }

    /** A press on [draft] would start a completion: it has a completer, it is Ready and [verdict] is settled, or a repeat is on. */
    fun enabled(draft: EditorDraft<*>, verdict: Verdict): Boolean =
        draft.canFinish && finishEnabled(draft.finishReadiness(), verdict, run.repeating.value)

    fun press() {
        if (!offered.value || locked()) return
        val draft = board() ?: return
        if (!draft.canFinish) return
        if (!run.busy.value && !enabled(draft, verdict())) return
        run.press(draft, land)
    }

    fun boardChanged(draft: EditorDraft<*>) = run.boardChanged(draft)

    /** Leaving the Studio: a running completion stops. */
    fun cancel() = run.cancel()
}
