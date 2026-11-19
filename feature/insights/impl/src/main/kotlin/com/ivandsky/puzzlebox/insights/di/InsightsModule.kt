package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Insights feature wiring. For now it declares the feature's release flags ([InsightsFlag]); the recap and stats view
 * models are bound here as their screens land. Its use cases come from :core:domain's `insightsDomainModule`.
 */
val insightsModule: Module = module {
    flagSet("insights", InsightsFlag.entries)
}
