package com.ivandsky.puzzlebox.home.di

import com.ivandsky.puzzlebox.home.HomeViewModel
import com.ivandsky.puzzlebox.home.domain.GetAppUpdate
import com.ivandsky.puzzlebox.home.domain.ObserveLatestSolvedToday
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Home feature wiring: Home's own use cases ([GetAppUpdate], [ObserveLatestSolvedToday]) live
 * here, the shared ones come from :core:domain's domainModule, the
 * [com.ivandsky.puzzlebox.game.GameRegistry] and the per-game brands
 * ([com.ivandsky.puzzlebox.gameui.GamePlayRegistry]) from the app's aggregation (ADR-0001).
 */
val homeModule = module {
    factory { GetAppUpdate(releases = get(), build = get()) }
    factory { ObserveLatestSolvedToday(completions = get(), calendar = get(), registry = get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
