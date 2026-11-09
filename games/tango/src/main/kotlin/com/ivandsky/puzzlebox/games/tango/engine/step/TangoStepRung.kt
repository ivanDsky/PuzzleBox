package com.ivandsky.puzzlebox.games.tango.engine.step

/**
 * Which deduction produced a [TangoStep.Deduction]: identity only (copy key, ledger key).
 * The declaration order has no meaning and nothing reads `ordinal`; the stepping order lives
 * solely in the stepper's rule list.
 */
enum class TangoStepRung { SIGN, PAIR, SANDWICH, FULL_LINE, EQUAL_PAIR, SIGN_COUNT, SPLIT_PAIR, COUNT_SQUEEZE, LINE_OPTIONS }
