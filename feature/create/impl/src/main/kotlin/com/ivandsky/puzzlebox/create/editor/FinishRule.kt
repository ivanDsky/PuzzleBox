package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * Whether the Studio's Finish tool is enabled (E1), pure so the rule is tested directly. It is on
 * while the board is a completion the author has not edited ([repeating], so pressing again gives
 * another one, even while that completion is being re-checked). Otherwise it needs the completer's
 * [readiness] to be Ready (null: no completer) and the live [verdict] to be settled and not None;
 * Checking and None keep it off, as Publish is.
 */
internal fun finishEnabled(readiness: FinishReadiness?, verdict: Verdict, repeating: Boolean): Boolean =
    repeating || (readiness == FinishReadiness.Ready && verdict != Verdict.Checking && verdict !is Verdict.None)
