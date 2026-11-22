package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.MatchLegResultViewModel
import com.ivandsky.puzzlebox.play.MatchLegViewModel
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.play.domain.ObserveLegTarget
import java.time.Instant
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** A duel leg's wiring, included by [playModule]: [FinishLeg], [ObserveLegTarget] and the leg's and its result's view models. */
val matchLegModule = module {
    factory { FinishLeg(outbox = get(), self = get(), identity = get(), clock = { Instant.now() }) }
    factory { ObserveLegTarget(self = get(), preferences = get()) }
    viewModel { (key: MatchLegResultKey) ->
        MatchLegResultViewModel(
            key = key,
            store = get(),
            observeMatch = get(),
            games = get(),
            ui = get(),
            loadReplay = get(),
        )
    }
    viewModel { (key: MatchLegKey) ->
        MatchLegViewModel(key = key, store = get(), matches = get(), outbox = get(), identity = get(), self = get(), legTarget = get())
    }
}
