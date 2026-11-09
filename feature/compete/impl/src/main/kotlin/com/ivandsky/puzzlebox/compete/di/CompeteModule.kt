package com.ivandsky.puzzlebox.compete.di

import com.ivandsky.puzzlebox.compete.create.NewDuelViewModel
import com.ivandsky.puzzlebox.compete.duel.DuelViewModel
import com.ivandsky.puzzlebox.compete.tab.DuelsTabViewModel
import java.time.Instant
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Compete feature wiring. [com.ivandsky.puzzlebox.domain.ObserveMyDuels], ObserveMatch and UpdateMatchSeat come from
 * :core:domain's domainModule and the identity service from the app's aggregation; the Duels tab's, the New duel
 * sheet's and the duel screen's view models are bound here (the duel screen's by its code). The saved plays come from
 * :core:data and the result sync from the app, which binds it to the result poster.
 */
val competeModule: Module = module {
    viewModel {
        DuelsTabViewModel(observeMyDuels = get(), updateSeat = get(), identity = get(), clock = { Instant.now() })
    }
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
    viewModel { (code: String) ->
        DuelViewModel(
            code = code,
            observeMatch = get(),
            updateSeat = get(),
            savedPlays = get(),
            resultSync = get(),
            completions = get(),
            identity = get(),
            idTokens = get(),
            registry = get(),
        )
    }
}
