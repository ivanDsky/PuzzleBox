package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.play.GamePageRankViewModel
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.PlayStatsViewModel
import com.ivandsky.puzzlebox.play.domain.ObservePlayStats
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The library and the game page's wiring, included by [playModule]: the library's view model, the game page's rank row,
 * the tablet stats panel's [ObservePlayStats] and view model. All three view models take the LinkedIn flag as the
 * parameter, so a graph without LinkedIn bindings builds them with it off.
 */
val libraryModule = module {
    factory {
        ObservePlayStats(
            todaySet = get(),
            history = get(),
            leaderboard = get(),
        )
    }
    // The flag arrives as the parameter, so a graph without LinkedIn bindings builds the library with it off.
    viewModel { params ->
        LibraryViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
            identity = get(),
        )
    }
    // Same flag-as-parameter rule as the library: a graph without LinkedIn bindings builds the rank row with it off.
    // The app always binds ObserveFriendDay.Binding.
    viewModel { params ->
        GamePageRankViewModel(
            params.get<GameId>(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
            friendDay = get<ObserveFriendDay.Binding>().reader,
        )
    }
    viewModel { params ->
        PlayStatsViewModel(
            params.get<PlaySlot>(), get(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
        )
    }
}
