package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.log.FailureLog
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one guard of a chain of providers (the next-thing chain, the recap chain): runs [block] for the provider
 * named [who] and returns its answer, or null for nothing. A provider that has nothing, throws, or takes longer than
 * [timeout] counts as nothing, so one bad provider never blocks the ones after it. A failure is reported once to
 * [failures] under [tag] as "[who] failed". A null [timeout] sets no limit, which is for providers that read only local
 * data.
 *
 * The time limit runs on the caller's coroutine context, so it takes no dispatcher of its own. The caller leaving
 * cancels the guard; a provider whose own work was cancelled is only nothing.
 */
suspend fun <T : Any> guardedAsk(
    failures: FailureLog,
    tag: String,
    who: String,
    timeout: Duration? = null,
    block: suspend () -> T?,
): T? =
    try {
        if (timeout == null) block() else withTimeoutOrNull(timeout) { block() }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (e: Exception) {
        failures.warn(tag, "$who failed", e)
        null
    }
