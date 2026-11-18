package com.ivandsky.puzzlebox.compete.create

/** Why the last Create did not close the sheet; the draft is kept for each. */
internal sealed interface DuelNotice {
    data object Offline : DuelNotice
    /** The service's daily limit on new duels was reached; not a connection problem. */
    data object Busy : DuelNotice
    data object NoPuzzles : DuelNotice
    /** The lineup's puzzle at [position] (from 1) is an "Any" of [game] with nothing left to draw; for [pastYou], nothing solved left. */
    data class EntryUndrawable(val position: Int, val game: String, val pastYou: Boolean = false) : DuelNotice
    /** The lineup's exact puzzle at [position] (from 1) was taken down or can't be read; for [pastYou], it is no longer one the player has solved. */
    data class EntryUnavailable(val position: Int, val pastYou: Boolean = false) : DuelNotice
    data class Failed(val message: String?) : DuelNotice
}
