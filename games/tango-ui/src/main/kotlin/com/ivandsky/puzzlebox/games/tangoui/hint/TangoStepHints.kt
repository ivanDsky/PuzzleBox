package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import java.time.Instant
import kotlinx.coroutines.CoroutineScope

/**
 * The controller's [StepHints] for one play. A culprit's second press erases it (A4) as one validated, undoable move
 * handed to [erase], which counts nothing; a count (A9) goes to [count], which must not clear the Spotlight the press
 * just set. [session] is non-null whenever a step delivers: a press needs a session and any input drops the search.
 */
internal fun tangoStepHints(
    mode: HintMode.Stepwise,
    scope: CoroutineScope,
    session: () -> TangoSession?,
    now: () -> Instant,
    erase: (TangoSession) -> Unit,
    count: (TangoSession) -> Unit,
): StepHints<*, *> = StepHints(
    mode,
    scope,
    TangoStepGame(
        session = { checkNotNull(session()) },
        onEraseCulprit = { step -> session()?.erased(step.idx, now())?.let(erase) },
    ),
    used = { session()?.hintsUsed ?: 0 },
    onCount = { used -> session()?.let { count(it.copy(hintsUsed = used)) } },
)

/** This session with [idx] emptied as one undo unit, or null when the rules refuse the move. */
private fun TangoSession.erased(idx: Int, at: Instant): TangoSession? {
    val move = TangoMove(idx, null)
    return if (TangoRules.validate(move, TangoState(puzzle, grid())) is TangoVerdict.Valid) withMove(move, at) else null
}
