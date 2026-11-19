package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.common.flags.FlagSet
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication

class InsightsModuleTest {

    @Test
    fun `the module declares every insights flag as the insights set`() {
        val set = koinApplication { modules(insightsModule) }.koin.get<FlagSet>(named("flags.insights"))

        assertEquals("insights", set.owner)
        assertEquals(InsightsFlag.entries, set.flags)
    }
}
