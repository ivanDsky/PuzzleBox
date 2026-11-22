package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.domain.ranks.LoadFriendsPastPage
import com.ivandsky.puzzlebox.play.ResultViewModel
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.play.domain.nextthing.ObserveResultNextThing
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The result's wiring, included by [playModule]: its insights ([ObserveResultInsights]) and next thing
 * ([ObserveResultNextThing]), both Play's own, defined here as :core:domain reads no flag (ADR-0022), and its view model.
 */
val resultModule = module {
    factory {
        ObserveResultInsights(
            registry = get(),
            history = get(),
            computeStreak = get(),
            puzzleDay = get(),
        )
    }
    factory {
        ObserveResultNextThing(
            registry = get(),
            identity = get(),
            history = get(),
            todaySet = get(),
            friendPuzzles = get(),
            friendUids = get(),
            leaderboard = get(),
            visibility = get(),
            computeStreak = get(),
            puzzleDay = get(),
            nextRollover = get(),
            communityPuzzles = get(),
            friendsPast = get<LoadFriendsPastPage>(),
        )
    }
    // The flag-as-parameter rule again: with it off the result never touches LinkedIn. The Post button's flag is
    // read inside the view model (the release flag), so its bindings resolve only on the same parameter.
    // The next-thing use case is resolved only when the view model first asks for it (today's solve).
    viewModel { params ->
        val linkedIn = params.getOrNull<Boolean>() == true
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
            linkedIn = if (linkedIn) get() else null,
            board = get(),
            completions = get(),
            loadReplay = get(),
            observePost = if (linkedIn) get() else null,
            submitPost = if (linkedIn) get() else null,
            nextThing = inject(),
            daily = get(),
        )
    }
}
