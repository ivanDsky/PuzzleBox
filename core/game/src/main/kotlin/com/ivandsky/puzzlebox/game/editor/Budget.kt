package com.ivandsky.puzzlebox.game.editor

/**
 * Cooperative cancellation/timeout probe for analysis loops (P7: no Dispatchers here). An
 * analyzer polls [exhausted] and answers [Verdict.Undetermined] once it is true.
 */
fun interface Budget {
    fun exhausted(): Boolean

    companion object {
        val Unlimited: Budget = Budget { false }

        /** Exhausted once [timeoutMs] elapsed since this call, or as soon as [cancelled] is true. */
        fun within(timeoutMs: Long, nanoTime: () -> Long, cancelled: () -> Boolean = { false }): Budget {
            val start = nanoTime()
            val timeoutNanos = timeoutMs * NANOS_PER_MILLI
            return Budget { cancelled() || nanoTime() - start >= timeoutNanos }
        }

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
