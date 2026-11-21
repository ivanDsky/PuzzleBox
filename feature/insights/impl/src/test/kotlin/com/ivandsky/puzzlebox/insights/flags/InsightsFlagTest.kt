package com.ivandsky.puzzlebox.insights.flags

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsFlagTest {

    @Test
    fun `the epic ships on, one flag per part`() {
        assertEquals(
            mapOf("MonthlyRecap" to true, "YearRecap" to true, "StatsPage" to true),
            InsightsFlag.entries.associate { it.key to it.releaseDefault },
        )
    }

    /** A stored override is keyed by the entry's name; a drifting key would strand it. */
    @Test
    fun `every key is its entry's name`() {
        assertEquals(InsightsFlag.entries.map { it.name }, InsightsFlag.entries.map { it.key })
    }
}
