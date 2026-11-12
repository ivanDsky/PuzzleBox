package com.ivandsky.puzzlebox.data.cloud

import kotlinx.coroutines.flow.StateFlow

/**
 * Which owner the screens see and which uid new rows get (ADR-0029 decision 6). One place decides, so no reader
 * or writer reads the identity on its own.
 */
interface OwnerScope {
    /** The owner whose rows are shown; null means no filter (flag off, or no account ever signed in here). */
    val visible: StateFlow<String?>

    /** The uid stamped on a new row; null when the flag is off or nobody is signed in. */
    fun writer(): String?
}
