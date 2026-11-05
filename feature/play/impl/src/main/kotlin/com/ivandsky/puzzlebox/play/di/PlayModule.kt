package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.HubViewModel
import com.ivandsky.puzzlebox.play.PlaySessionStore
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
 * (ADR-0001: one registration per game); the repositories and the calendar from
 * :core:data's dataModule. This module names no game.
 *
 * [PlaySessionStore] is a single so live plays outlive their entries' compositions. Its
 * slot opening and every slot's writer share ONE [WriteLane], so a released slot's final
 * save is on disk before the same slot's reopen reads it; the writers' scope is process
 * lifetime, so a release never cancels a queued save.
 */
val playModule = module {
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
                replays = get(),
                saved = saved,
                calendar = get(),
                games = get(),
                lane = lane,
            ),
            writers = { slot -> SlotWriter(slot, saved, lane, writes) },
            completions = get(),
            calendar = get(),
            dispatchers = dispatchers,
        )
    }
    viewModel { HubViewModel(get(), get()) }
    viewModel { (key: GameKey) -> GameViewModel(key, get(), get()) }
    viewModel { (key: ResultKey) -> ResultViewModel(key, get(), get(), get(), get(), get()) }
}
