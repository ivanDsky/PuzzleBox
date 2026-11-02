package com.ivandsky.puzzlebox.games.queens.engine.rules

/**
 * Identity of a [QueensRule] (zip precedent: [com.ivandsky.puzzlebox.games.zip.engine.ZipRuleId]):
 * a value class over [Int] so ids travel as inlined Ints with zero boxing. The numbers are
 * stable diagnostics — never renumber.
 */
@JvmInline
value class QueensRuleId(val value: Int) {
    companion object {
        const val ROW_EXCLUSIVITY = 1
        const val COLUMN_EXCLUSIVITY = 2
        const val REGION_EXCLUSIVITY = 3
        const val NO_TOUCH_ADJACENCY = 4

        val RowExclusivity = QueensRuleId(ROW_EXCLUSIVITY)
        val ColumnExclusivity = QueensRuleId(COLUMN_EXCLUSIVITY)
        val RegionExclusivity = QueensRuleId(REGION_EXCLUSIVITY)
        val NoTouchAdjacency = QueensRuleId(NO_TOUCH_ADJACENCY)

        val all: List<QueensRuleId> =
            listOf(RowExclusivity, ColumnExclusivity, RegionExclusivity, NoTouchAdjacency)
    }
}
