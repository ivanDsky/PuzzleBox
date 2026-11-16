package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoardGenerator
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import kotlin.random.Random

/** Partial drafts cut from the answers of generated unique boards: the completer's test and measurement input. */
internal object TangoDraftCuts {
    /** The one answer of `TangoBoardGenerator.boards(n, seed).minimal`, row-major. */
    fun answer(n: Int, seed: Long): List<TangoCell> =
        checkNotNull(TangoUniqueness.solve(TangoBoardGenerator.boards(n, seed).minimal)) { "no answer for n$n seed $seed" }

    /** How many interior edges an [n] × [n] board has. */
    fun edgeCount(n: Int): Int = 2 * n * (n - 1)

    /**
     * [givens] random cells of [answer] and [markers] random interior edges, each marker's kind
     * read off [answer]: a draft that [answer] completes.
     */
    fun cut(n: Int, answer: List<TangoCell>, givens: Int, markers: Int, random: Random): TangoDraft {
        val cells = (0 until n * n).shuffled(random).take(givens)
        val edges = (0 until n * n).flatMap { cell ->
            listOfNotNull(
                (cell to TangoEdgeSide.RIGHT).takeIf { cell % n < n - 1 },
                (cell to TangoEdgeSide.DOWN).takeIf { cell / n < n - 1 },
            )
        }.shuffled(random).take(markers)
        return TangoDraft(
            n,
            cells.associateWith { answer[it] },
            edges.map { (cell, side) ->
                val neighbor = if (side == TangoEdgeSide.RIGHT) cell + 1 else cell + n
                TangoMarker(cell, side, if (answer[cell] == answer[neighbor]) TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE)
            }.canonicalMarkers(),
        )
    }
}
