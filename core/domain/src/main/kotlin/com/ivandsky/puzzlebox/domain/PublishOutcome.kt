package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.model.ShareCode

/** What one [CreateSharedPuzzle] call concluded about a draft. */
sealed interface PublishOutcome {
    /** Live under [code]; the author's published list has its row. */
    data class Published(val code: ShareCode) : PublishOutcome

    /** Not ready: what still keeps the draft from publishing. */
    data class Blocked(val blocks: List<PublishBlock>) : PublishOutcome

    data object SignedOut : PublishOutcome

    data object Offline : PublishOutcome

    data object DailyCapReached : PublishOutcome

    /** The store refused the document and the device's UTC day is not the server's. */
    data object ClockSkew : PublishOutcome

    /** The store refused the document, or no free code was found. */
    data class Rejected(val message: String?) : PublishOutcome
}
