package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict

/** What the word slot says about a verdict (A3): found words show themselves, the rest name the problem. */
internal object StrandsVerdictSlot {

    fun of(verdict: StrandsVerdict, seq: Int): WordSlot.Verdict = when (verdict) {
        is StrandsVerdict.ThemeWord -> WordSlot.Verdict(verdict.word.text, VerdictKind.Theme, seq)
        is StrandsVerdict.Spangram -> WordSlot.Verdict(StrandsCopy.SPANGRAM, VerdictKind.Spangram, seq)
        is StrandsVerdict.NonTheme -> WordSlot.Verdict(verdict.word, VerdictKind.NonTheme, seq)
        else -> WordSlot.Verdict(rejectCopy(verdict), VerdictKind.Reject, seq)
    }

    private fun rejectCopy(verdict: StrandsVerdict): String = when (verdict) {
        StrandsVerdict.Empty -> StrandsCopy.EMPTY
        StrandsVerdict.TooShort -> StrandsCopy.TOO_SHORT
        StrandsVerdict.TooLong -> StrandsCopy.TOO_LONG
        StrandsVerdict.AlreadyFound -> StrandsCopy.ALREADY_FOUND
        StrandsVerdict.WordLimitReached -> StrandsCopy.WORD_LIMIT
        // The trace input never builds a broken path and the controller never submits a
        // foreign move; if either slips through, the honest answer is still "not a word".
        else -> StrandsCopy.NOT_IN_WORD_LIST
    }
}
