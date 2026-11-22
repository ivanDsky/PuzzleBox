package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.ReplayCompareViewModel
import com.ivandsky.puzzlebox.play.ReplayViewModel
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The replay's wiring, included by [playModule]: the replay's and the compare's view models. */
val replayModule = module {
    viewModel { (key: ReplayKey) -> ReplayViewModel(key, get(), get(), get(), get(), get()) }
    viewModel { (key: ReplayCompareKey) -> ReplayCompareViewModel(key, get(), get(), get(), get(), get()) }
}
