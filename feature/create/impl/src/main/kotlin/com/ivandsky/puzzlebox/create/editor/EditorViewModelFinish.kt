package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GradingMode
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The Studio view model's Finish for me (E1): [enabled] says whether a press would start a completion, and [press]
 * runs one through [FinishRun] unless the draft is [locked] (published), nothing is open or the rule says no;
 * while one runs a press cancels it. [boardChanged] is told of every board the Studio adopts, and [land]
 * receives a completion as one step. A game without a completer ([EditorDraft.canFinish]) never offers it.
 *
 * A press aims for a level (90): the one handed in, which becomes the session's [choice], or that choice.
 * The Studio grades by score ([grading]), and once the [settled] verdict of a
 * landed completion is in, a level other than the one asked for raises [FinishNotice.Missed]. A completion
 * that changed the author's pieces ([EditorDraft.finishChanged]) raises [FinishNotice.Recoloured] as it
 * lands. A give-up raises the notice that fits its cause ([gaveUpNotice]). All of them wait in [notices].
 */
internal class EditorViewModelFinish(
    scope: CoroutineScope,
    dispatchers: DispatcherProvider,
    random: Random,
    private val choice: FinishLevelChoice,
    private val board: () -> EditorDraft<*>?,
    private val verdict: () -> Verdict,
    private val settled: StateFlow<Verdict>,
    private val locked: () -> Boolean,
    private val land: (EditorDraft<*>) -> Unit,
) {
    val notices = FinishNoticeState()

    /** The verdict the author's pieces had when the repeat chain began: what a give-up on them is judged by. */
    private var piecesVerdict: Verdict = Verdict.Checking

    /** The level a landed completion was asked for, until its verdict settles or the board changes. */
    private var awaiting: Difficulty.Level? = null

    private val run = FinishRun(scope, dispatchers, random, gaveUp = { pieces ->
        notices.raise(gaveUpNotice(pieces.finishReadiness(), piecesVerdict))
    })

    val busy: StateFlow<Boolean> = run.busy
    val repeating: StateFlow<Boolean> = run.repeating

    /** The level the picker opens on: the author's last choice this session. */
    val level: StateFlow<Difficulty.Level> = choice.level

    /** How the Studio's verdict is graded: by the game's score. */
    val grading: GradingMode get() = GradingMode.Scored

    init {
        scope.launch { settled.collect(::compare) }
    }

    /** A press on [draft] would start a completion: it has a completer, it is Ready and [verdict] is settled, or a repeat is on. */
    fun enabled(draft: EditorDraft<*>, verdict: Verdict): Boolean =
        draft.canFinish && finishEnabled(draft.finishReadiness(), verdict, run.repeating.value)

    /** Finish aiming for [target], which becomes the session's choice; while a completion runs, cancels it instead. */
    fun press(target: Difficulty.Level = choice.level.value) {
        if (locked()) return
        val draft = board() ?: return
        if (!draft.canFinish) return
        if (!run.busy.value) {
            val current = verdict()
            if (!enabled(draft, current)) return
            choice.choose(target)
            if (!run.repeating.value) piecesVerdict = current
        }
        run.press(draft, { next -> landed(next, target) }, target)
    }

    fun boardChanged(draft: EditorDraft<*>) {
        awaiting = null
        run.boardChanged(draft)
    }

    /** Leaving the Studio, or a tap on the busy tool: a running completion stops. */
    fun cancel() = run.cancel()

    /** [next] goes to the board; its verdict is compared with [target] now when the layout did not change, else when the check settles. */
    private fun landed(next: EditorDraft<*>, target: Difficulty.Level) {
        land(next)
        awaiting = target
        compare(settled.value)
        next.finishChanged().takeIf { it > 0 }?.let { notices.raise(FinishNotice.Recoloured(it)) }
    }

    private fun compare(verdict: Verdict) {
        val wanted = awaiting ?: return
        if (verdict == Verdict.Checking) return
        awaiting = null
        val got = (verdict as? Verdict.Unique)?.takeUnless { it.needsGuessing }?.difficulty?.level ?: return
        if (got != wanted) notices.raise(FinishNotice.Missed(got, wanted))
    }
}
