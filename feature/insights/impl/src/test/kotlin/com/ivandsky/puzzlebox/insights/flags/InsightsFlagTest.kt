package com.ivandsky.puzzlebox.insights.flags

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsFlagTest {

    @Test
    fun `the epic ships on, one flag per part, and the stats filters ship dark`() {
        assertEquals(
            mapOf("StatsFilters" to false),
            InsightsFlag.entries.associate { it.key to it.releaseDefault },
        )
    }

    @Test
    fun `the stats filters flag is labelled for the debug Release flags section`() {
        assertEquals("Stats filters", InsightsFlag.StatsFilters.label)
    }

    /** A stored override is keyed by the entry's name; a drifting key would strand it. */
    @Test
    fun `every key is its entry's name`() {
        assertEquals(InsightsFlag.entries.map { it.name }, InsightsFlag.entries.map { it.key })
    }
}
