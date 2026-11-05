package com.ivandsky.puzzlebox.games.strandsui

/** The line between the theme card and the board (A35): the live letters, then the verdict. */
sealed interface WordSlot {
    data object Empty : WordSlot

    data class Tracing(val letters: String) : WordSlot

    /** [seq] grows with every verdict, so the same copy twice still retriggers its motion. */
    data class Verdict(val copy: String, val kind: VerdictKind, val seq: Int) : WordSlot
}
