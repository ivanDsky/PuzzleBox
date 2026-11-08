package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.PlayStatsViewModel
import com.ivandsky.puzzlebox.play.ResultViewModel
import com.ivandsky.puzzlebox.play.SlotOpening
import com.ivandsky.puzzlebox.play.SlotWriter
import com.ivandsky.puzzlebox.play.WriteLane
import com.ivandsky.puzzlebox.play.api.GameKey
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
            ),
            writers = { slot -> SlotWriter(slot, saved, lane, writes) },
            completions = get(),
            calendar = get(),
            dispatchers = dispatchers,
        )
    }
    factory { ObserveFriendPuzzles(received = get(), saved = get(), completions = get(), registry = get()) }
    factory { DeleteReceivedPuzzle(received = get(), saved = get()) }
    viewModel { LibraryViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (key: GameKey) -> GameViewModel(key, get(), get(), get()) }
    viewModel { (slot: PlaySlot) -> PlayStatsViewModel(slot, get(), get(), get(), get(), get()) }
    viewModel { (key: ResultKey) -> ResultViewModel(key, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
