package com.ivandsky.puzzlebox.games.zip.render

import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.WallDirection
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

    data class CellRender(
        val numberLabel: Int?, // waypoint label rendered in the cell corner, 1-based
        val isWaypoint: Boolean,
        val isStart: Boolean, // waypoint 1: the only legal first cell (I4)
    )

    data class WallRender(
        val cellIdx: Int,
        val direction: WallDirection,
    )

    /** Last invalid drop, for the rejection animation + mistake counter. [cellIdx] is the
     *  offending cell, null when the verdict names none (foreign move). */
    data class RejectedMove(
        val cellIdx: Int?,
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
            val cells = (0 until board.cellCount).map { idx ->
                val cell = Cell(idx)
                val label = board.waypointLabel(cell)
                CellRender(
                    numberLabel = label,
                    isWaypoint = label != null,
                    isStart = idx == board.waypoints.firstOrNull()?.idx,
                )
            }
            // single rules instance (companion) — never allocated per frame
            val walls = board.walls
                .sortedWith(compareBy({ it.cell.idx }, { it.direction.ordinal }))
                .map { WallRender(it.cell.idx, it.direction) }
            return ZipRendererModel(
                n = board.n,
                path = path.map(Cell::idx),
                cells = cells,
                walls = walls,
                hintHighlight = (hint as? ZipHint.Step)?.cell?.idx,
                rejection = rejection,
                status = when {
                    path.isEmpty() -> Status.IDLE
                    ZipRules.isComplete(state) -> Status.COMPLETE
                    else -> Status.PLAYING
                },
            )
        }
    }
}
