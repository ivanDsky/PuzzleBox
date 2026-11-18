package com.ivandsky.puzzlebox.compete.create.lineup

/**
 * A drag of the row at [lifted] over rows whose heights, each with the gap that follows it, are [pitches]; [offset] is
 * how far the finger has taken the row from where it lay, in pixels. Pure: the panel measures, feeds the finger's
 * movement in through [dragged], and reads where the row would land ([target]), how far each other row steps aside
 * ([shiftOf]) and, once dropped, where the row still has to travel to its slot ([settleFrom]).
 */
internal data class LineupReorder(
    private val pitches: List<Float>,
    val lifted: Int,
    val offset: Float = 0f,
) {
    private val tops: List<Float> = pitches.runningFold(0f, Float::plus)

    /** The slot the lifted row lands in: past each row whose middle its leading edge has crossed, so either end of the list can be reached. */
    val target: Int
        get() {
            val top = tops[lifted] + offset
            val bottom = top + pitches[lifted]
            val passedBelow = (lifted + 1 until pitches.size).count { bottom >= tops[it] + pitches[it] / 2 }
            val passedAbove = (0 until lifted).count { top < tops[it] + pitches[it] / 2 }
            return lifted + passedBelow - passedAbove
        }

    /** The row moves with the finger but never out of the list. */
    fun dragged(delta: Float): LineupReorder =
        copy(offset = (offset + delta).coerceIn(-tops[lifted], tops.last() - pitches[lifted] - tops[lifted]))

    /** How far the row at [index] is displaced while the lifted row hovers over [target]: the others make room, the lifted one follows the finger. */
    fun shiftOf(index: Int): Float = when {
        index == lifted -> offset
        index in lifted + 1..target -> -pitches[lifted]
        index in target until lifted -> pitches[lifted]
        else -> 0f
    }

    /** After the move is committed the row sits in its new slot; this is how far from there the finger left it. */
    val settleFrom: Float
        get() {
            val slotTop = pitches.filterIndexed { at, _ -> at != lifted }.take(target).sum()
            return tops[lifted] + offset - slotTop
        }

    companion object {
        fun lift(pitches: List<Float>, index: Int): LineupReorder = LineupReorder(pitches, index)
    }
}
