package com.ivandsky.puzzlebox.data.insights

import kotlinx.coroutines.flow.Flow

/**
 * The recap Today cards the player dismissed (A3): a dismissal hides that period's card only, never the Profile entry.
 * Kept on this device and never synced (A18). A period is named by its id, the text a recap's navigation key carries:
 * `YYYY-MM` for a month, `YYYY` for a year.
 */
interface RecapDismissals {
    /** The ids of every dismissed period, emitting again as one is added; empty until the first dismissal. */
    fun observe(): Flow<Set<String>>

    /** Hides the Today card of the period named [periodId], for good; dismissing twice changes nothing. */
    suspend fun dismiss(periodId: String)
}
