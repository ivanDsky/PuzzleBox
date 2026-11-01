package com.ivandsky.puzzlebox.games.zip.render

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.engine.ZipHint
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation

/**
 * Game-authored paint-by-numbers for :core:game-ui's [com.ivandsky.puzzlebox.gameui.BoardGrid]
 * (A16 / F2 §7): row-major cells, interior walls, at most one hint highlight, the play
 * status, and the last rejection for the animation. The game maps its state to this model;
 * game-ui never imports Zip (A16 has no GUI -> GAMES edge — the state -> model call happens
 * in the play feature, ticket 13).
 */
data class ZipRendererModel(
    val n: Int,
    val path: List<Int>, // player's trace as row-major indices — the registered board draws it
    val cells: List<CellRender>,
    val walls: List<WallRender>,
    val hintHighlight: Int?,
    val rejection: RejectedMove?,
    val status: Status,
) : RendererModel {

    // Everything the cell decor needs to draw LinkedIn-style waypoint dots (ticket 22):
    // dot presence + inside label, the visited (on-path) state that switches the dot to
    // its on-path role, the start marker, and the next-target flag (wave 2's breathing
    // target reads it; nothing animates today).
    data class CellRender(
        val label: Int?, // waypoint label rendered inside the dot, 1-based
        val isWaypoint: Boolean,
        val isStart: Boolean, // waypoint 1: the only legal first cell (I4)
        val isOnPath: Boolean, // the player's trace covers this cell
        val isNextTarget: Boolean, // next waypoint in label order not yet traced
    )

    data class WallRender(
        val cellIdx: Int,
        val direction: Direction,
    ) {
        enum class Direction { RIGHT, DOWN }
    }

    /** Last invalid drop, for the rejection animation + mistake counter. */
    data class RejectedMove(
        val cellIdx: Int,
        val reason: ZipValidation.Reason,
    )

    enum class Status { IDLE, PLAYING, COMPLETE }

    companion object {

        fun of(
            state: ZipState,
            hint: Hint?,
            rejection: RejectedMove? = null,
        ): ZipRendererModel {
            val board = state.puzzle.board
            val path = state.path
            val traced = path.mapTo(HashSet()) { it.idx }
            val cells = (0 until board.cellCount).map { idx ->
                val cell = Cell(idx)
                val label = board.waypointLabel(cell)
                CellRender(
                    label = label,
                    isWaypoint = label != null,
                    isStart = idx == board.waypoints.firstOrNull()?.idx,
                    isOnPath = idx in traced,
                    // Waypoint order is a rule (I4), so the target is the lowest-label
                    // waypoint the trace has not reached; none once every waypoint sits
                    // on the path.
                    isNextTarget = label != null && idx !in traced &&
                        board.waypoints.withIndex().none { (i, wp) ->
                            i + 1 < label && wp.idx !in traced
                        },
                )
            }
            val walls = board.walls
                .sortedWith(compareBy({ it.cell.idx }, { it.direction.ordinal }))
                .map { WallRender(it.cell.idx, WallRender.Direction.valueOf(it.direction.name)) }
            return ZipRendererModel(
                n = board.n,
                path = path.map(Cell::idx),
                cells = cells,
                walls = walls,
                hintHighlight = (hint as? ZipHint.Step)?.cell?.idx,
                rejection = rejection,
                status = when {
                    path.isEmpty() -> Status.IDLE
                    ZipRules().isComplete(state) -> Status.COMPLETE
                    else -> Status.PLAYING
                },
            )
        }
    }
}
