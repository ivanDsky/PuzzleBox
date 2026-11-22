package com.ivandsky.puzzlebox.insights.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall
import com.ivandsky.puzzlebox.insights.domain.rankhistory.ObserveRankHistory
import com.ivandsky.puzzlebox.insights.domain.recap.ComputePeriodFacts
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveDueRecap
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecap
import com.ivandsky.puzzlebox.insights.domain.recap.ObserveRecapAvailability
import com.ivandsky.puzzlebox.insights.domain.recap.ResolveFriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.ReplaySolveFacts
import com.ivandsky.puzzlebox.insights.domain.stats.ObserveStatsPage
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStatsSectionProvider
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStatsSectionProvider
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.insights.hub.InsightsHubViewModel
import com.ivandsky.puzzlebox.insights.recap.RecapViewModel
import com.ivandsky.puzzlebox.insights.share.RecapShareCleaner
import com.ivandsky.puzzlebox.insights.share.RecapShareRenderer
import com.ivandsky.puzzlebox.insights.share.RecapShareUris
import com.ivandsky.puzzlebox.insights.share.RecapShareViewModel
import org.koin.android.ext.koin.androidContext
import com.ivandsky.puzzlebox.insights.stats.StatsViewModel
import com.ivandsky.puzzlebox.model.GameId
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Insights feature wiring: the feature's release flags ([InsightsFlag]), the recap and stats use cases and the view
 * models of the hub, of a recap story, of its share card and of the stats page. The stats page's vs friends section
 * ([VsFriendsStatsSectionProvider]) reads Friends Overall like the recap's friends card; its rank history section
 * ([RankHistoryStatsSectionProvider]) reads the kept weeks through [ObserveRankHistory], the one reader. The friends standing
 * ([ResolveFriendsStanding]) is the one recap read that goes to the network for a month, kept apart from the local
 * providers; a year's comes from the kept weekly history ([ObserveRankHistory]). A story's period and a stats page's
 * game arrive as Koin parameters, so each opened month, year or game has its own.
 * The share files (renderer, Uris, cleaner) are singletons over the app's cache directory; [RecapShareCleaner] is the
 * one :app starts. [ObserveDueRecap] is the one use case :app reads, for Home's recap card. Use cases that several features share come from :core:domain's `insightsDomainModule`.
 */
val insightsModule: Module = module {
    flagSet("insights", InsightsFlag.entries)

    factory { ReplaySolveFacts(computeStreak = get(), puzzleDay = get(), registry = get()) }
    factory { ComputePeriodFacts(computeStreak = get(), replaySolveFacts = get(), registry = get()) }
    factory { ObserveRankHistory(identity = get(), store = get()) }
    factory {
        val friendsOverall = get<ObserveFriendsOverall>()
        val rankHistory = get<ObserveRankHistory>()
        ResolveFriendsStanding(
            identity = get(),
            friendsOverall = { window, games -> friendsOverall(window, games) },
            rankHistory = { game -> rankHistory(game) },
            puzzleDay = get(),
        )
    }
    factory {
        ObserveRecap(observeGameHistory = get(), computePeriodFacts = get(), registry = get(), resolveFriends = get())
    }
    factory { ObserveRecapAvailability(observeGameHistory = get(), calendar = get(), registry = get()) }
    factory { ObserveDueRecap(availability = get()) }
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

    single { RecapShareRenderer(cacheRoot = androidContext().cacheDir, dispatchers = get()) }
    single { RecapShareUris(androidContext()) }
    single { RecapShareCleaner(renderer = get(), scope = get()) }

    viewModel {
        InsightsHubViewModel(observeRecapAvailability = get(), cloudSync = get(), releaseFlags = get(), openings = get(), puzzleDay = get())
    }
    viewModel { params ->
        RecapViewModel(period = params.get(), observeRecap = get(), cloudSync = get(), puzzleDay = get(), openings = get(), savedState = get())
    }
    viewModel { params ->
        val uris = get<RecapShareUris>()
        RecapShareViewModel(
            period = params.get(),
            identity = get(),
            cloudSync = get(),
            puzzleDay = get(),
            renderer = get(),
            uriOf = uris::of,
        )
    }
    viewModel { params ->
        StatsViewModel(
            initialGame = params.getOrNull<String>()?.let(::GameId),
            observeStatsPage = get(),
            registry = get(),
            cloudSync = get(),
            savedState = get(),
            releaseFlags = get(),
        )
    }
}
