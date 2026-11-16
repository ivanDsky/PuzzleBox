package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import kotlin.random.Random

/** Half-painted drafts cut from generated unique boards: the completer's test and measurement input. */
object QueensDraftCuts {
    /**
     * [started] random regions of [board], each painted as a connected blob that stays inside the
     * region's piece around its queen: a random share of the piece, or all of it when [whole].
     * [crowns] of those regions hold their queen as an answer crown; the blob then covers it.
     */
    fun cut(board: QueensPuzzle, started: Int, crowns: Int, random: Random, whole: Boolean = false): QueensDraft {
        val n = board.size
        val queens = checkNotNull(board.solution) { "the board carries no solution" }.map { it.idx }
        val picked = (0 until n).shuffled(random).take(started)
        val crowned = picked.take(crowns).map { region -> queens.single { board.regions[it] == region } }.toSet()
        val regions = MutableList(n * n) { QueensDraft.UNPAINTED }
        for (region in picked) {
            val queen = queens.single { board.regions[it] == region }
            val piece = piece(board, queen)
            val start = if (queen in crowned) queen else piece[random.nextInt(piece.size)]
            val target = if (whole) piece.size else random.nextInt(1, piece.size + 1)
            blob(n, piece.toSet(), start, target, random).forEach { regions[it] = region }
        }
        return QueensDraft(n, regions, crowned)
    }

    /** The 4-connected cells of [from]'s region that hold [from]: generated regions may be split. */
    private fun piece(board: QueensPuzzle, from: Int): List<Int> {
        val seen = linkedSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            neighbours(queue.removeFirst(), board.size)
                .filter { board.regions[it] == board.regions[from] && seen.add(it) }
                .forEach(queue::addLast)
        }
        return seen.toList()
    }

    /** [target] cells of [piece] grown from [start], one random frontier cell at a time. */
    private fun blob(n: Int, piece: Set<Int>, start: Int, target: Int, random: Random): Set<Int> {
        val blob = linkedSetOf(start)
        while (blob.size < target) {
            val frontier = blob.flatMap { neighbours(it, n) }.filter { it in piece && it !in blob }.distinct()
            blob += frontier[random.nextInt(frontier.size)]
        }
        return blob
    }

    private fun neighbours(cell: Int, n: Int): List<Int> = listOfNotNull(
        (cell - n).takeIf { cell / n > 0 },
        (cell + n).takeIf { cell / n < n - 1 },
        (cell - 1).takeIf { cell % n > 0 },
        (cell + 1).takeIf { cell % n < n - 1 },
    )
}
