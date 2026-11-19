package com.ivandsky.puzzlebox.insights.flags

import org.junit.Assert.assertEquals
import org.junit.Test

class InsightsFlagTest {

    @Test
    fun `the epic ships dark, one flag per part`() {
        assertEquals(
            mapOf("MonthlyRecap" to false, "YearRecap" to false, "StatsPage" to false),
            InsightsFlag.entries.associate { it.key to it.releaseDefault },
        )
    }

    /** A stored override is keyed by the entry's name; a drifting key would strand it. */
    @Test
    fun `every key is its entry's name`() {
        assertEquals(InsightsFlag.entries.map { it.name }, InsightsFlag.entries.map { it.key })
    }
}
