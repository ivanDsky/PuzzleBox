package com.ivandsky.puzzlebox.games.queens.editor

/**
 * Fix-list rows for answer crowns (round 6), one per region in region order; empty when the
 * draft has no crowns. [cells] = the region's cells, for Select. Crown rows never block publishing.
 */
data class QueensCrownIssue(val id: String, val region: Int, val cells: Set<Int>) {
    companion object {
        const val DOUBLE: String = "queens.crowns.double"     // "Region X has 2 crowns" (k ≥ 2)
        const val MISSING: String = "queens.crowns.missing"   // "Region X has no crown" (painted region, 0 crowns)
        /** Every id issuesOf can emit (QueensCopyCoverageTest, 25). */
        val ids: Set<String> = setOf(DOUBLE, MISSING)

        fun issuesOf(draft: QueensDraft): List<QueensCrownIssue> {
            if (draft.crowns.isEmpty()) return emptyList()
            val cellsByRegion = draft.regions.indices
                .filter { draft.regions[it] != QueensDraft.UNPAINTED }
                .groupBy { draft.regions[it] }
                .toSortedMap()
            return cellsByRegion.mapNotNull { (region, cells) ->
                val crowns = cells.count { it in draft.crowns }
                val id = when {
                    crowns >= 2 -> DOUBLE
                    crowns == 0 -> MISSING
                    else -> return@mapNotNull null
                }
                QueensCrownIssue(id, region, cells.toSet())
            }
        }
    }
}
