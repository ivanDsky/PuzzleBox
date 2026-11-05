package com.ivandsky.puzzlebox.home.di

import com.ivandsky.puzzlebox.home.HomeViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Home feature wiring: the use cases come from :core:domain's domainModule, the
 * [com.ivandsky.puzzlebox.game.GameRegistry] from the app's aggregation (ADR-0001).
 */
val homeModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
}
