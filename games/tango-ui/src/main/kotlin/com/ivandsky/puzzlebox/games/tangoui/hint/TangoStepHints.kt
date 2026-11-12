package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import kotlinx.coroutines.CoroutineScope

/**
 * The controller's [StepHints] for one play. A press only shows, a culprit included (A4); a count (A9) goes to
 * [count], which must not clear the Spotlight the press just set; it records the one counted hint. [session] is
 * non-null whenever a step delivers: a press needs a session and any input drops the search.
 */
internal fun tangoStepHints(
    mode: HintMode.Stepwise,
    scope: CoroutineScope,
    session: () -> TangoSession?,
    count: () -> Unit,
    solve: (TangoPuzzle) -> List<TangoCell>?,
): StepHints<*, *> = StepHints(
    mode,
    scope,
    TangoStepGame(session = { checkNotNull(session()) }, solve = solve),
    used = { session()?.hintsUsed ?: 0 },
    onCount = { count() },
)
