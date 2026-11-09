package com.ivandsky.puzzlebox.compete.di

import com.ivandsky.puzzlebox.compete.create.NewDuelViewModel
import com.ivandsky.puzzlebox.compete.tab.DuelsTabViewModel
import java.time.Instant
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Compete feature wiring. [com.ivandsky.puzzlebox.domain.ObserveMyDuels] comes from :core:domain's domainModule
 * and the identity service from the app's aggregation; the Duels tab's and the New duel sheet's view models are bound here.
 */
val competeModule: Module = module {
    viewModel { DuelsTabViewModel(observeMyDuels = get(), identity = get(), clock = { Instant.now() }) }
    viewModel {
        NewDuelViewModel(
            createMatch = get(),
            identity = get(),
            idTokens = get(),
            todaySet = get(),
            calendar = get(),
            daily = get(),
            registry = get(),
            clock = { Instant.now() },
        )
    }
}
