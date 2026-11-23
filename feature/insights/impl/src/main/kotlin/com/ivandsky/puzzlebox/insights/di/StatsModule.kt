package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall
import com.ivandsky.puzzlebox.insights.domain.rankhistory.ObserveRankHistory
import com.ivandsky.puzzlebox.insights.domain.stats.ObserveStatsPage
import com.ivandsky.puzzlebox.insights.domain.stats.ObserveStatsSince
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStatsSectionProvider
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStatsSectionProvider
import com.ivandsky.puzzlebox.insights.stats.StatsViewModel
import com.ivandsky.puzzlebox.model.GameId
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The stats page's wiring, included by [insightsModule]: its use cases and view model. The page's vs friends section
 * ([VsFriendsStatsSectionProvider]) reads Friends Overall like the recap's friends card; its rank history section
 * ([RankHistoryStatsSectionProvider]) reads the kept weeks through [ObserveRankHistory], the one reader. A page's game
 * arrives as a Koin parameter, so each opened game has its own.
 */
val statsModule = module {
    factory { ObserveStatsSince(observeGameHistory = get(), calendar = get(), registry = get()) }
    factory {
        val friendsOverall = get<ObserveFriendsOverall>()
        val rankHistory = get<ObserveRankHistory>()
        ObserveStatsPage(
            observeGameHistory = get(),
            computePeriodFacts = get(),
            calendar = get(),
            registry = get(),
            rankHistory = RankHistoryStatsSectionProvider(
                identity = get(),
                rankHistory = { game -> rankHistory(game) },
                friendsOverall = { window, games -> friendsOverall(window, games) },
            ),
            vsFriends = VsFriendsStatsSectionProvider(identity = get(), friendsOverall = { window, games -> friendsOverall(window, games) }),
        )
    }

    viewModel { params ->
        StatsViewModel(
            initialGame = params.getOrNull<String>()?.let(::GameId),
            observeStatsPage = get(),
            registry = get(),
            cloudSync = get(),
            savedState = get(),
            observeSince = get(),
        )
    }
}
