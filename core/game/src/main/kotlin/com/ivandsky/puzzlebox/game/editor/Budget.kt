package com.ivandsky.puzzlebox.game.editor

/**
 * Cooperative cancellation/timeout probe for analysis loops (P7: no Dispatchers here). An
 * analyzer polls [exhausted] and answers [Verdict.Undetermined] once it is true. An analyzer
 * that bounds its own work by a fixed count, so a draft gets the same verdict on any device,
 * polls [cancelled] instead.
 */
fun interface Budget {
    fun exhausted(): Boolean

    /**
     * True once the caller gave up on the answer. For [within], only its cancel lambda sets it;
     * any other Budget answers as [exhausted].
     */
    fun cancelled(): Boolean = exhausted()

    companion object {
        val Unlimited: Budget = Budget { false }

        /**
         * Exhausted once [timeoutMs] elapsed since this call, or as soon as [cancelled] is true;
         * [Budget.cancelled] reads [cancelled] only.
         */
        fun within(timeoutMs: Long, nanoTime: () -> Long, cancelled: () -> Boolean = { false }): Budget {
            val start = nanoTime()
            val timeoutNanos = timeoutMs * NANOS_PER_MILLI
            val gaveUp = cancelled
            return object : Budget {
                override fun exhausted(): Boolean = gaveUp() || nanoTime() - start >= timeoutNanos
                override fun cancelled(): Boolean = gaveUp()
            }
        }

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
