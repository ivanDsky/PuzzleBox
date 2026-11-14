package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow

/**
 * How many of the signed-in account's drafts are too big to sync: a draft over the rules' size cap is never uploaded,
 * so the Google row says so. Resolved with `getOrNull<OversizedDrafts>()`; null (flag off) means today's UI exactly.
 */
interface OversizedDrafts {
    /** The live count: 0 while nobody is signed in or no draft is over the cap; follows edits, deletes and sign-in changes. */
    val count: Flow<Int>
}
