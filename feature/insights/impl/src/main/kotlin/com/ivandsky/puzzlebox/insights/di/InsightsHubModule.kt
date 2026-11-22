package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.insights.hub.InsightsHubViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The hub's wiring, included by [insightsModule]: its view model. */
val insightsHubModule = module {
    viewModel {
        InsightsHubViewModel(observeRecapAvailability = get(), cloudSync = get(), releaseFlags = get(), openings = get(), puzzleDay = get())
    }
}
