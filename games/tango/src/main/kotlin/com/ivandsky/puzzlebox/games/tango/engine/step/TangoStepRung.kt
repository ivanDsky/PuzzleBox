package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.game.trainer.TechniqueId

/**
 * Which deduction produced a [TangoStep.Deduction]: identity (copy key, ledger key) and the stable
 * [trainerId] the Trainer stores. The declaration order has no meaning and nothing reads `ordinal`;
 * the stepping order lives solely in the stepper's rule list. Every id is stated literally, so
 * renaming an entry never changes a stored key.
 */
enum class TangoStepRung(val trainerId: TechniqueId) {
    SIGN(TechniqueId("sign")),
    PAIR(TechniqueId("pair")),
    SANDWICH(TechniqueId("sandwich")),
    FULL_LINE(TechniqueId("full-line")),
    EQUAL_PAIR(TechniqueId("equal-pair")),
    SIGN_COUNT(TechniqueId("sign-count")),
    SPLIT_PAIR(TechniqueId("split-pair")),
    COUNT_SQUEEZE(TechniqueId("count-squeeze")),
    LINE_OPTIONS(TechniqueId("line-options")),
}
