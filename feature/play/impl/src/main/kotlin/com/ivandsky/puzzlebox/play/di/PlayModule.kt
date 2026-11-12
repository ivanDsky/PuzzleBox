package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.GamePageBoardViewModel
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.MatchLegResultViewModel
import com.ivandsky.puzzlebox.play.MatchLegViewModel
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.PlayStatsViewModel
import com.ivandsky.puzzlebox.play.ReplayViewModel
import com.ivandsky.puzzlebox.play.ResultViewModel
import com.ivandsky.puzzlebox.play.SlotOpening
import com.ivandsky.puzzlebox.play.SlotWriter
import com.ivandsky.puzzlebox.play.WriteLane
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Play feature wiring. The [com.ivandsky.puzzlebox.game.GameRegistry] and the
 * [com.ivandsky.puzzlebox.gameui.GamePlayRegistry] come from the app's aggregation
 * (ADR-0001: one registration per game); the repositories (the tutorial-seen preferences
 * included) and the calendar from :core:data's dataModule, and the use cases (today's
 * set, history, stats, the rollover) from :core:domain's domainModule. The tablet stats
 * panel's identity and leaderboard come from the backend module. This module names no game;
 * friend puzzles' wiring is [friendModule], From friends' [ObserveFriendPuzzles] and
 * [DeleteReceivedPuzzle] are here.
 *
 * [PlaySessionStore] is a single so live plays outlive their entries' compositions. Its
 * slot opening and every slot's writer share ONE [WriteLane], so a released slot's final
 * save is on disk before the same slot's reopen reads it; the writers' scope is process
 * lifetime, so a release never cancels a queued save.
 */
val playModule = module {
    includes(friendModule)
    single {
        val lane = WriteLane()
        val saved = get<SavedPlayRepository>()
        val dispatchers = get<DispatcherProvider>()
        val writes = CoroutineScope(SupervisorJob() + dispatchers.main)
        PlaySessionStore(
            games = get(),
            ui = get(),
            opening = SlotOpening(
                daily = get(),
                pastDays = get(),
                saved = saved,
                calendar = get(),
                games = get(),
                lane = lane,
                received = get(),
                matches = get(),
                loadShared = get(),
                dispatchers = dispatchers,
            ),
            writers = { slot -> SlotWriter(slot, saved, lane, writes) },
            completions = get(),
            finishLeg = get(),
            calendar = get(),
            dispatchers = dispatchers,
            // Graphs without the app's build-type flags (feature tests) play with the shipped ones.
            flags = getOrNull() ?: ShippedReleaseFlags,
        )
    }
    factory { ObserveFriendPuzzles(received = get(), saved = get(), completions = get(), registry = get()) }
    factory { DeleteReceivedPuzzle(received = get(), saved = get()) }
    // The flag arrives as the parameter, so a graph without LinkedIn bindings builds the library with it off.
    viewModel { params ->
        LibraryViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
        )
    }
    viewModel { (key: GameKey) -> GameViewModel(key, get(), get(), get()) }
    // Same flag-as-parameter rule as the library: a graph without LinkedIn bindings builds the board with it off.
    viewModel { params ->
        GamePageBoardViewModel(
            params.get<GameId>(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
        )
    }
    viewModel { params ->
        PlayStatsViewModel(
            params.get<PlaySlot>(), get(), get(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
        )
    }
    // The flag-as-parameter rule again: with it off the result never touches LinkedIn.
    viewModel { params ->
        ResultViewModel(
            key = params.get<ResultKey>(),
            store = get(),
            todaySet = get(),
            calendar = get(),
            history = get(),
            rollover = get(),
            ui = get(),
            games = get(),
            insights = get(),
            rank = get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
            board = get(),
            completions = get(),
            loadReplay = get(),
        )
    }
    viewModel { (key: ReplayKey) -> ReplayViewModel(key, get(), get(), get(), get()) }
    viewModel { (key: MatchLegResultKey) ->
        MatchLegResultViewModel(key = key, store = get(), observeMatch = get(), games = get(), ui = get())
    }
    viewModel { (key: MatchLegKey) ->
        MatchLegViewModel(key = key, store = get(), matches = get(), outbox = get(), identity = get())
    }
}
