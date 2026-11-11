package com.ivandsky.puzzlebox.data.linkedin

import kotlinx.coroutines.flow.Flow

/**
 * What this phone already keeps of the linked account, for the Profile summary. Reads local tables only and never
 * asks LinkedIn, so collecting it is always safe.
 */
interface LinkedInAccount {
    /** Emitted again when the link, a stored board, a photo or the fetch log changes; empty while not linked. */
    fun observe(): Flow<LinkedInAccountData>
}
