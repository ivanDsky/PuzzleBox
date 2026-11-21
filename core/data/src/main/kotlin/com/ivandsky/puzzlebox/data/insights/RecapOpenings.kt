package com.ivandsky.puzzlebox.data.insights

import kotlinx.coroutines.flow.Flow

/**
 * The recaps whose story the player opened, so an entry tile can tell new from watched. A recap counts once its story
 * first shows cards. Kept on this device and never synced (A18), never removed. A period is named by the same id
 * [RecapDismissals] uses: `YYYY-MM` for a month, `YYYY` for a year.
 */
interface RecapOpenings {
    /** The ids of every opened period, emitting again as one is added; empty until the first story opens. */
    fun observe(): Flow<Set<String>>

    /** Records the period named by [periodId] as opened; marking twice changes nothing. */
    suspend fun markOpened(periodId: String)
}
