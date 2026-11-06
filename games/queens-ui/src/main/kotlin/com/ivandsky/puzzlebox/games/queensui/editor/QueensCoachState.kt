package com.ivandsky.puzzlebox.games.queensui.editor

import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft

/**
 * The coach line under the Queens editor board (flow C, round 6): one step of "crowns, then
 * regions" at a time. Pure. Paint mode with no crowns shows only [Unique], so regions-first
 * authoring is never nagged about crowns.
 */
sealed interface QueensCoachState {
    /** Paint mode with no crowns and no Unique; Checking; Undetermined; None. */
    data object Hidden : QueensCoachState

    /** "Place {N} crowns where the answer goes ({k}/{N})". */
    data class PlaceCrowns(val placed: Int, val total: Int) : QueensCoachState

    /** N crowns, Incomplete: "Paint a region around each crown — drag out from a crown". */
    data object PaintRegions : QueensCoachState

    /** Crowns + Multiple: "2 answers fit — ghost crowns show the other one. Move a highlighted cell to another colour". */
    data object Ambiguous : QueensCoachState

    /** Unique: "Unique ✓ — try solving it". */
    data object Unique : QueensCoachState

    companion object {
        fun of(draft: QueensDraft, verdict: Verdict, mode: QueensEditorMode): QueensCoachState {
            val placed = draft.crowns.size
            return when (verdict) {
                is Verdict.Unique -> Unique
                is Verdict.Incomplete, is Verdict.Multiple -> when {
                    placed == 0 && mode == QueensEditorMode.Paint -> Hidden
                    verdict is Verdict.Multiple && placed > 0 -> Ambiguous
                    placed < draft.size -> PlaceCrowns(placed, draft.size)
                    else -> PaintRegions
                }
                Verdict.Checking, Verdict.Undetermined, is Verdict.None -> Hidden
            }
        }
    }
}
