package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import kotlin.random.Random

/** Half-built drafts cut from generated unique boards: the completer's test and measurement input. */
object ZipDraftCuts {
    /**
     * The first [length] cells of [board]'s path as the author's stroke, with [marks] of its
     * inner cells numbered and [walls] of the board's walls. Built with the editor's own edits,
     * so the numbers are what the Studio would hold: both ends of the stroke and the marks.
     */
    fun drawn(board: ZipPuzzle, length: Int, marks: Int, walls: Int, random: Random): ZipDraft {
        val stroke = checkNotNull(board.solution) { "the board carries no solution" }.map { it.idx }.take(length)
        val blank = ZipDraft(board.board.n, walls(board, walls, random), emptyList(), emptyList())
        val marked = stroke.drop(1).dropLast(1).shuffled(random).take(marks)
        return marked.fold(blank.stroke(stroke).apply(blank)) { draft, cell -> checkNotNull(draft.toggleMark(cell)).apply(draft) }
    }

    /** No path, as a shared board arrives: the board's two end numbers, [inner] of the numbers between them and [walls] of its walls. */
    fun numbered(board: ZipPuzzle, inner: Int, walls: Int, random: Random): ZipDraft {
        val numbers = board.board.waypoints.map { it.idx }
        val kept = numbers.drop(1).dropLast(1).shuffled(random).take(inner).toSet() + numbers.first() + numbers.last()
        return ZipDraft(board.board.n, walls(board, walls, random), emptyList(), numbers.filter { it in kept })
    }

    /** [count] of the board's walls. Sorted first: a wall's hash, and so the set's order, changes from run to run. */
    private fun walls(board: ZipPuzzle, count: Int, random: Random): Set<Wall> =
        board.board.walls.sortedBy { it.cell.idx * 2 + it.direction.ordinal }.shuffled(random).take(count).toSet()
}
