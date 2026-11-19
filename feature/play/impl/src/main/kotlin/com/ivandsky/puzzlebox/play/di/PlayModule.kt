package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.common.di.flagSet
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.TopBarFlag
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.ranks.LoadFriendsPastPage
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.CustomGameViewModel
import com.ivandsky.puzzlebox.play.GamePageRankViewModel
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.HowToPlayRules
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.MainTabsLargeBar
import com.ivandsky.puzzlebox.play.MatchLegResultViewModel
import com.ivandsky.puzzlebox.play.MatchLegViewModel
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.PlayStatsViewModel
import com.ivandsky.puzzlebox.play.ReplayCompareViewModel
import com.ivandsky.puzzlebox.play.ReplayViewModel
import com.ivandsky.puzzlebox.play.ResultViewModel
import com.ivandsky.puzzlebox.play.SlotOpening
import com.ivandsky.puzzlebox.play.SlotWriter
import com.ivandsky.puzzlebox.play.WriteLane
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.play.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveLegTarget
import com.ivandsky.puzzlebox.play.domain.ObservePlayStats
import com.ivandsky.puzzlebox.play.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.play.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import com.ivandsky.puzzlebox.play.random.RandomPickMemory
import com.ivandsky.puzzlebox.play.random.RandomPuzzleViewModel
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Play feature wiring. The [com.ivandsky.puzzlebox.game.GameRegistry] and the
 * [com.ivandsky.puzzlebox.gameui.GamePlayRegistry] come from the app's aggregation
 * (ADR-0001: one registration per game); the repositories (the tutorial-seen preferences
 * included) and the calendar from :core:data's dataModule, and the shared use cases (today's
 * set, history, stats, the rollover) from :core:domain's domainModule. The result's insights
 * ([ObserveResultInsights]) and next thing ([ObserveResultNextThing]), both Play's own, are
 * defined here, as :core:domain reads no flag (ADR-0022). The tablet stats panel's identity and
 * leaderboard come from the backend
 * module. This module names no game; friend puzzles' wiring is [friendModule]. From friends'
 * [ObserveFriendPuzzles] and [DeleteReceivedPuzzle] are here, as are Play's own use cases: the
 * archive month and progress, the tablet stats panel's [ObservePlayStats], a duel leg's
 * [FinishLeg] and [ObserveLegTarget], Report's [ReportPuzzle], the result's LinkedIn post
 * ([ObserveLinkedInPost], [SubmitLinkedInResult]), a past day's [LoadPastDay], the community
 * list ([ListCommunityPuzzles]), and the random pick ([LoadRandomDay], [LoadRandomShared],
 * [LoadRandomPuzzle]), which shuffles with [Random.Default]. The feature's release flags
 * ([PlayFlag]) are declared here.
 *
 * [PlaySessionStore] is a single so live plays outlive their entries' compositions. Its
 * slot opening and every slot's writer share ONE [WriteLane], so a released slot's final
 * save is on disk before the same slot's reopen reads it; the writers' scope is process
 * lifetime, so a release never cancels a queued save.
 */
val playModule = module {
    includes(friendModule)
    flagSet("play", PlayFlag.entries)
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
        )
    }
    factory { ObserveFriendPuzzles(received = get(), saved = get(), completions = get(), registry = get(), hidden = get()) }
    factory { DeleteReceivedPuzzle(received = get(), saved = get()) }
    factory { ObserveArchiveMonth(daily = get(), saved = get(), completions = get(), calendar = get()) }
    factory { ObserveArchiveProgress(daily = get(), calendar = get(), observeGameHistory = get()) }
    factory {
        ObservePlayStats(
            todaySet = get(),
            history = get(),
            leaderboard = get(),
        )
    }
    factory { FinishLeg(outbox = get(), self = get(), identity = get(), clock = { Instant.now() }) }
    factory { ObserveLegTarget(self = get(), preferences = get()) }
    factory { ReportPuzzle(shared = get(), hidden = get()) }
    factory { ObserveLinkedInPost(posts = get()) }
    factory { SubmitLinkedInResult(posts = get()) }
    factory { LoadPastDay(daily = get(), registry = get()) }
    factory {
        LoadRandomDay(
            daily = get(),
            completions = get(),
            loadPastDay = get(),
            registry = get(),
            random = Random.Default,
        )
    }
    factory { ListCommunityPuzzles(shared = get(), hidden = get(), identity = get(), clock = System::currentTimeMillis) }
    factory { LoadRandomShared(list = get(), random = Random.Default) }
    factory { LoadRandomPuzzle(day = get(), shared = get()) }
    // The flag arrives as the parameter, so a graph without LinkedIn bindings builds the library with it off.
    viewModel { params ->
        LibraryViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
            identity = get(),
            releaseFlags = get(),
        )
    }
    viewModel { (key: GameKey) -> GameViewModel(key, get(), get(), get(), get()) }
    viewModel { (key: CustomGameKey) -> CustomGameViewModel(key, get(), get(), get()) }
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
    // MainTabsLargeBar (backlog #1597): Games and Community open on Today's large top bar while it is on.
    single { MainTabsLargeBar(enabled = get<ReleaseFlags>().isEnabled(TopBarFlag.MainTabsLargeBar)) }
    // The memory forgets the last pick with the process.
    single { RandomPickMemory() }
    // The preset game is the one parameter: the page whose Random opened the sheet.
    viewModel { params -> RandomPuzzleViewModel(params.get<GameId>(), get(), get(), get()) }
    // How to play opens the rules sheet over the live play for a game with rules copy.
    single { HowToPlayRules() }
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
            friendsPast = get<LoadFriendsPastPage.Binding>().loader,
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
    viewModel { (key: ReplayKey) -> ReplayViewModel(key, get(), get(), get(), get(), get()) }
    viewModel { (key: ReplayCompareKey) -> ReplayCompareViewModel(key, get(), get(), get(), get(), get()) }
    viewModel { (key: MatchLegResultKey) ->
        MatchLegResultViewModel(
            key = key,
            store = get(),
            observeMatch = get(),
            games = get(),
            ui = get(),
            loadReplay = get(),
        )
    }
    viewModel { (key: MatchLegKey) ->
        MatchLegViewModel(key = key, store = get(), matches = get(), outbox = get(), identity = get(), self = get(), legTarget = get())
    }
}
