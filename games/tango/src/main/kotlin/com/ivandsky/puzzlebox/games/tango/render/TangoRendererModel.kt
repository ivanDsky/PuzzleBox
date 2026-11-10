package com.ivandsky.puzzlebox.games.tango.render

import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict

/**
 * Game-authored paint-by-numbers for the tango-ui board (ZipRendererModel shape): the
 * merged grid, immutable givens, the edge markers (A12), the last rejection for the
 * feedback animation, and the play status. :core:game-ui and tango-ui never import
 * Tango state — the state -> model call happens in the plugin.
 */
data class TangoRendererModel(
    val n: Int,
    /** Row-major merged grid (givens + placements), `null` = empty player cell. */
    val cells: List<CellRender>,
    val markers: List<MarkerRender>,
    val rejection: RejectedMove?,
    val status: Status,
    /** Cell-sets of violations NEWLY introduced by the last placement, hidden until
     *  0.5 s after the last touch (user QA 2026-11-02). Pre-existing violations are
     *  never suppressed — an unfixed error only leaves when the grid fixes it. */
    val suppressedSets: Set<Set<Int>> = emptySet(),
    /** A guided lesson's answer (spec A12) on each open square that doesn't hold it yet,
     *  drawn faintly until the square matches. Empty on every unguided board. */
    val ghosts: Map<Int, TangoCell> = emptyMap(),
) : RendererModel {

    data class CellRender(
        val value: TangoCell?,
        val isGiven: Boolean,
    )

    data class MarkerRender(
        val cellIdx: Int,
        val side: TangoEdgeSide,
        val kind: TangoMarkerKind,
    )

    /** Last rejected placement, for the violation feedback. [idx] null when the verdict names none. */
    data class RejectedMove(
        val idx: Int?,
        val reason: TangoVerdict.Reason,
    )

    enum class Status { IDLE, PLAYING, COMPLETE }

    companion object {

        fun of(
            state: TangoState,
            verdict: MoveVerdict? = null,
            suppressedSets: Set<Set<Int>> = emptySet(),
        ): TangoRendererModel {
            val merged = state.grid
            return TangoRendererModel(
                n = state.puzzle.n,
                cells = merged.withIndex().map { (idx, value) ->
                    CellRender(value = value, isGiven = state.puzzle.isGiven(idx))
                },
                markers = state.puzzle.markers
                    .sortedWith(compareBy({ it.cell }, { it.side.ordinal }))
                    .map { MarkerRender(it.cell, it.side, it.kind) },
                rejection = (verdict as? TangoVerdict.Rejected)?.let { RejectedMove(it.idx, it.reason) },
                status = when {
                    TangoRules.isComplete(state) -> Status.COMPLETE
                    merged.withIndex().all { (idx, value) -> state.puzzle.isGiven(idx) || value == null } -> Status.IDLE
                    else -> Status.PLAYING
                },
                suppressedSets = suppressedSets,
            )
        }
    }
}
