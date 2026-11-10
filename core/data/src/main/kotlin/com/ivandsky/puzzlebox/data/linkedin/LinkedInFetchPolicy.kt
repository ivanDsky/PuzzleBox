package com.ivandsky.puzzlebox.data.linkedin

/**
 * The pure cache windows of the LinkedIn reads, and the 7-day window of the connections board. A
 * board read at delta 0 is stored as `BoardToday`; at delta ≥ 1 [shouldFetch] returns true for a
 * `BoardToday` last fetch (once), false for `Board`. The day sync stores `BoardToday` when delta == 0,
 * else `Board`.
 */
internal object LinkedInFetchPolicy {
    /** LinkedIn serves a connections board for today and the 7 days before it, nothing older. */
    const val BOARD_WINDOW_DAYS = 7

    private const val MINUTE_MS = 60_000L
    private const val OWN_TODAY_WINDOW_MS = 2 * MINUTE_MS
    private const val OWN_PAST_WINDOW_MS = 24 * 60 * MINUTE_MS
    private const val BOARD_TODAY_WINDOW_MS = 10 * MINUTE_MS

    /**
     * Is a call due for [item] of the day [delta] days before today (0 = today), given its [last]
     * recorded fetch? Windows are inclusive: an item is due at exactly its window length.
     */
    fun shouldFetch(item: LinkedInItem, delta: Int, last: LinkedInFetch?, nowMs: Long): Boolean {
        // A future day has nothing to read; refusing keeps a caller's bug from becoming a request.
        if (delta < 0) return false
        if (item == LinkedInItem.Board && delta > BOARD_WINDOW_DAYS) return false
        if (last == null) return true
        // Negative when the clock went backwards, which no window below counts as due.
        val elapsed = nowMs - last.fetchedAtMs
        return when (item) {
            LinkedInItem.Own -> ownDue(delta, last.outcome, elapsed)
            LinkedInItem.Board -> boardDue(delta, last.outcome, elapsed)
        }
    }

    private fun ownDue(delta: Int, outcome: LinkedInOutcome, elapsed: Long): Boolean = when (outcome) {
        // A solved day cannot change, so it is read once.
        LinkedInOutcome.Solved -> false
        LinkedInOutcome.NotPlayed -> elapsed >= if (delta == 0) OWN_TODAY_WINDOW_MS else OWN_PAST_WINDOW_MS
        // A board outcome on an own result is a corrupt row; trusting it would block the item forever.
        LinkedInOutcome.Board, LinkedInOutcome.BoardToday -> true
    }

    private fun boardDue(delta: Int, outcome: LinkedInOutcome, elapsed: Long): Boolean = when (outcome) {
        // A board read after its day ended is complete.
        LinkedInOutcome.Board -> delta == 0 && elapsed >= BOARD_TODAY_WINDOW_MS
        // Read while the day was still running, so it is partial: once the day ends it is read once more.
        LinkedInOutcome.BoardToday -> delta > 0 || elapsed >= BOARD_TODAY_WINDOW_MS
        // An own-result outcome on a board is a corrupt row; trusting it would block the item forever.
        LinkedInOutcome.Solved, LinkedInOutcome.NotPlayed -> true
    }
}
