package com.ivandsky.puzzlebox.gameui.hint

/** Ceiling of a play's hint count (spec A9): presses saturate here, never wrap or throw. */
const val MAX_HINTS_USED: Int = 255

/**
 * The hint count rules shared by every stepwise game (spec A4, A9), keyed by an opaque step key
 * [K] compared by `equals`. Core never interprets the key (ADR-0001). A press always shows; the
 * ledger only decides what it costs.
 *
 * - A key not yet counted costs one hint, saturating at [MAX_HINTS_USED].
 * - A counted key is free, whatever was shown or played in between, a culprit's re-show included (A4).
 *
 * In-memory only: after process death the same step counts again. Not thread-safe; callers confine
 * it to the main thread.
 */
class HintLedger<K : Any> {
    private val counted = HashSet<K>()

    /** The play's hint count after pressing [key], given the count [used] before it. */
    fun press(key: K, used: Int): Int = if (counted.add(key)) minOf(used + 1, MAX_HINTS_USED) else used

    /** A play-again or a fresh session: forget every counted key. */
    fun reset() {
        counted.clear()
    }
}
