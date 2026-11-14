package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.Flow

/**
 * Which owner the screens see and which uid new rows get (ADR-0029 decision 6). One place decides, so no reader
 * or writer reads the identity on its own.
 */
interface OwnerScope {
    /**
     * The owner whose rows are shown; null means no filter (flag off, or no account ever signed in here). It emits
     * nothing until the owner is known, so a collector never sees a null that only means "not loaded yet".
     */
    val visible: Flow<String?>

    /** The owner [visible] shows now, waiting for it to be known; use it where a flow cannot be collected. */
    suspend fun visibleNow(): String?

    /** The uid stamped on a new row; null when the flag is off or nobody is signed in. */
    fun writer(): String?
}
