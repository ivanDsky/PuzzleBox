package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict

/**
 * How a submitted path leaves the board when it found no theme word: [Error] is refused
 * (red, shaking, dissolving); the others are set aside (neutral, held, dissolving).
 */
internal enum class TraceEndKind {
    Error,
    Bonus,
    Short,
    Repeat,
    ;

    companion object {
        /** Null for a verdict that leaves nothing to show: a found word takes its tiles instead. */
        fun of(verdict: StrandsVerdict): TraceEndKind? = when (verdict) {
            is StrandsVerdict.ThemeWord, is StrandsVerdict.Spangram, StrandsVerdict.Empty -> null
            is StrandsVerdict.NonTheme -> Bonus
            StrandsVerdict.AlreadyFound -> Repeat
            StrandsVerdict.TooShort, StrandsVerdict.TooLong, StrandsVerdict.WordLimitReached -> Short
            StrandsVerdict.NotInWordList, StrandsVerdict.InvalidPath, StrandsVerdict.Foreign -> Error
        }
    }
}

/**
 * The submitted [path] still on the board while it plays out its [kind]; [seq] is the verdict it answers, so a repeat replays.
 * [pulse] marks a [TraceEndKind.Repeat] of a found theme word, whose tiles also pulse; a repeated non-theme word has none to pulse.
 */
internal data class TraceEnd(val path: List<Coord>, val kind: TraceEndKind, val seq: Int, val pulse: Boolean = false)
