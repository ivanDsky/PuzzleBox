package com.ivandsky.puzzlebox.games.queens.editor

/**
 * Fix-list rows for answer crowns, one per region in region order; empty when the
 * draft has no crowns. [cells] = the region's cells, for Select. Crown rows never block publishing.
 */
data class QueensCrownIssue(val id: String, val region: Int, val cells: Set<Int>) {
    companion object {
        /** A region holding two or more crowns. */
        const val DOUBLE: String = "queens.crowns.double"
        /** A painted region with no crown; unpainted cells never form a region. */
        const val MISSING: String = "queens.crowns.missing"
        /** Every id issuesOf can emit. */
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
