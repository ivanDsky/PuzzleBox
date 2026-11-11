package com.ivandsky.puzzlebox.gameui.board

import kotlin.math.abs
import kotlin.math.roundToInt

private const val MaxCells = 8
private const val PodiumPlaces = 3

/**
 * The numbers a place rolls through, as a column of [cells] that scrolls from index [start] (the old place) to index
 * [end] (the new one). Going down the board the cells are in the order the roll passes them, and going up they are
 * reversed, so the column always scrolls the way the place moves.
 */
internal class RankStrip(val cells: List<Int>, val start: Int, val end: Int) {
    val rolls: Boolean get() = cells.size > 1

    /** The number nearest the middle when the column is [offset] cells down it. */
    fun centre(offset: Float): Int = cells[offset.roundToInt().coerceIn(0, cells.lastIndex)]
}

/** Every place from [from] to [to] when they are at most seven apart, otherwise eight evenly spaced ones. */
internal fun rankStrip(from: Int, to: Int): RankStrip {
    if (from == to) return RankStrip(listOf(to), 0, 0)
    val span = to - from
    val path = if (abs(span) < MaxCells) {
        if (span > 0) (from..to).toList() else (from downTo to).toList()
    } else {
        List(MaxCells) { j -> (from + span * j / (MaxCells - 1).toDouble()).roundToInt() }
    }
    return if (span > 0) RankStrip(path, 0, path.lastIndex) else RankStrip(path.reversed(), path.lastIndex, 0)
}

internal enum class MedalChange { None, FlipIn, Drain }

internal fun isPodium(place: Int?): Boolean = place != null && place in 1..PodiumPlaces

/**
 * What a place change does to the medal: it flips in when the place enters the podium, drains out when it leaves it,
 * and does neither between two podium places (the disc just changes colour as the column scrolls). Nothing happens on
 * the first sight of a place ([from] null) unless the row has just [arrival] on the podium.
 */
internal fun medalChange(from: Int?, to: Int?, arrival: Boolean = false): MedalChange = when {
    from == null -> if (arrival && isPodium(to)) MedalChange.FlipIn else MedalChange.None
    isPodium(to) && !isPodium(from) -> MedalChange.FlipIn
    isPodium(from) && !isPodium(to) -> MedalChange.Drain
    else -> MedalChange.None
}
