package com.ivandsky.puzzlebox.games.queensui.editor

import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft

/**
 * The cells the author has picked before choosing a ring colour (spec S3). Changing the
 * selection is not an edit: it never touches the draft or its history. [cells] keeps the
 * order cells joined in, so the first and last picked cells anchor an [area].
 */
internal data class QueensSelection(val cells: Set<Int> = emptySet()) {
    /** Toggles [cell]: a selected cell leaves the selection, any other joins it. */
    fun tap(cell: Int): QueensSelection = QueensSelection(if (cell in cells) cells - cell else cells + cell)

    /** Grows the selection by every cell of [draft] painted [region] (`QueensDraft.UNPAINTED` too). */
    fun allOf(draft: QueensDraft, region: Int): QueensSelection {
        require(region == QueensDraft.UNPAINTED || region in 0 until draft.size) { "no region $region on a ${draft.size}×${draft.size} board" }
        return QueensSelection(cells + draft.regions.indices.filter { draft.regions[it] == region })
    }

    /** Grows the selection by the bounding rectangle of [from] and [to] on a [size]×[size] board. */
    fun area(size: Int, from: Int, to: Int): QueensSelection {
        require(from in 0 until size * size && to in 0 until size * size) { "cells $from..$to off a $size×$size board" }
        val rows = minOf(from / size, to / size)..maxOf(from / size, to / size)
        val cols = minOf(from % size, to % size)..maxOf(from % size, to % size)
        return QueensSelection(cells + rows.flatMap { row -> cols.map { col -> row * size + col } })
    }
}
