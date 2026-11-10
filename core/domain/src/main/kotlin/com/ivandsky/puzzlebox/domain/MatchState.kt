package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.Match

/** What a match screen shows for one duel code; [UpdateApp] names the format version this build cannot read. */
sealed interface MatchState {
    data class Loaded(val view: MatchView) : MatchState

    data object NotFound : MatchState

    data object Offline : MatchState

    data class UpdateApp(val found: Int) : MatchState

    /**
     * No identity. [match] is the public match document when it could be read (an invite's details, nothing sealed:
     * no results, no outbox, no local cache), null when it could not be read now.
     */
    data class SignedOut(val match: Match?) : MatchState
}
