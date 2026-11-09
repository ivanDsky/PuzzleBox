package com.ivandsky.puzzlebox.gameui.hint

/**
 * The hint press rules shared by every stepwise game (spec A4, A9, A10), keyed by an opaque step
 * key [K] compared by `equals`. Core never interprets the key (ADR-0001).
 *
 * - A key not yet counted costs one hint, saturating at [MAX_HINTS_USED].
 * - A counted key is free, whatever was shown or played in between.
 * - A culprit pressed again while it is [shown] answers [HintPress.Action.ERASE_CULPRIT], free;
 *   the caller erases the entry, the ledger only decides.
 * - [inputMade] forgets [shown] and keeps the counted keys.
 *
 * In-memory only: after process death the same step counts again. Not thread-safe; callers confine
 * it to the main thread.
 */
class HintLedger<K : Any> {
    private val counted = HashSet<K>()

    /** The key the Spotlight currently shows, or null after an input, a reset, or before any press. */
    var shown: K? = null
        private set

    fun press(key: K, culprit: Boolean, used: Int): HintPress {
        if (culprit && key == shown) return HintPress(HintPress.Action.ERASE_CULPRIT, used)
        val next = if (counted.add(key)) minOf(used + 1, MAX_HINTS_USED) else used
        shown = key
        return HintPress(HintPress.Action.SHOW, next)
    }

    fun inputMade() {
        shown = null
    }

    /** A replay or a fresh session: forget the shown key and every counted key. */
    fun reset() {
        shown = null
        counted.clear()
    }
}
