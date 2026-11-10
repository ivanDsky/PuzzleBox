package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.CompletionRecord

/** The player's own LinkedIn result for one day. */
sealed interface LinkedInOwnState {
    data object Unknown : LinkedInOwnState

    /** Any import row for the day. */
    data class Imported(val record: CompletionRecord) : LinkedInOwnState

    data object NotPlayed : LinkedInOwnState
}
