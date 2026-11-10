package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead

/** Whether this install is linked to a LinkedIn account, and whether its reads are paused. */
sealed interface LinkState {
    data object NotConnected : LinkState

    data class Connected(val profileId: String) : LinkState

    /**
     * Linked, but no read is sent until the pause lifts. [kind] is the rejection that tripped
     * it, carried as the transport's own type so a rejected read latches without translation.
     */
    data class Paused(val profileId: String, val kind: LinkedInRead.Rejected.Kind) : LinkState
}
