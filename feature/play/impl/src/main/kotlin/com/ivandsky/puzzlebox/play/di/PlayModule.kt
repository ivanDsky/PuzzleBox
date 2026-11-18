package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.domain.DeleteReceivedPuzzle
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.CustomGameViewModel
import com.ivandsky.puzzlebox.play.GamePageRankViewModel
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.MatchLegResultViewModel
import com.ivandsky.puzzlebox.play.MatchLegViewModel
import com.ivandsky.puzzlebox.play.HintPolish
import com.ivandsky.puzzlebox.play.PlayFeel
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.PlayStatsViewModel
import com.ivandsky.puzzlebox.play.ReplayCompareViewModel
import com.ivandsky.puzzlebox.play.ReplayViewModel
import com.ivandsky.puzzlebox.play.ResultViewModel
import com.ivandsky.puzzlebox.play.SlotOpening
import com.ivandsky.puzzlebox.play.SlotWriter
import com.ivandsky.puzzlebox.play.WriteLane
import com.ivandsky.puzzlebox.play.ZipDotPulse
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.random.RandomPickMemory
import com.ivandsky.puzzlebox.play.random.RandomPuzzleEntry
import com.ivandsky.puzzlebox.play.random.RandomPuzzleViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
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
        )
    }
    factory { ObserveFriendPuzzles(received = get(), saved = get(), completions = get(), registry = get(), hidden = get()) }
    factory { DeleteReceivedPuzzle(received = get(), saved = get()) }
    // The flag arrives as the parameter, so a graph without LinkedIn bindings builds the library with it off.
    viewModel { params ->
        LibraryViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            linkedIn = if (params.getOrNull<Boolean>() == true) get() else null,
            identity = get(),
        )
    }
    viewModel { (key: GameKey) -> GameViewModel(key, get(), get(), get()) }
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
    // PlayFeel (spec A19) is read once per graph: every play board and the lessons host hand it down.
    single { PlayFeel(enabled = getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.PlayFeel) == true) }
    single { HintPolish(enabled = getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.HintPolish) == true) }
    // ZipDotPulse (backlog #1151) too: the Zip board's next number pulses its own dot while it is on.
    single { ZipDotPulse(enabled = getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.ZipDotPulse) == true) }
    // RandomPuzzle (epic #1153): screens read the holder, never the flag; the memory forgets the last pick with the process.
    single { RandomPuzzleEntry(enabled = getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.RandomPuzzle) == true) }
    single { RandomPickMemory() }
    // The preset game is the one optional parameter: no parameter is Any game.
    viewModel { params -> RandomPuzzleViewModel(params.getOrNull<GameId>(), get(), get(), get()) }
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
            releaseFlags = getOrNull(),
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
