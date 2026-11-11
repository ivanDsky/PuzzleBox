package com.ivandsky.puzzlebox.data.linkedin

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps what a day sync stores and what a disconnect wipes from interleaving. A read that was already on the
 * wire when Disconnect began would otherwise land after the wipe, and its ghosts, log row or import would
 * come back with the next account. One instance is shared by the sync and the link.
 */
internal class LinkedInStoreLock {

    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
