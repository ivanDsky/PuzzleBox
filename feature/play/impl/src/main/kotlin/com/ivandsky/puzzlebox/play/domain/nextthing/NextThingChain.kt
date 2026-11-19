package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The next-thing chain (L3): asks [providers] one at a time, lowest [NextThingProvider.order] first, and
 * offers the first answer. A provider that has nothing, throws, or takes longer than [timeout] counts as
 * nothing, so one bad provider never blocks the ones after it. The chain only offers: it starts nothing.
 *
 * The time limit runs on the caller's coroutine context, so it takes no dispatcher of its own.
 */
internal class NextThingChain(
    providers: List<NextThingProvider> = NextThingProviders,
    private val timeout: Duration = 2.seconds,
    private val failures: FailureLog = LogcatFailureLog,
) {
    private val providers = providers.sortedBy { it.order }

    /** The next thing for today's counted solve; null for a play again or a practice puzzle, which get no chain. */
    suspend fun offer(facts: NextThingFacts): NextThing? {
        if (facts.playAgain || facts.practice) return null
        return providers.firstNotNullOfOrNull { ask(it, facts) }
    }

    private suspend fun ask(provider: NextThingProvider, facts: NextThingFacts): NextThing? =
        try {
            withTimeoutOrNull(timeout) { provider.offer(facts) }
        } catch (e: CancellationException) {
            // The caller leaving cancels the chain; a provider whose own work was cancelled is only nothing.
            currentCoroutineContext().ensureActive()
            null
        } catch (e: Exception) {
            failures.warn(TAG, "next-thing provider ${provider::class.simpleName} failed", e)
            null
        }

    private companion object {
        const val TAG = "NextThingChain"
    }
}
