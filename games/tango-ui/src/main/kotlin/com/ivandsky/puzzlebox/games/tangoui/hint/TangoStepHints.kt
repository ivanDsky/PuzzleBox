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
 * non-null whenever a step delivers: a press needs a session and any input drops the search. [plainCaptions] picks the
 * callout set; [placeIt] is the "Place it" placement.
 */
internal fun tangoStepHints(
    mode: HintMode.Stepwise,
    scope: CoroutineScope,
    session: () -> TangoSession?,
    count: () -> Unit,
    solve: (TangoPuzzle) -> List<TangoCell>?,
    placeIt: (idx: Int, value: TangoCell) -> Unit,
    plainCaptions: Boolean = false,
): StepHints<*, *> = StepHints(
    mode,
    scope,
    TangoStepGame(session = { checkNotNull(session()) }, solve = solve, plainCaptions = plainCaptions, placeIt = placeIt),
    used = { session()?.hintsUsed ?: 0 },
    onCount = { count() },
)
