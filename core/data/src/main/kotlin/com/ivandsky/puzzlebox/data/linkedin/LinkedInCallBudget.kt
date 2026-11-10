package com.ivandsky.puzzlebox.data.linkedin

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
    @Synchronized
    fun tryAcquire(): Boolean {
        val now = nowMs()
        while (calls.isNotEmpty() && now - calls.first() >= windowMs) calls.removeFirst()
        if (calls.size >= limit) return false
        calls.addLast(now)
        return true
    }
}
