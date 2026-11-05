package com.ivandsky.puzzlebox.domain.di

import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.OpenPastDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodayLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import java.time.LocalDate
import org.koin.dsl.module

/**
 * Use-case wiring: stateless factories over :core:data's repositories and the app's
 * [com.ivandsky.puzzlebox.game.GameRegistry]. Today's leaderboard day is the device's
 * local date, read when the board (re)starts.
 */
val domainModule = module {
    factory { ObserveGameHistory(registry = get(), daily = get(), completions = get()) }
    factory { GetFeaturedGame(registry = get()) }
    factory { ComputeStreak() }
    factory { OpenPastDay(daily = get(), registry = get()) }
    factory { ObserveTodaySet(registry = get(), saved = get(), completions = get(), calendar = get()) }
    factory {
        ObserveTodayLeaderboard(
            identity = get(),
            leaderboard = get(),
            featured = get(),
            today = { LocalDate.now() },
        )
    }
}
