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
     * The crowns that break a rule with another crown: same row, same column, touching, or the
     * same painted region. Both crowns of a pair are in the set; empty when none clash.
     */
    fun conflicts(draft: QueensDraft): Set<Int> {
        val crowns = draft.crowns.toList()
        return crowns.filterTo(LinkedHashSet()) { crown ->
            crowns.any { other ->
                other != crown && (attacks(other, crown, draft.size) || sharesRegion(draft, other, crown))
            }
        }
    }

    /**
     * The command for a crown-mode tap on [cell]: remove when it holds a crown; else place, even
     * onto a crossed cell (the clash shows through [conflicts]), seeding the lowest region id no
     * crown cell holds when [cell] is UNPAINTED. With every id held the cell stays unpainted.
     */
    fun toggle(draft: QueensDraft, cell: Int): QueensDraftCommand.Crown {
        if (cell in draft.crowns) return QueensDraftCommand.Crown(cell, placed = false, seed = null)
        if (draft.regions[cell] != QueensDraft.UNPAINTED) return QueensDraftCommand.Crown(cell, placed = true, seed = null)
        val held = draft.crowns.mapTo(HashSet()) { draft.regions[it] }
        val seed = (0 until draft.size).firstOrNull { it !in held }
        return QueensDraftCommand.Crown(cell, placed = true, seed = seed)
    }

    private fun sharesRegion(draft: QueensDraft, a: Int, b: Int): Boolean =
        draft.regions[a] != QueensDraft.UNPAINTED && draft.regions[a] == draft.regions[b]

    /** Same row, same column, or touching (the play UI's auto-×). */
    private fun attacks(crown: Int, cell: Int, n: Int): Boolean {
        val dr = abs(crown / n - cell / n)
        val dc = abs(crown % n - cell % n)
        return dr == 0 || dc == 0 || (dr <= 1 && dc <= 1)
    }
}
