package com.ivandsky.puzzlebox.domain

/** What a match screen shows for one duel code; [UpdateApp] names the format version this build cannot read (A25). */
sealed interface MatchState {
    data class Loaded(val view: MatchView) : MatchState

    data object NotFound : MatchState

    data object Offline : MatchState

    data class UpdateApp(val found: Int) : MatchState

    data object SignedOut : MatchState
}
