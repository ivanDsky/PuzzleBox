package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.engine.step.TangoCountSqueezeRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoEqualPairRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoLegacyStepRules
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoSignCountRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoSplitPairRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/**
 * Tango's hint ladder: every rung with the rule that finds its deductions, easiest first. The same rules the stepper
 * lists, in the order it lists them (the stepper keeps its own private lists and defers glance steps; the practice
 * board judges against the plain order, so it keeps this one).
 */
internal object TangoLadder {

    val rungs: List<Pair<TangoStepRung, TangoStepRule>> = listOf(
        TangoStepRung.SIGN to TangoLegacyStepRules.sign,
        TangoStepRung.PAIR to TangoLegacyStepRules.pair,
        TangoStepRung.SANDWICH to TangoLegacyStepRules.sandwich,
        TangoStepRung.FULL_LINE to TangoLegacyStepRules.fullLine,
        TangoStepRung.EQUAL_PAIR to TangoEqualPairRule,
        TangoStepRung.SIGN_COUNT to TangoSignCountRule,
        TangoStepRung.SPLIT_PAIR to TangoSplitPairRule,
        TangoStepRung.COUNT_SQUEEZE to TangoCountSqueezeRule,
        TangoStepRung.LINE_OPTIONS to TangoLegacyStepRules.lineOptions,
    )
}
