package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead

/** Result of one sync of a day's LinkedIn data. */
sealed interface LinkedInSync {
    /** Every due item fetched, or every item inside its cache window (0 calls). */
    data object Done : LinkedInSync

    data object NotLinked : LinkedInSync

    data object NotLinkedInGame : LinkedInSync

    data class Paused(val kind: LinkedInRead.Rejected.Kind) : LinkedInSync

    data object Offline : LinkedInSync
}
