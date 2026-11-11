package com.ivandsky.puzzlebox.data.linkedin

import kotlinx.coroutines.delay

/**
 * At most [limit] calls per rolling [windowMs]. One instance is shared by every game, so the cap
 * holds for the whole app however many day views ask at once.
 */
internal class LinkedInCallBudget(private val nowMs: () -> Long, private val limit: Int = 6, private val windowMs: Long = 60_000) {
    /** When each call still inside the window was let through, oldest first. */
    private val calls = ArrayDeque<Long>()

    /**
     * True, recording a call, while fewer than [limit] were let through in the last [windowMs]. A
     * refusal records nothing, so asking again never pushes the next free slot further away.
     */
    fun tryAcquire(): Boolean = reserve() == 0L

    /**
     * Returns once a slot is taken, waiting for one to free while the cap is used up. The wait ends only
     * with a free slot or the caller's cancellation, and a cancelled wait takes none: whoever shows the
     * day stops asking by leaving it. Callers racing for a freed slot each re-check, so the cap holds.
     */
    suspend fun acquire() {
        while (true) {
            val waitMs = reserve()
            if (waitMs == 0L) return
            delay(waitMs)
        }
    }

    /** 0 after recording a call, else the milliseconds until the oldest call in the window ages out. */
    @Synchronized
    private fun reserve(): Long {
        val now = nowMs()
        while (calls.isNotEmpty() && now - calls.first() >= windowMs) calls.removeFirst()
        if (calls.size < limit) {
            calls.addLast(now)
            return 0L
        }
        // At least 1 so a clock that stands still cannot spin; at most a window so one that went backwards cannot park the caller for long.
        return (calls.first() + windowMs - now).coerceIn(1L, windowMs)
    }
}
