package com.ivandsky.puzzlebox.games.zip.engine

/**
 * Identity of a [ZipRule] (A18 addendum §1): a value class over [Int] so the hot path
 * ([ZipRule.violated]) carries zero boxing — ids stay inlined Ints everywhere they travel.
 * The numbers are stable diagnostics: never renumber.
 */
@JvmInline
value class ZipRuleId(val value: Int) {
    companion object {
        const val START_CELL = 1
        const val ADJACENCY = 2
        const val WALL_CROSSING = 3
        const val CELL_REUSE = 4
        const val WAYPOINT_ORDER = 5
        const val COVERAGE_ON_COMPLETE = 6
        const val END_AT_LAST_WAYPOINT = 7

        val StartCell = ZipRuleId(START_CELL)
        val Adjacency = ZipRuleId(ADJACENCY)
        val WallCrossing = ZipRuleId(WALL_CROSSING)
        val CellReuse = ZipRuleId(CELL_REUSE)
        val WaypointOrder = ZipRuleId(WAYPOINT_ORDER)
        val CoverageOnComplete = ZipRuleId(COVERAGE_ON_COMPLETE)
        val EndAtLastWaypoint = ZipRuleId(END_AT_LAST_WAYPOINT)
    }
}
