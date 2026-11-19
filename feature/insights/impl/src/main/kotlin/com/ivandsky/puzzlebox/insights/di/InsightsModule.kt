package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.insights.domain.recap.ComputePeriodFacts
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveDueRecap
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecap
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecapAvailability
import com.ivandsky.puzzlebox.insights.domain.recap.ReplaySolveFacts
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.insights.hub.InsightsHubViewModel
import com.ivandsky.puzzlebox.insights.recap.RecapViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Insights feature wiring: the feature's release flags ([InsightsFlag]), the recap use cases and the view models of
 * the hub and of a recap story. A story's period arrives as a Koin parameter, so each opened month has its own.
 * [ObserveDueRecap] is the one use case :app reads, for Home's recap card. Use cases that several features share come from :core:domain's `insightsDomainModule`.
 */
val insightsModule: Module = module {
    flagSet("insights", InsightsFlag.entries)

    factory { ReplaySolveFacts(computeStreak = get(), registry = get()) }
    factory { ComputePeriodFacts(computeStreak = get(), replaySolveFacts = get()) }
    factory { ObserveRecap(observeGameHistory = get(), computePeriodFacts = get(), registry = get()) }
    factory { ObserveRecapAvailability(observeGameHistory = get(), calendar = get(), registry = get()) }
    factory { ObserveDueRecap(availability = get()) }

    viewModel { InsightsHubViewModel(observeRecapAvailability = get(), cloudSync = get()) }
    viewModel { params ->
        RecapViewModel(period = params.get(), observeRecap = get(), cloudSync = get(), savedState = get())
    }
}
