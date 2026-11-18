package com.ivandsky.puzzlebox.compete.create

/** Why the last Create did not close the sheet; the draft is kept for each. */
internal sealed interface DuelNotice {
    data object Offline : DuelNotice
    /** The service's daily limit on new duels was reached; not a connection problem. */
    data object Busy : DuelNotice
    data object NoPuzzles : DuelNotice
    /** The lineup's puzzle at [position] (from 1) is an "Any" of [game] with nothing left to draw. */
    data class EntryUndrawable(val position: Int, val game: String) : DuelNotice
    data class Failed(val message: String?) : DuelNotice
}
