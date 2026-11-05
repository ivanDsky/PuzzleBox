package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.PlayViewModel
import com.ivandsky.puzzlebox.play.ResultViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Play feature wiring. The [com.ivandsky.puzzlebox.game.GameRegistry] and the
 * [com.ivandsky.puzzlebox.gameui.GamePlayRegistry] come from the app's aggregation
 * (ADR-0001: one registration per game); the repositories (kept sessions and the
 * tutorial-seen preferences included) from :core:data's dataModule and the picker's
 * today's set from :core:domain's domainModule.
 * This module names no game.
 *
 * Ticket 23: [PlaySessionStore] is a single so the live play outlives the play entry's
 * composition across the result route — the record write never gets cancelled by
 * navigation and "Play again" resets in place. The route VMs are thin views over it.
 */
val playModule = module {
    single { PlaySessionStore(games = get(), ui = get(), daily = get(), completions = get(), kept = get()) }
    viewModel { PlayViewModel(store = get(), todaySet = get(), games = get(), plays = get(), prefs = get()) }
    viewModel { ResultViewModel(get()) }
}
