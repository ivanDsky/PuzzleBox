package com.ivandsky.puzzlebox.games.queens.editor

import kotlin.math.abs

/** Answer-crown rules for authoring (flow C, round 6); pure. */
object QueensCrowns {
    /**
     * Cells × by the crowns: each crown's row, column and 8 neighbours, minus crown cells.
     * No region rule (regions are unsettled while authoring).
     */
    fun crossed(draft: QueensDraft): Set<Int> {
        val n = draft.size
        return draft.regions.indices.filterTo(LinkedHashSet()) { cell ->
            cell !in draft.crowns && draft.crowns.any { crown -> attacks(crown, cell, n) }
        }
    }

    /**
     * The command for a crown-mode tap on [cell]: remove when it holds a crown; null (refused)
     * when [cell] ∈ crossed(draft); else place, seeding the lowest region id no crown cell
     * holds when [cell] is UNPAINTED.
     */
    fun toggle(draft: QueensDraft, cell: Int): QueensDraftCommand.Crown? {
        if (cell in draft.crowns) return QueensDraftCommand.Crown(cell, placed = false, seed = null)
        if (draft.crowns.any { attacks(it, cell, draft.size) }) return null
        if (draft.regions[cell] != QueensDraft.UNPAINTED) return QueensDraftCommand.Crown(cell, placed = true, seed = null)
        val held = draft.crowns.mapTo(HashSet()) { draft.regions[it] }
        // Toggle-built crowns never attack, so `size` of them cover every row and an open cell
        // always has a free id; a hand-made board without one is refused rather than guessed.
        val seed = (0 until draft.size).firstOrNull { it !in held } ?: return null
        return QueensDraftCommand.Crown(cell, placed = true, seed = seed)
    }

    /** Same row, same column, or touching (the play UI's auto-×). */
    private fun attacks(crown: Int, cell: Int, n: Int): Boolean {
        val dr = abs(crown / n - cell / n)
        val dc = abs(crown % n - cell % n)
        return dr == 0 || dc == 0 || (dr <= 1 && dc <= 1)
    }
}
