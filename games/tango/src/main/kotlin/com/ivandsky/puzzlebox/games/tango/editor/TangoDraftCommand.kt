package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind

/**
 * Tango draft edits as undoable commands (spec A5). Clear and other whole-board edits use
 * [com.ivandsky.puzzlebox.game.editor.ReplaceDraft], not a Tango command.
 */
sealed interface TangoDraftCommand : Command<TangoDraft> {
    /** One cell tap = one step; a null [value] empties the cell. [previous] is what revert restores. */
    data class SetCell(val cell: Int, val value: TangoCell?, val previous: TangoCell?) : TangoDraftCommand {
        override fun apply(state: TangoDraft): TangoDraft = state.withCell(cell, value)
        override fun revert(state: TangoDraft): TangoDraft = state.withCell(cell, previous)
    }

    /** One edge tap = one step; a null [kind] removes the marker. [previous] is what revert restores. */
    data class SetMarker(
        val cell: Int,
        val side: TangoEdgeSide,
        val kind: TangoMarkerKind?,
        val previous: TangoMarkerKind?,
    ) : TangoDraftCommand {
        override fun apply(state: TangoDraft): TangoDraft = state.withMarker(cell, side, kind)
        override fun revert(state: TangoDraft): TangoDraft = state.withMarker(cell, side, previous)
    }
}

/** empty → sun → moon → empty. */
fun TangoDraft.cycleCell(cell: Int): TangoDraftCommand.SetCell {
    val current = givens[cell]
    val next = when (current) {
        null -> TangoCell.SUN
        TangoCell.SUN -> TangoCell.MOON
        TangoCell.MOON -> null
    }
    return TangoDraftCommand.SetCell(cell, next, current)
}

/** none → `=` → `×` → none. */
fun TangoDraft.cycleMarker(cell: Int, side: TangoEdgeSide): TangoDraftCommand.SetMarker {
    val current = markers.firstOrNull { it.cell == cell && it.side == side }?.kind
    val next = when (current) {
        null -> TangoMarkerKind.SAME
        TangoMarkerKind.SAME -> TangoMarkerKind.OPPOSITE
        TangoMarkerKind.OPPOSITE -> null
    }
    return TangoDraftCommand.SetMarker(cell, side, next, current)
}

private fun TangoDraft.withCell(cell: Int, value: TangoCell?): TangoDraft =
    copy(givens = if (value == null) givens - cell else givens + (cell to value))

/** Sets, replaces or removes the one marker on the edge, keeping [canonicalMarkers] order. */
private fun TangoDraft.withMarker(cell: Int, side: TangoEdgeSide, kind: TangoMarkerKind?): TangoDraft {
    val others = markers.filterNot { it.cell == cell && it.side == side }
    return copy(markers = (if (kind == null) others else others + TangoMarker(cell, side, kind)).canonicalMarkers())
}

/** (cell, side) order, one marker per edge (the last listed wins): the only order a draft is built in. */
internal fun List<TangoMarker>.canonicalMarkers(): List<TangoMarker> =
    asReversed().distinctBy { it.cell to it.side }.sortedWith(compareBy({ it.cell }, { it.side.ordinal }))
