package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection

/**
 * Zip draft edits as undoable commands. Resize and clear-board use
 * [com.ivandsky.puzzlebox.game.editor.ReplaceDraft], not a Zip command.
 */
sealed interface ZipDraftCommand : Command<ZipDraft> {
    /** One stroke, Reverse or Clear path = one step. */
    data class PathEdit(
        val before: List<Int>,
        val after: List<Int>,
        val waypointsBefore: List<Int>,
        val waypointsAfter: List<Int>,
    ) : ZipDraftCommand {
        override fun apply(state: ZipDraft): ZipDraft = state.copy(path = after, waypoints = waypointsAfter)
        override fun revert(state: ZipDraft): ZipDraft = state.copy(path = before, waypoints = waypointsBefore)
    }

    /** Numbers a path cell (or removes its number); endpoints stay numbered. */
    data class ToggleMark(val cell: Int, val waypointsBefore: List<Int>, val waypointsAfter: List<Int>) : ZipDraftCommand {
        override fun apply(state: ZipDraft): ZipDraft = state.copy(waypoints = waypointsAfter)
        override fun revert(state: ZipDraft): ZipDraft = state.copy(waypoints = waypointsBefore)
    }

    /** Adds the wall, or removes it when present; the step is its own inverse. */
    data class ToggleWall(val wall: Wall) : ZipDraftCommand {
        override fun apply(state: ZipDraft): ZipDraft = flip(state)
        override fun revert(state: ZipDraft): ZipDraft = flip(state)

        private fun flip(state: ZipDraft): ZipDraft =
            state.copy(walls = if (wall in state.walls) state.walls - wall else state.walls + wall)
    }

    /** Clear walls = one step. */
    data class ClearWalls(val before: Set<Wall>) : ZipDraftCommand {
        override fun apply(state: ZipDraft): ZipDraft = state.copy(walls = emptySet())
        override fun revert(state: ZipDraft): ZipDraft = state.copy(walls = before)
    }
}

/** Replace the path with [path]; marks the new path still visits keep their path order, its ends are numbered. */
fun ZipDraft.stroke(path: List<Int>): ZipDraftCommand.PathEdit =
    ZipDraftCommand.PathEdit(this.path, path, waypoints, if (path.isEmpty()) waypoints else numbered(path, interiorMarks()))

/** The path run backwards, marks renumbered to follow it. */
fun ZipDraft.reversed(): ZipDraftCommand.PathEdit = stroke(path.asReversed())

/** Null off the path and on either end, which are always numbered. */
fun ZipDraft.toggleMark(cell: Int): ZipDraftCommand.ToggleMark? {
    if (cell !in path || cell == path.first() || cell == path.last()) return null
    val marks = interiorMarks()
    val next = if (cell in marks) marks - cell else marks + cell
    return ZipDraftCommand.ToggleMark(cell, waypoints, numbered(path, next))
}

/** Null when adding the wall would cut between two consecutive path cells or it is not between two cells. */
fun ZipDraft.toggleWall(wall: Wall): ZipDraftCommand.ToggleWall? {
    if (wall in walls) return ZipDraftCommand.ToggleWall(wall)
    val from = wall.cell.idx
    val to = when (wall.direction) {
        WallDirection.RIGHT -> if (from % n < n - 1) from + 1 else return null
        WallDirection.DOWN -> if (from / n < n - 1) from + n else return null
    }
    val cuts = path.zipWithNext().any { (a, b) -> (a == from && b == to) || (a == to && b == from) }
    return if (cuts) null else ZipDraftCommand.ToggleWall(wall)
}

/** Waypoints that are neither end of the path. */
private fun ZipDraft.interiorMarks(): List<Int> =
    waypoints.filter { path.isEmpty() || (it != path.first() && it != path.last()) }

/** `[first]` + [marks] on [path] in path order + `[last]`; a one-cell path numbers its cell once. */
private fun numbered(path: List<Int>, marks: List<Int>): List<Int> {
    val position = path.withIndex().associate { (i, cell) -> cell to i }
    val middle = marks.filter { it in position && it != path.first() && it != path.last() }
        .distinct().sortedBy { position.getValue(it) }
    val ends = if (path.size == 1) listOf(path.first()) else listOf(path.first(), path.last())
    return listOf(ends.first()) + middle + ends.drop(1)
}
