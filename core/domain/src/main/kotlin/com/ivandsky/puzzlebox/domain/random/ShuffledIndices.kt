package com.ivandsky.puzzlebox.domain.random

import kotlin.random.Random

/**
 * `0 until count` in a random order, drawn one at a time (Fisher-Yates over a sparse swap map), so taking a few
 * of a long archive never builds the whole list.
 */
internal class ShuffledIndices(private val count: Int, private val random: Random) : Iterator<Int> {
    private val moved = HashMap<Int, Int>()
    private var drawn = 0

    /** Indices held in memory; at most the number drawn so far. */
    val tracked: Int get() = moved.size

    override fun hasNext(): Boolean = drawn < count

    override fun next(): Int {
        if (!hasNext()) throw NoSuchElementException()
        // The front slot is read once and never again, so what sat there moves into the slot drawn from.
        val front = moved.remove(drawn) ?: drawn
        val at = drawn + random.nextInt(count - drawn)
        val picked = if (at == drawn) front else moved[at] ?: at
        if (at != drawn) moved[at] = front
        drawn++
        return picked
    }
}
