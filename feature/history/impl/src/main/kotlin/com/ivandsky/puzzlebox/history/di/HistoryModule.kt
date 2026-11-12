package com.ivandsky.puzzlebox.history.di

import com.ivandsky.puzzlebox.history.HistoryViewModel
import com.ivandsky.puzzlebox.history.api.HistoryKey
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * History feature wiring: the use cases come from :core:domain's domainModule, the
 * [com.ivandsky.puzzlebox.game.GameRegistry] and the game brands
 * ([com.ivandsky.puzzlebox.gameui.GamePlayRegistry]) from the app's aggregation (ADR-0001);
 * the optional [com.ivandsky.puzzlebox.data.cloud.CloudSync] is resolved with getOrNull (unbound = no restoring state).
 */
val historyModule = module {
    viewModel { (key: HistoryKey) -> HistoryViewModel(get(), get(), get(), get(), get(), get(), getOrNull(), key) }
}
