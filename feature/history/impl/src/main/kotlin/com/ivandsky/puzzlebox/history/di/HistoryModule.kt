package com.ivandsky.puzzlebox.history.di

import com.ivandsky.puzzlebox.history.HistoryViewModel
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.ranks.RanksViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * History feature wiring: the use cases come from :core:domain's domainModule, the
 * [com.ivandsky.puzzlebox.game.GameRegistry] and the game brands
 * ([com.ivandsky.puzzlebox.gameui.GamePlayRegistry]) from the app's aggregation (ADR-0001);
 * the optional [com.ivandsky.puzzlebox.data.cloud.CloudSync] is resolved with getOrNull (unbound = no restoring state).
 * The Ranks hub's [RanksViewModel] is bound beside History's; the app decides which entry shows.
 */
val historyModule = module {
    viewModel { (key: HistoryKey) -> HistoryViewModel(get(), get(), get(), get(), get(), get(), getOrNull(), key) }
    viewModel { RanksViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
