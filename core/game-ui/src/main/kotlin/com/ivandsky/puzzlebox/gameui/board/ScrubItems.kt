package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.ui.util.lerp
import kotlin.math.abs

// One part of the two boards drawn as one: its place in each board's order, -1 on a board it is not on. [part] is the
// arriving board's when it is on both, and [was] the one it was on the board it leaves.
internal class ScrubItem(val part: BoardPart, val was: BoardPart?, val from: Int, val to: Int) {
    val both: Boolean get() = from >= 0 && to >= 0
}

/** The heights of the two boards, each stacked on its own. */
internal data class ScrubHeights(val from: Int, val to: Int) {
    /** The height [progress] of the way from the one board to the other. */
    fun at(progress: Float): Int = if (from == to) to else lerp(from, to, progress)
}

/**
 * The parts of both boards, the arriving board's first, with the order each board stacks them in and where each sits
 * on either board. A pass of layout fills [heights] and calls [stack]; it builds no list, so a frame of the scrub
 * allocates none.
 */
internal class ScrubItems(from: List<BoardPart>, to: List<BoardPart>) {
    val all: List<ScrubItem>
    private val fromOrder: IntArray
    private val toOrder: IntArray
    val fromY: IntArray
    val toY: IntArray

    /** The height of each of [all], as measured. */
    val heights: IntArray

    init {
        val before = HashMap<String, Int>(from.size)
        from.forEachIndexed { index, part -> before[part.key] = index }
        val arriving = to.mapIndexed { index, part ->
            val at = before[part.key] ?: -1
            ScrubItem(part, from.getOrNull(at), at, index)
        }
        val kept = arriving.mapTo(HashSet()) { it.part.key }
        val leaving = from.mapIndexedNotNull { index, part -> if (part.key in kept) null else ScrubItem(part, null, index, -1) }
        all = arriving + leaving
        fromOrder = all.indices.filter { all[it].from >= 0 }.sortedBy { all[it].from }.toIntArray()
        toOrder = all.indices.filter { all[it].to >= 0 }.sortedBy { all[it].to }.toIntArray()
        fromY = IntArray(all.size)
        toY = IntArray(all.size)
        heights = IntArray(all.size)
    }

    /** Stacks each board's parts under each other and returns the two boards' heights; a part on one board stays where that board has it. */
    fun stack(gap: Int): ScrubHeights {
        val fromHeight = stack(fromOrder, fromY, gap)
        val toHeight = stack(toOrder, toY, gap)
        all.forEachIndexed { index, item ->
            if (item.from < 0) fromY[index] = toY[index] else if (item.to < 0) toY[index] = fromY[index]
        }
        return ScrubHeights(fromHeight, toHeight)
    }

    /** How far part [index] goes down, or up when negative, from where the board being left has it to where the other does. */
    fun travel(index: Int): Int = toY[index] - fromY[index]

    /** A part on its way is drawn over the ones it passes, the further it goes the higher; one that frosts is under them all. */
    fun depth(index: Int): Float {
        val travel = travel(index)
        return boardDepth(all[index].both, travel != 0, abs(travel).toFloat())
    }

    /** Where part [index] is [progress] of the way from the one board to the other. */
    fun offset(index: Int, progress: Float): Float = fromY[index] + travel(index) * progress

    // Every part carries the gap above it, so the first one's is not part of the list.
    private fun stack(order: IntArray, ys: IntArray, gap: Int): Int {
        var y = -gap
        for (index in order) {
            ys[index] = y
            y += heights[index]
        }
        return y.coerceAtLeast(0)
    }
}
